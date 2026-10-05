package top.liuwei.xbvr.media;
import top.liuwei.xbvr.domain.Projection;

/** Geometry and sensor deltas used by the Android renderer; no Android dependency. */
public final class RenderMath {
    private RenderMath() {}
    public static float eyeWidth(int width,int layout){return Math.max(1,width)/(layout==Projection.SBS?2f:1f);}
    public static float eyeHeight(int height,int layout){return Math.max(1,height)/(layout==Projection.TB?2f:1f);}
    public static float eyeAspect(int width,int height,int layout,boolean halfPacked,float pixelAspect){
        float ratio=eyeWidth(width,layout)/eyeHeight(height,layout);
        if(Float.isFinite(pixelAspect)&&pixelAspect>0)ratio*=pixelAspect;
        if(halfPacked){if(layout==Projection.SBS)ratio*=2;else if(layout==Projection.TB)ratio/=2;}
        return ratio;
    }
    public static float wrappedDelta(float current,float previous){
        float delta=(current-previous)%360;
        if(delta>180)delta-=360;
        if(delta< -180)delta+=360;
        return delta;
    }
    /** Android camera-remapped pitch is negative when the physical viewing direction tilts upward. */
    public static float gyroPitch(float currentViewPitch,float sensorPitch,float previousSensorPitch){
        return Math.max(-85,Math.min(85,currentViewPitch-wrappedDelta(sensorPitch,previousSensorPitch)));
    }
}
