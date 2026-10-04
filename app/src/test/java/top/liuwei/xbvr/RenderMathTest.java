package top.liuwei.xbvr;

import org.junit.Test;
import static org.junit.Assert.*;

public class RenderMathTest {
    @Test public void flatFullAndHalfStereoKeepEyeAspect(){
        assertEquals(16f/9,RenderMath.eyeAspect(1920,1080,Projection.MONO,false,1),1e-6);
        assertEquals(16f/9,RenderMath.eyeAspect(3840,1080,Projection.SBS,false,1),1e-6);
        assertEquals(16f/9,RenderMath.eyeAspect(1920,2160,Projection.TB,false,1),1e-6);
        assertEquals(16f/9,RenderMath.eyeAspect(1920,1080,Projection.SBS,true,1),1e-6);
        assertEquals(16f/9,RenderMath.eyeAspect(1920,1080,Projection.TB,true,1),1e-6);
    }
    @Test public void decodedPixelAspectIsAppliedWithoutChangingEyeCrop(){
        assertEquals(4f/3,RenderMath.eyeAspect(720,576,Projection.MONO,false,16f/15),1e-6);
        assertEquals(2000,RenderMath.eyeWidth(4000,Projection.SBS),1e-6);
        assertEquals(1000,RenderMath.eyeHeight(2000,Projection.TB),1e-6);
        assertTrue(Float.isFinite(RenderMath.eyeAspect(0,0,Projection.SBS,false,Float.NaN)));
    }
    @Test public void sensorCrossingNorthDoesNotJump(){
        assertEquals(2,RenderMath.wrappedDelta(-179,179),1e-6);
        assertEquals(-2,RenderMath.wrappedDelta(179,-179),1e-6);
        assertEquals(0,RenderMath.wrappedDelta(720,0),1e-6);
        // Incremental deltas retain a manual drag while the sensor is enabled.
        float viewYaw=40;
        viewYaw+=RenderMath.wrappedDelta(20,10);
        viewYaw-=15;
        viewYaw+=RenderMath.wrappedDelta(22,20);
        assertEquals(37,viewYaw,1e-6);
    }
    @Test public void sensorTiltMatchesPhysicalCameraElevationInEveryDisplayRotation(){
        for(int rotation=0;rotation<4;rotation++)for(float elevation:new float[]{10,-10,35,-35}){
            double[] matrix=cameraMatrix(elevation,rotation*90);
            assertEquals(Math.sin(Math.toRadians(elevation)),-matrix[8],1e-6);
            float sensorPitch=androidCameraPitch(matrix,rotation);
            assertEquals(-elevation,sensorPitch,1e-5);
            float viewPitch=RenderMath.gyroPitch(0,sensorPitch,0);
            assertEquals("Physical camera elevation, rotation="+rotation,elevation,viewPitch,1e-5);
            double rayY=Math.sin(Math.toRadians(viewPitch)),rayZ=Math.cos(Math.toRadians(viewPitch));
            assertTrue("Looking up must produce an upward center ray",rayY*elevation>0);
            for(int kind:new int[]{Projection.EQUIRECT,Projection.FISHEYE}){
                Projection projection=new Projection();projection.kind=kind;projection.capture=180;
                double[] uv=projection.map(0,rayY,rayZ,1920,1080);
                assertNotNull(uv);
                assertTrue("Top-left image V must decrease when looking up",(uv[1]-.5)*elevation<0);
            }
        }
    }
    @Test public void gyroPitchRetainsManualTouchOffset(){
        float viewPitch=30;
        viewPitch=RenderMath.gyroPitch(viewPitch,5,10);
        assertEquals(35,viewPitch,1e-6);
        viewPitch=RenderMath.gyroPitch(viewPitch,10,5);
        assertEquals(30,viewPitch,1e-6);
    }
    @Test public void gyroPitchUsesShortestWrappedDelta(){
        assertEquals(28,RenderMath.gyroPitch(30,-179,179),1e-6);
        assertEquals(32,RenderMath.gyroPitch(30,179,-179),1e-6);
        assertEquals(30,RenderMath.gyroPitch(30,720,0),1e-6);
    }
    @Test public void gyroPitchClampsToViewLimits(){
        assertEquals(85,RenderMath.gyroPitch(80,-20,0),1e-6);
        assertEquals(-85,RenderMath.gyroPitch(-80,20,0),1e-6);
        assertEquals(85,RenderMath.gyroPitch(84,-2,0),1e-6);
    }
    /** Device screen upright; the camera looks along -device Z, with physical elevation above horizon. */
    private static double[] cameraMatrix(double elevation,double screenRoll){
        double theta=Math.toRadians(90+elevation),roll=Math.toRadians(screenRoll);
        double ct=Math.cos(theta),st=Math.sin(theta),cr=Math.cos(roll),sr=Math.sin(roll);
        return new double[]{cr,-sr,0,ct*sr,ct*cr,-st,st*sr,st*cr,ct};
    }
    /** Analytic column permutations of the production remap axes, followed by Android's pitch formula. */
    private static float androidCameraPitch(double[] matrix,int rotation){
        // 0: X,Z; 90: Z,-X; 180: -X,-Z; 270: -Z,X.
        int[][] columns={{1,-3,2},{-2,-3,1},{-1,-3,-2},{2,-3,-1}};
        double[] remap=new double[9];
        for(int row=0;row<3;row++)for(int col=0;col<3;col++){
            int source=columns[rotation][col];
            remap[row*3+col]=Math.signum(source)*matrix[row*3+Math.abs(source)-1];
        }
        return (float)Math.toDegrees(Math.asin(-remap[7]));
    }
}
