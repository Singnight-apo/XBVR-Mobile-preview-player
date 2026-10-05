package top.liuwei.xbvr;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Test APK only: real GL compilation and the production PlayerActivity failure listener. */
public final class RendererFailureInstrumentation extends Instrumentation {
    private static final String FIXTURE_URL="http://10.0.2.2:18766/deovr/1";
    private static final String SECRET_URL="https://synthetic.invalid/never-requested?token=QA_SECRET_URL_41";
    private static final String SECRET_TOKEN="QA_SECRET_TOKEN_83";
    private Bundle arguments;
    private String stage="launch";
    private Activity playerActivity;
    private Object engineBeforeFailure;

    @Override public void onCreate(Bundle args){super.onCreate(args);arguments=args==null?new Bundle():new Bundle(args);start();}

    @Override public void onStart(){
        Bundle results=new Bundle();
        try{
            String url=arguments.getString("url",FIXTURE_URL);
            requireFixture(url);
            String profile=findFixtureProfile(url,arguments.getString("profile",""));
            Intent intent=new Intent().setClassName(getTargetContext(),"top.liuwei.xbvr.PlayerActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("profile",profile).putExtra("url",url)
                .putExtra("title","合成测试 · 渲染故障注入");
            playerActivity=startActivitySync(intent);
            awaitPlayerAndSurface();
            GLSurfaceView view=mainValue(()->(GLSurfaceView)read(playerActivity,"vr"));
            AtomicReference<String> renderer=new AtomicReference<>();
            AtomicReference<RuntimeException> injected=new AtomicReference<>();

            stage="mediump.compile";
            onGl(view,()->{
                String vertex=(String)call(method("RendererShader","vertex",boolean.class),null,false);
                String fragment=(String)call(method("RendererShader","fragment",boolean.class),null,false);
                Method link=method("VrView","link",String.class,String.class);
                int program=(Integer)call(link,view,vertex,fragment);
                try{check(program!=0&&GLES20.glIsProgram(program),"Valid mediump shaders did not link");}
                finally{if(program!=0)GLES20.glDeleteProgram(program);}
                check(!GLES20.glIsProgram(program),"Temporary mediump program was not deleted");
                renderer.set(GLES20.glGetString(GLES20.GL_RENDERER));
                check(renderer.get()!=null&&!renderer.get().isEmpty(),"Current GL renderer is unavailable");
            });
            results.putBoolean("mediump",true);
            progress(1,"Valid mediump vertex + external-texture fragment linked on the real GL thread.");

            stage="shader.fragment";
            onGl(view,()->{
                synchronized(read(view,"surfaceLock")){
                    String vertex=(String)call(method("RendererShader","vertex",boolean.class),null,false);
                    String badFragment="precision mediump float;\nvarying mediump vec2 pos;\nvoid main(){gl_FragColor=vec4(1.0) THIS_IS_NOT_VALID;}\n";
                    RuntimeException compilerFailure=null;
                    try{
                        int unexpected=(Integer)call(method("VrView","link",String.class,String.class),view,vertex,badFragment);
                        if(unexpected!=0)GLES20.glDeleteProgram(unexpected);
                    }catch(IllegalStateException failure){compilerFailure=failure;}
                    check(compilerFailure!=null&&compilerFailure.getMessage()!=null&&compilerFailure.getMessage().contains("Shader compile infoLog:"),"Broken fragment did not produce a compiler infoLog");
                    check("shader.fragment".equals(read(view,"renderPhase")),"Compiler failure stage was not shader.fragment");
                    // Synthetic secrets exercise both renderer-message filtering and cause-message omission.
                    RuntimeException failure=new IllegalStateException(compilerFailure.getMessage()+"\n"+SECRET_URL+"\ntoken="+SECRET_TOKEN+"\nAuthorization: Bearer "+SECRET_TOKEN,compilerFailure);
                    injected.set(failure);
                    call(method("VrView","fail",RuntimeException.class),view,failure);
                }
            });
            awaitRendererFailure();
            mainValue(()->{
                assertFailureUi();
                check(read(view,"renderFailure")==injected.get(),"Original injected exception identity was lost");
                check(Boolean.TRUE.equals(read(view,"failureDelivered")),"Production failure listener was not delivered");
                Set<?> dialogs=(Set<?>)read(read(playerActivity,"view"),"dialogs");
                boolean showing=false;for(Object entry:dialogs)if(entry instanceof Dialog&&((Dialog)entry).isShowing())showing=true;
                check(showing,"Playback failure did not leave a visible dialog");
                return null;
            });
            results.putBoolean("faultCallback",true);
            progress(2,"Real fragment compiler error reached the production main-thread listener; player released and activity remains alive.");

            stage="diagnostics.privacy";
            String report=(String)call(method("PlaybackDiagnostics","report",Context.class),null,getTargetContext());
            check(report.contains("shader.fragment"),"Diagnostic omitted the failure phase");
            check(report.contains("GL_VENDOR:")&&report.contains("GL_RENDERER: "+renderer.get())&&report.contains("GL_VERSION:")&&report.contains("GLSL:"),"Diagnostic omitted actual GPU strings");
            check(report.contains("java.lang.IllegalStateException"),"Diagnostic omitted the exception class");
            check(report.contains("Shader compile infoLog:"),"Diagnostic omitted the compiler infoLog");
            check(!report.contains(SECRET_URL)&&!report.contains("synthetic.invalid")&&!report.contains("QA_SECRET_URL_41")&&!report.contains(SECRET_TOKEN),"Diagnostic leaked synthetic URL or credentials");
            // Production safe() emits the English markers; the secrets themselves must be gone.
            check(report.contains("[URL omitted]")&&report.contains("[credentials omitted]"),"Synthetic URL/credentials did not exercise diagnostic redaction");
            // A second injected failure must not replace the first; stopped render callbacks must return safely.
            onGl(view,()->{
                synchronized(read(view,"surfaceLock")){
                    call(method("VrView","fail",RuntimeException.class),view,new IllegalArgumentException("second synthetic failure"));
                    ((GLSurfaceView.Renderer)view).onDrawFrame(null);
                    check(read(view,"renderFailure")==injected.get(),"Second failure replaced the first exception");
                }
            });
            SystemClock.sleep(250);
            mainValue(()->{assertFailureUi();return null;});
            results.putBoolean("privacy",true);results.putBoolean("passed",true);results.putInt("checks",3);
            results.putString("phase","shader.fragment");results.putString("gpu",renderer.get());
            results.putString("exceptionClass",injected.get().getClass().getName());
            results.putString("stream","\nOK (3 integration checks): mediump GL compile; production failure callback/release; compiler/GPU diagnostic and secret filtering.\n");
            finish(Activity.RESULT_OK,results);
        }catch(Exception|AssertionError failure){
            results.putBoolean("passed",false);results.putString("phase",stage);
            results.putString("exceptionClass",failure.getClass().getName());
            // Do not dump arbitrary exception messages: launch/profile errors can include media addresses.
            String assertion=failure instanceof AssertionError?String.valueOf(failure.getMessage()):"Inspect test stage and local playback diagnostic.";
            results.putString("stream","\nFAIL at "+stage+": "+failure.getClass().getName()+"; "+assertion+"\n");
            finish(Activity.RESULT_CANCELED,results);
        }
    }

    private void awaitPlayerAndSurface()throws Exception{
        long deadline=SystemClock.uptimeMillis()+30000;
        while(SystemClock.uptimeMillis()<deadline){
            if(mainValue(()->{
                check(!playerActivity.isFinishing()&&!playerActivity.isDestroyed(),"Player activity exited during initialization");
                check(!rendererFailed(),"Renderer failed before deliberate injection");
                if(!hasEngine()||!surfaceBound())return false;
                engineBeforeFailure=enginePlayer();
                return engineBeforeFailure!=null;
            }))return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Synthetic fixture player/surface was not ready within 30 seconds");
    }
    private void awaitRendererFailure()throws Exception{
        long deadline=SystemClock.uptimeMillis()+10000;
        while(SystemClock.uptimeMillis()<deadline){if(mainValue(()->rendererFailed()))return;SystemClock.sleep(50);}
        throw new AssertionError("Production renderer failure callback was not delivered within 10 seconds");
    }
    private void assertFailureUi()throws Exception{
        check(rendererFailed(),"rendererFailed was not set");
        check(!hasEngine(),"Playback session engine was not cleared");
        check(engineReleased(),"ExoPlayer was not released");
        check(!playerActivity.isFinishing()&&!playerActivity.isDestroyed(),"Failure listener finished the activity");
    }
    /** The session's live engine, located through the lifecycle policy that owns it. */
    private Object session()throws Exception{return read(playerActivity,"session");}
    private boolean hasEngine()throws Exception{
        Object value=session();
        return value!=null&&Boolean.TRUE.equals(call(value.getClass().getMethod("hasEngine"),value));
    }
    private Object enginePlayer()throws Exception{
        Object value=session();if(value==null)return null;
        Object lifecycle=read(value,"lifecycle");
        Object backend=call(lifecycle.getClass().getMethod("backend"),lifecycle);
        return backend==null?null:read(backend,"player");
    }
    private boolean engineReleased()throws Exception{
        if(engineBeforeFailure==null)return false;
        return Boolean.TRUE.equals(call(engineBeforeFailure.getClass().getMethod("isReleased"),engineBeforeFailure));
    }
    private boolean surfaceBound()throws Exception{
        Object value=session();
        return value!=null&&read(value,"surface")!=null;
    }
    private boolean rendererFailed()throws Exception{
        Object controller=read(playerActivity,"controller");
        Object state=call(controller.getClass().getMethod("state"),controller);
        return Boolean.TRUE.equals(read(state,"rendererFailed"));
    }
    private static void requireFixture(String value){
        Uri url=Uri.parse(value);
        check("http".equals(url.getScheme())&&"10.0.2.2:18766".equals(url.getEncodedAuthority())&&url.getPath()!=null&&url.getPath().matches("/deovr/[0-9]+")&&url.getQuery()==null&&url.getFragment()==null,"Only the local synthetic fixture detail URL is accepted");
    }
    private String findFixtureProfile(String url,String requested)throws Exception{
        Class<?> storeClass=appClass("Store");Object store=storeClass.getConstructor(Context.class).newInstance(getTargetContext());
        JSONArray profiles=(JSONArray)call(storeClass.getMethod("profiles"),store);
        Method sameOrigin=method("data.XbvrProtocol","sameOrigin",String.class,String.class);
        for(int i=0;i<profiles.length();i++){
            JSONObject profile=profiles.getJSONObject(i);String id=profile.optString("id");
            if(!id.isEmpty()&&(requested.isEmpty()||requested.equals(id))&&Boolean.TRUE.equals(call(sameOrigin,null,profile.optString("base"),url)))return id;
        }
        throw new AssertionError("No saved synthetic fixture profile matches the test URL; configure it in MainActivity first");
    }
    private void progress(int current,String message){Bundle status=new Bundle();status.putInt("current",current);status.putInt("numtests",3);status.putString("stream",message+"\n");sendStatus(0,status);}
    private <T>T mainValue(Callable<T> action)throws Exception{
        FutureTask<T> task=new FutureTask<>(action);runOnMainSync(task);
        try{return task.get();}catch(java.util.concurrent.ExecutionException error){Throwable cause=error.getCause();if(cause instanceof Exception)throw (Exception)cause;if(cause instanceof Error)throw (Error)cause;throw new AssertionError("Unexpected main-thread test failure");}
    }
    private interface GlAction {void run()throws Exception;}
    private void onGl(GLSurfaceView view,GlAction action)throws Exception{
        CountDownLatch complete=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
        view.queueEvent(()->{try{action.run();}catch(Exception|AssertionError error){failure.set(error);}finally{complete.countDown();}});
        check(complete.await(15,TimeUnit.SECONDS),"GL test action timed out");
        Throwable error=failure.get();if(error instanceof Exception)throw (Exception)error;if(error instanceof AssertionError)throw (AssertionError)error;
    }
    private Class<?> appClass(String name)throws ClassNotFoundException{return Class.forName("top.liuwei.xbvr."+name,true,getTargetContext().getClassLoader());}
    private Method method(String name,String method,Class<?>...params)throws Exception{Method result=appClass(name).getDeclaredMethod(method,params);result.setAccessible(true);return result;}
    private static Object read(Object owner,String name)throws Exception{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
    private static Object call(Method method,Object owner,Object...args)throws Exception{
        try{return method.invoke(owner,args);}catch(InvocationTargetException error){Throwable cause=error.getCause();if(cause instanceof Exception)throw (Exception)cause;if(cause instanceof Error)throw (Error)cause;throw new AssertionError("Unexpected reflected failure");}
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
