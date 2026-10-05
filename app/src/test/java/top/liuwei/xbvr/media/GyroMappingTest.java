package top.liuwei.xbvr.media;

import android.hardware.SensorManager;
import android.view.Surface;

import org.junit.Test;
import top.liuwei.xbvr.domain.Projection;
import static org.junit.Assert.*;

/**
 * Synthetic-angle coverage for the gyro state machine and the display-rotation remap. No Android
 * API is invoked: only the inlined axis/rotation constants and the pure Mapping are used.
 */
public class GyroMappingTest {
    @Test public void firstFrameOnlyEstablishesTheBase(){
        Projection projection=new Projection();
        projection.yaw=40;projection.pitch=12;
        GyroController.Mapping mapping=new GyroController.Mapping();
        assertFalse(mapping.hasBase());
        assertFalse("The first sample must not move the view",mapping.sample(projection,true,100,20));
        assertEquals(40,projection.yaw,1e-6);
        assertEquals(12,projection.pitch,1e-6);
        assertTrue(mapping.hasBase());
    }

    @Test public void laterFramesApplyIncrementalYawAndPitchDeltas(){
        Projection projection=new Projection();
        GyroController.Mapping mapping=new GyroController.Mapping();
        assertFalse(mapping.sample(projection,true,10,5));
        assertTrue(mapping.sample(projection,true,20,10));
        assertEquals(10,projection.yaw,1e-6);
        assertEquals(-5,projection.pitch,1e-6);
    }

    @Test public void yawWrapsAcrossThePlusMinus180Boundary(){
        Projection projection=new Projection();
        GyroController.Mapping mapping=new GyroController.Mapping();
        mapping.sample(projection,true,179,0);
        mapping.sample(projection,true,-179,0);
        assertEquals("179 -> -179 is a +2 degree turn",2,projection.yaw,1e-6);
        mapping.reset();
        mapping.sample(projection,true,-179,0);
        mapping.sample(projection,true,179,0);
        assertEquals("The reverse crossing cancels the short delta",0,projection.yaw,1e-6);
    }

    @Test public void pitchUsesTheShortestWrappedDeltaAndKeepsItsBounds(){
        Projection projection=new Projection();
        GyroController.Mapping mapping=new GyroController.Mapping();
        mapping.sample(projection,true,0,-179);
        mapping.sample(projection,true,0,179);
        assertEquals(2,projection.pitch,1e-6);
        projection.pitch=0;
        mapping.reset();
        mapping.sample(projection,true,0,0);
        mapping.sample(projection,true,0,-100);
        assertEquals(85,projection.pitch,1e-6);
        projection.pitch=0;
        mapping.reset();
        mapping.sample(projection,true,0,0);
        mapping.sample(projection,true,0,100);
        assertEquals(-85,projection.pitch,1e-6);
    }

    @Test public void inactiveSamplesAreIgnoredAndEstablishNoBase(){
        Projection projection=new Projection();
        projection.yaw=7;projection.pitch=3;
        GyroController.Mapping mapping=new GyroController.Mapping();
        assertFalse(mapping.sample(projection,false,90,45));
        assertFalse("An inactive sample must not build a base",mapping.hasBase());
        assertEquals(7,projection.yaw,1e-6);
        assertEquals(3,projection.pitch,1e-6);
        assertFalse("The first active sample is still only a base",mapping.sample(projection,true,90,45));
        assertEquals(7,projection.yaw,1e-6);
    }

    @Test public void resetRebuildsTheBaseOnTheNextActiveSample(){
        Projection projection=new Projection();
        GyroController.Mapping mapping=new GyroController.Mapping();
        mapping.sample(projection,true,10,5);
        mapping.sample(projection,true,30,15);
        assertEquals(20,projection.yaw,1e-6);
        mapping.reset();
        assertFalse(mapping.hasBase());
        assertFalse("After a reset the next sample must not jump",mapping.sample(projection,true,200,40));
        assertEquals(20,projection.yaw,1e-6);
        assertTrue(mapping.sample(projection,true,205,45));
        assertEquals(25,projection.yaw,1e-6);
    }

    @Test public void eachDisplayRotationMapsItsOwnCameraAxes(){
        assertArrayEquals(new int[]{SensorManager.AXIS_X,SensorManager.AXIS_Z},
                GyroController.Mapping.remap(Surface.ROTATION_0));
        assertArrayEquals(new int[]{SensorManager.AXIS_Z,SensorManager.AXIS_MINUS_X},
                GyroController.Mapping.remap(Surface.ROTATION_90));
        assertArrayEquals(new int[]{SensorManager.AXIS_MINUS_X,SensorManager.AXIS_MINUS_Z},
                GyroController.Mapping.remap(Surface.ROTATION_180));
        assertArrayEquals(new int[]{SensorManager.AXIS_MINUS_Z,SensorManager.AXIS_X},
                GyroController.Mapping.remap(Surface.ROTATION_270));
    }
}
