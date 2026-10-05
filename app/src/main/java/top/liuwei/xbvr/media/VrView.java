package top.liuwei.xbvr.media;
import top.liuwei.xbvr.domain.Projection;

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
    public VrView(Context c,VrRenderer.SurfaceReady ready) {
        super(c);renderer=new VrRenderer(this,ready);renderer.setSettings(settings);
        setEGLContextClientVersion(2);setEGLConfigChooser(8,8,8,8,0,0);setPreserveEGLContextOnPause(true);setRenderer(renderer);setRenderMode(RENDERMODE_WHEN_DIRTY);
        pinch=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){settings.viewFov=Math.max(30,Math.min(110,settings.viewFov/d.getScaleFactor()));requestRender();return true;}});
        gesture=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){@Override public boolean onDown(MotionEvent e){return true;}@Override public boolean onSingleTapConfirmed(MotionEvent e){return performClick();}@Override public boolean onDoubleTap(MotionEvent e){if(e.getX()<getWidth()/2f)doubleLeft.run();else doubleRight.run();return true;}});
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
    @Override public boolean onTouchEvent(MotionEvent e){pinch.onTouchEvent(e);gesture.onTouchEvent(e);
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN||e.getActionMasked()==MotionEvent.ACTION_POINTER_UP){int i=e.getActionMasked()==MotionEvent.ACTION_POINTER_UP&&e.getActionIndex()==0?1:0;if(i<e.getPointerCount()){touchX=e.getX(i);touchY=e.getY(i);}}
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&e.getPointerCount()==1&&!pinch.isInProgress()){
            settings.yaw-=(e.getX()-touchX)*settings.viewFov/Math.max(1,getWidth());settings.pitch+= (e.getY()-touchY)*settings.viewFov/Math.max(1,getHeight());settings.pitch=Math.max(-85,Math.min(85,settings.pitch));touchX=e.getX();touchY=e.getY();requestRender();
        }return true;
    }
    /** Producer is detached first by PlayerActivity. Release works even after GL thread was paused. */
    public void release(){renderer.release();}
}
