package top.liuwei.xbvr.media;
import top.liuwei.xbvr.domain.Projection;

import android.graphics.SurfaceTexture;
import android.opengl.*;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import java.nio.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Owns every GL object and the SurfaceTexture/Surface pair: the EGL callbacks, shader compile/link,
 * the frame loop and the single renderer failure path. VrView keeps the GLSurfaceView, the touch
 * input and the settings bridge, and pushes settings/video geometry into this object. Exactly one
 * object holds the released flag, the surfaceLock and the texture.
 */
public final class VrRenderer implements GLSurfaceView.Renderer {
    public interface SurfaceReady {void ready(Surface surface);}
    public interface Failure {void failed(String phase,RuntimeException failure,String diagnostic);}
    private final GLSurfaceView view;
    private final SurfaceReady callback;
    private volatile Projection settings=new Projection();
    private volatile int videoWidth=1920,videoHeight=1080;
    private volatile float videoPixelAspect=1;
    private SurfaceTexture texture;private Surface surface;private int program,tex,w=1,h=1;
    private final float[] transform=new float[16];private final AtomicBoolean frame=new AtomicBoolean();
    private final Object surfaceLock=new Object();private volatile boolean released;
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean failed;
    private Failure failureListener;private boolean failureDelivered;
    private RuntimeException renderFailure;private String failurePhase,failureDiagnostic;
    private String renderPhase="surface.create",gpu="GPU information unavailable",precision="not queried";
    private final FloatBuffer quad=ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    VrRenderer(GLSurfaceView view,SurfaceReady ready) {this.view=view;callback=ready;quad.put(new float[]{-1,-1,1,-1,-1,1,1,1}).position(0);}
    /** VrView pushes the live projection reference and the decoded video geometry, keeping one owner. */
    void setSettings(Projection value){settings=value;}
    void setVideoSize(int width,int height,float pixelAspect){videoWidth=width;videoHeight=height;videoPixelAspect=pixelAspect;}
    /** Register before attaching the view. A failure is delivered once, on the main thread. */
    void onFailure(Failure listener){synchronized(surfaceLock){failureListener=listener;if(failed&&!failureDelivered)main.post(this::deliverFailure);}}
    private void deliverFailure(){
        Failure listener;RuntimeException error;String phase,diagnostic;
        synchronized(surfaceLock){if(released||!failed||failureDelivered||failureListener==null)return;failureDelivered=true;listener=failureListener;error=renderFailure;phase=failurePhase;diagnostic=failureDiagnostic;}
        listener.failed(phase,error,diagnostic);
    }
    private void fail(RuntimeException error){
        if(failed)return;
        failed=true;frame.set(false);renderFailure=error;failurePhase=renderPhase;
        failureDiagnostic="Renderer stage: "+failurePhase+"\n"+gpu+"\nFragment float precision: "+precision+"\n"+error.getClass().getName()+": "+error.getMessage();
        main.post(this::deliverFailure);
    }
    private static String glString(int name){try{String value=GLES20.glGetString(name);return value==null?"unavailable":value;}catch(RuntimeException error){return "unavailable ("+error.getClass().getSimpleName()+")";}}
    @Override public void onSurfaceCreated(GL10 gl,EGLConfig config){
        synchronized(surfaceLock){if(released||failed)return;
        try{
        renderPhase="surface.capabilities";
        gpu="GL_VENDOR: "+glString(GLES20.GL_VENDOR)+"\nGL_RENDERER: "+glString(GLES20.GL_RENDERER)+"\nGL_VERSION: "+glString(GLES20.GL_VERSION)+"\nGLSL: "+glString(GLES20.GL_SHADING_LANGUAGE_VERSION);
        int[] range=new int[2],bits=new int[1];GLES20.glGetShaderPrecisionFormat(GLES20.GL_FRAGMENT_SHADER,GLES20.GL_HIGH_FLOAT,range,0,bits,0);
        boolean highp=RendererShader.supportsHighp(range[0],range[1],bits[0]);
        precision=(highp?"highp":"mediump")+" (highp range="+range[0]+","+range[1]+"; bits="+bits[0]+")";
        // onSurfaceCreated means a new EGL context: old GL names must not be deleted in it.
        program=0;tex=0;
        program=link(RendererShader.vertex(highp),RendererShader.fragment(highp));
        renderPhase="surface.texture";int[] ids=new int[1];GLES20.glGenTextures(1,ids,0);tex=ids[0];if(tex==0)throw new IllegalStateException("glGenTextures returned 0");
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,tex);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        if(surface!=null)surface.release();if(texture!=null){texture.setOnFrameAvailableListener(null);texture.release();}
        renderPhase="surface.create";
        frame.set(false);texture=new SurfaceTexture(tex);texture.setOnFrameAvailableListener(t->{if(!released&&!failed){frame.set(true);view.requestRender();}});surface=new Surface(texture);Matrix.setIdentityM(transform,0);
        Surface created=surface;
        view.post(()->{synchronized(surfaceLock){if(!released&&!failed&&surface==created&&created.isValid())callback.ready(created);}});
        }catch(RuntimeException error){fail(error);}
        }
    }
    @Override public void onSurfaceChanged(GL10 gl,int width,int height){synchronized(surfaceLock){if(released||failed)return;try{renderPhase="surface.resize";w=Math.max(1,width);h=Math.max(1,height);GLES20.glViewport(0,0,w,h);}catch(RuntimeException error){fail(error);}}}
    private void f(String name,float value){GLES20.glUniform1f(GLES20.glGetUniformLocation(program,name),value);}
    @Override public void onDrawFrame(GL10 gl){
        synchronized(surfaceLock){if(released||failed||texture==null)return;
        try{
        renderPhase="frame.clear";
        GLES20.glClearColor(0,0,0,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if(frame.getAndSet(false)){renderPhase="frame.update";texture.updateTexImage();texture.getTransformMatrix(transform);}
        renderPhase="frame.draw";
        GLES20.glUseProgram(program);int attr=GLES20.glGetAttribLocation(program,"position");quad.position(0);GLES20.glEnableVertexAttribArray(attr);GLES20.glVertexAttribPointer(attr,2,GLES20.GL_FLOAT,false,0,quad);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,tex);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"video"),0);GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"st"),1,false,transform,0);
        Projection p=settings;f("kind",p.kind);f("stereoLayout",p.layout);f("eye",p.eye);f("capture",(float)Math.toRadians(p.capture));f("yaw",(float)Math.toRadians(p.yaw));f("pitch",(float)Math.toRadians(p.pitch));f("tangent",(float)Math.tan(Math.toRadians(p.viewFov/2)));f("aspect",(float)w/h);f("rotation",(float)Math.toRadians(p.rotation));f("mirror",p.mirror?-1:1);
        float ew=RenderMath.eyeWidth(videoWidth,p.layout),eh=RenderMath.eyeHeight(videoHeight,p.layout),r=Math.min(ew,eh)*.5f*p.radius;
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"circle"),r/ew,r/eh);GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"center"),p.centerX,p.centerY);
        f("sourceAspect",RenderMath.eyeAspect(videoWidth,videoHeight,p.layout,p.halfPacked,videoPixelAspect));
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        }catch(RuntimeException error){fail(error);}
        }
    }
    /** Producer is detached first by PlayerActivity. Release works even after GL thread was paused. */
    void release(){synchronized(surfaceLock){released=true;frame.set(false);if(surface!=null){surface.release();surface=null;}if(texture!=null){texture.setOnFrameAvailableListener(null);texture.release();texture=null;}}}
    private int shader(int type,String source){
        renderPhase=type==GLES20.GL_VERTEX_SHADER?"shader.vertex":"shader.fragment";
        int id=0;
        try{
            id=GLES20.glCreateShader(type);if(id==0)throw new IllegalStateException("glCreateShader returned 0");
            GLES20.glShaderSource(id,source);GLES20.glCompileShader(id);
            int[] status=new int[1];GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,status,0);
            if(status[0]==0)throw new IllegalStateException("Shader compile infoLog:\n"+GLES20.glGetShaderInfoLog(id));
            return id;
        }catch(RuntimeException error){deleteShader(id,error);throw error;}
    }
    private int link(String vertex,String fragment){
        int a=0,b=0,p=0;
        try{
            a=shader(GLES20.GL_VERTEX_SHADER,vertex);b=shader(GLES20.GL_FRAGMENT_SHADER,fragment);renderPhase="program.link";
            p=GLES20.glCreateProgram();if(p==0)throw new IllegalStateException("glCreateProgram returned 0");
            GLES20.glAttachShader(p,a);GLES20.glAttachShader(p,b);GLES20.glLinkProgram(p);
            int[] status=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,status,0);
            if(status[0]==0)throw new IllegalStateException("Program link infoLog:\n"+GLES20.glGetProgramInfoLog(p));
            renderPhase="program.cleanup";
            RuntimeException cleanup=deleteShader(a,null);a=0;cleanup=deleteShader(b,cleanup);b=0;
            if(cleanup!=null)throw cleanup;
            return p;
        }catch(RuntimeException error){deleteShader(a,error);deleteShader(b,error);deleteProgram(p,error);throw error;}
    }
    // Preserve the original compiler/driver exception and still attempt every deletion.
    private static RuntimeException deleteShader(int id,RuntimeException failure){
        if(id!=0)try{GLES20.glDeleteShader(id);}catch(RuntimeException cleanup){if(failure==null)return cleanup;if(cleanup!=failure)failure.addSuppressed(cleanup);}
        return failure;
    }
    private static void deleteProgram(int id,RuntimeException failure){
        if(id!=0)try{GLES20.glDeleteProgram(id);}catch(RuntimeException cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
    }
}
