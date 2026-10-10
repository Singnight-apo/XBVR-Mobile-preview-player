package top.liuwei.xbvr.media;
import top.liuwei.xbvr.domain.Projection;
import top.liuwei.xbvr.domain.SeekGesture;

import android.content.Context;
import android.opengl.*;
import android.view.*;

/**
 * Android rendering/input adapter: owns the GLSurfaceView, the touch and pinch gestures, and the
 * settings bridge. The GL objects, the SurfaceTexture/Surface pair, the frame loop and the failure
 * path live in {@link VrRenderer}; this view only pushes the live projection and video geometry to it.
 */
public final class VrView extends GLSurfaceView {
    private final VrRenderer renderer;
    private volatile Projection settings=new Projection();
    private float touchX,touchY;private final ScaleGestureDetector pinch;
    private Runnable tap=()->{};
    private Runnable doubleLeft=()->{},doubleRight=()->{};
    private final GestureDetector gesture;
    public interface SeekListener {
        long duration();
        long position();
        void preview(long position,long start,long duration);
        void finished(long position);
        void cancelled();
    }
    private SeekListener seekListener;
    private final SeekGesture seekGesture=new SeekGesture();
    private final int touchSlop;
    public VrView(Context c,VrRenderer.SurfaceReady ready) {
        super(c);renderer=new VrRenderer(this,ready);renderer.setSettings(settings);
        touchSlop=ViewConfiguration.get(c).getScaledTouchSlop();
        setEGLContextClientVersion(2);setEGLConfigChooser(8,8,8,8,0,0);setPreserveEGLContextOnPause(true);setRenderer(renderer);setRenderMode(RENDERMODE_WHEN_DIRTY);
        pinch=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){settings.viewFov=Math.max(30,Math.min(110,settings.viewFov/d.getScaleFactor()));requestRender();return true;}});
        gesture=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){@Override public boolean onDown(MotionEvent e){return true;}@Override public boolean onSingleTapConfirmed(MotionEvent e){return performClick();}@Override public boolean onDoubleTap(MotionEvent e){seekGesture.cancel();if(e.getX()<getWidth()/2f)doubleLeft.run();else doubleRight.run();return true;}});
    }
    public void onTap(Runnable r){tap=r;}
    /** Register before attaching the view. A failure is delivered once, on the main thread. */
    public void onFailure(VrRenderer.Failure listener){renderer.onFailure(listener);}
    /** Keeps a current projection reference and pushes that same object to the renderer. */
    public void setSettings(Projection value){settings=value;renderer.setSettings(value);}
    /** Forwards the decoded video geometry to the renderer for the next frame. */
    public void setVideoSize(int width,int height,float pixelAspect){renderer.setVideoSize(width,height,pixelAspect);}
    @Override public boolean performClick(){super.performClick();tap.run();return true;}
    public void onDoubleTap(Runnable left,Runnable right){doubleLeft=left;doubleRight=right;}
    public void onSeek(SeekListener listener){seekListener=listener;}
    public void cancelSeek(){boolean active=seekGesture.mode()==SeekGesture.Mode.SEEK;seekGesture.cancel();if(active&&seekListener!=null)seekListener.cancelled();}
    private void cancelTap(MotionEvent event){MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);gesture.onTouchEvent(cancel);cancel.recycle();}
    private int navigationBottom(){WindowInsets in=getRootWindowInsets();return in==null?0:in.getMandatorySystemGestureInsets().bottom;}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){cancelSeek();super.onSizeChanged(w,h,oldw,oldh);}
    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            cancelSeek();touchX=e.getX();touchY=e.getY();
            seekGesture.begin(touchX,touchY,getWidth(),getHeight()-navigationBottom(),touchSlop,
                seekListener==null?0:seekListener.duration(),seekListener==null?0:seekListener.position());
        }
        if(action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_CANCEL){cancelSeek();cancelTap(e);}
        pinch.onTouchEvent(e);
        if(action==MotionEvent.ACTION_POINTER_UP&&e.getPointerCount()==2){
            int remaining=e.getActionIndex()==0?1:0;touchX=e.getX(remaining);touchY=e.getY(remaining);seekGesture.continuePan();
        }
        if(action==MotionEvent.ACTION_MOVE&&e.getPointerCount()==1&&!pinch.isInProgress()){
            boolean alreadySeeking=seekGesture.mode()==SeekGesture.Mode.SEEK;
            SeekGesture.Mode mode=seekGesture.move(e.getX(),e.getY());
            if(mode==SeekGesture.Mode.SEEK){
                if(!alreadySeeking)cancelTap(e);
                if(seekListener!=null)seekListener.preview(seekGesture.target(),seekGesture.startPosition(),seekGesture.duration());
            }else if(mode==SeekGesture.Mode.PAN){
                settings.yaw-=(e.getX()-touchX)*settings.viewFov/Math.max(1,getWidth());settings.pitch+=(e.getY()-touchY)*settings.viewFov/Math.max(1,getHeight());
                settings.pitch=Math.max(-85,Math.min(85,settings.pitch));touchX=e.getX();touchY=e.getY();requestRender();
            }
        }
        if(seekGesture.mode()!=SeekGesture.Mode.SEEK)gesture.onTouchEvent(e);
        if(action==MotionEvent.ACTION_UP){long target=seekGesture.finish();if(target>=0&&seekListener!=null)seekListener.finished(target);}
        return true;
    }
    /** Producer is detached first by PlayerActivity. Release works even after GL thread was paused. */
    public void release(){renderer.release();}
}
