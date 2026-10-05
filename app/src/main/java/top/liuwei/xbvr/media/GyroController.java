package top.liuwei.xbvr.media;
import top.liuwei.xbvr.domain.Projection;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Surface;

/**
 * Owns the rotation-vector sensor and the yaw/pitch mapping. The page only sends the
 * enable/activate/stop/reset actions and reads {@link #enabled()}; the SensorManager is registered
 * and unregistered only from here, driven by the Activity lifecycle. The pure {@link Mapping} part
 * holds the base-angle state machine and the display-rotation remap, so it stays JVM testable with
 * synthetic angle inputs.
 */
public final class GyroController implements SensorEventListener {
    /** Result of the enable action. */
    public static final int OK=0,NO_SENSOR=1,UNAVAILABLE=2;

    /** Page hooks; deliberately Android-free apart from the display rotation code. */
    public interface Sink {
        boolean active();
        Projection projection();
        void render();
        int rotation();
    }

    private final SensorManager sensors;
    private final Sensor rotationSensor;
    private final Sink sink;
    private final Mapping mapping=new Mapping();
    private boolean enabled;

    public GyroController(SensorManager sensors,Sink sink){
        this.sensors=sensors;
        this.sink=sink;
        Sensor sensor=null;
        if(sensors!=null){
            sensor=sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            if(sensor==null)sensor=sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
        rotationSensor=sensor;
    }

    public boolean enabled(){return enabled;}

    public boolean hasSensor(){return rotationSensor!=null;}

    /**
     * Enable/disable action. Always resets the base first (unless no sensor exists), matching the
     * old toggle. Returns {@link #NO_SENSOR} when the device has no rotation vector sensor and
     * {@link #UNAVAILABLE} when the page is not active or the registration was refused.
     */
    public int toggle(){
        if(rotationSensor==null)return NO_SENSOR;
        mapping.reset();
        if(enabled){
            sensors.unregisterListener(this);
            enabled=false;
            return OK;
        }
        enabled=sink.active()
                &&sensors.registerListener(this,rotationSensor,SensorManager.SENSOR_DELAY_GAME);
        return enabled?OK:UNAVAILABLE;
    }

    /**
     * Activate action after the page becomes visible again: re-registers the listener the old
     * onStart re-armed. Returns true only when an armed registration failed, so the page refreshes
     * its status line exactly like before.
     */
    public boolean activate(){
        if(!enabled||rotationSensor==null)return false;
        if(sensors.registerListener(this,rotationSensor,SensorManager.SENSOR_DELAY_GAME))return false;
        enabled=false;
        return true;
    }

    /** Stop action: the listener is dropped as soon as the Activity stops, not at onDestroy. */
    public void stop(){
        if(sensors!=null)sensors.unregisterListener(this);
    }

    /** Reset action: the next sample only rebuilds the base angle. */
    public void reset(){mapping.reset();}

    @Override
    public void onSensorChanged(SensorEvent event){
        if(!enabled)return;
        float[] mat=new float[9],remap=new float[9],angles=new float[3];
        SensorManager.getRotationMatrixFromVector(mat,event.values);
        int[] axes=Mapping.remap(sink.rotation());
        SensorManager.remapCoordinateSystem(mat,axes[0],axes[1],remap);
        SensorManager.getOrientation(remap,angles);
        float yaw=(float)Math.toDegrees(angles[0]),pitch=(float)Math.toDegrees(angles[1]);
        if(mapping.sample(sink.projection(),sink.active(),yaw,pitch))sink.render();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor,int accuracy){}

    /**
     * Pure yaw/pitch state machine and display-rotation remap. It has no Android types in reach,
     * so tests drive it with captured or synthetic angles.
     */
    public static final class Mapping {
        private boolean base;
        private float baseYaw,basePitch;

        public void reset(){base=false;}

        public boolean hasBase(){return base;}

        /**
         * Applies one sample. Inactive samples are ignored and do not establish a base. The first
         * active sample after a reset only records the base; later samples add the wrapped yaw
         * delta and the bounded pitch delta and report that a frame should be drawn.
         */
        public boolean sample(Projection projection,boolean active,float yaw,float pitch){
            if(!active)return false;
            boolean changed=false;
            if(base){
                projection.yaw+=RenderMath.wrappedDelta(yaw,baseYaw);
                projection.pitch=RenderMath.gyroPitch(projection.pitch,pitch,basePitch);
                changed=true;
            }
            baseYaw=yaw;
            basePitch=pitch;
            base=true;
            return changed;
        }

        /**
         * SensorManager remap axes {x,y} for the current display rotation. Mirrors the old
         * onSensorChanged branches: 0: X,Z / 90: Z,-X / 180: -X,-Z / 270: -Z,X.
         */
        public static int[] remap(int rotation){
            int x=SensorManager.AXIS_X,y=SensorManager.AXIS_Z;
            if(rotation==Surface.ROTATION_90){
                x=SensorManager.AXIS_Z;
                y=SensorManager.AXIS_MINUS_X;
            }else if(rotation==Surface.ROTATION_270){
                x=SensorManager.AXIS_MINUS_Z;
                y=SensorManager.AXIS_X;
            }else if(rotation==Surface.ROTATION_180){
                x=SensorManager.AXIS_MINUS_X;
                y=SensorManager.AXIS_MINUS_Z;
            }
            return new int[]{x,y};
        }
    }
}
