package top.liuwei.xbvr.media;
import top.liuwei.xbvr.domain.Projection;

import org.junit.Test;
import static org.junit.Assert.*;

/** T19: the renderer-side projection function keeps its domain maths after leaving the domain model. */
public class ProjectionMathTest {
    @Test public void directionNormalizationIgnoresMagnitude(){
        for(int kind:new int[]{Projection.EQUIRECT,Projection.FISHEYE}){
            Projection p=new Projection();p.kind=kind;p.capture=180;
            double[] unit=ProjectionMath.map(p,.6,0,.8,2000,1000);
            double[] scaled=ProjectionMath.map(p,6,0,8,2000,1000);
            assertNotNull(unit);
            assertArrayEquals(unit,scaled,1e-12);
        }
    }
    @Test public void fisheyeCenterOffsetMovesTheWholeImage(){
        Projection p=new Projection();
        p.kind=Projection.FISHEYE;p.capture=200;p.centerX=.25f;p.centerY=.75f;
        // angle==0 makes the ray direction degenerate: the centre is returned without NaN.
        double[] uv=ProjectionMath.map(p,0,0,1,4000,2000);
        assertNotNull(uv);
        assertEquals(.25,uv[0],1e-9);
        assertEquals(.75,uv[1],1e-9);
    }
    @Test public void tbEyeSplitsTheImageVertically(){
        Projection p=new Projection();
        p.kind=Projection.EQUIRECT;p.capture=360;p.layout=Projection.TB;
        p.eye=0;
        assertEquals(.25,ProjectionMath.map(p,0,0,1,4000,2000)[1],1e-9);
        p.eye=1;
        assertEquals(.75,ProjectionMath.map(p,0,0,1,4000,2000)[1],1e-9);
    }
    @Test public void zeroOrNegativeFrameAreaIsRejected(){
        Projection p=new Projection();
        assertNull(ProjectionMath.map(p,0,0,1,0,1080));
        assertNull(ProjectionMath.map(p,0,0,1,1920,0));
        assertNull(ProjectionMath.map(p,0,0,1,-1920,1080));
    }
    @Test public void mappedCoordinatesAreAlwaysInsideTheUnitSquare(){
        double[][] rays={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{.5,.5,.7071},{-.3,.4,.866}};
        for(int kind:new int[]{Projection.EQUIRECT,Projection.FISHEYE})
            for(int layout:new int[]{Projection.MONO,Projection.SBS,Projection.TB})
                for(int eye:new int[]{0,1})
                    for(double[] ray:rays){
                        Projection p=new Projection();p.kind=kind;p.capture=180;p.layout=layout;p.eye=eye;
                        double[] uv=ProjectionMath.map(p,ray[0],ray[1],ray[2],4000,2000);
                        if(uv==null)continue;
                        assertTrue("u="+uv[0],uv[0]>=0&&uv[0]<=1);
                        assertTrue("v="+uv[1],uv[1]>=0&&uv[1]<=1);
                    }
    }
}
