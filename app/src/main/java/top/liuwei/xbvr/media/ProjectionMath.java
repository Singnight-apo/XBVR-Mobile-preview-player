package top.liuwei.xbvr.media;
import top.liuwei.xbvr.domain.Projection;

/**
 * Renderer-side ray → top-left image UV projection. Moved line for line from the domain model so the
 * domain no longer carries renderer algorithm; the maths, defaults and edge behaviour are unchanged.
 */
public final class ProjectionMath {
    private ProjectionMath() {}
    public static double[] map(Projection p,double x,double y,double z,int width,int height) {
        double norm=Math.sqrt(x*x+y*y+z*z);if(!Double.isFinite(norm)||norm<1e-12||width<=0||height<=0)return null;x/=norm;y/=norm;z/=norm;
        double u,v;
        if(p.kind==Projection.FISHEYE) {
            double angle=Math.acos(Math.max(-1,Math.min(1,z))), half=Math.toRadians(p.capture/2.0);
            if(angle>half) return null;
            double l=Math.hypot(x,y), qx=l<1e-9?0:x/l,qy=l<1e-9?0:y/l;
            double a=Math.toRadians(p.rotation), rx=(qx*Math.cos(a)-qy*Math.sin(a))*(p.mirror?-1:1),ry=qx*Math.sin(a)+qy*Math.cos(a);
            double w=width/(p.layout==Projection.SBS?2.0:1),h=height/(p.layout==Projection.TB?2.0:1),r=Math.min(w,h)*.5*p.radius;
            u=p.centerX+rx*angle/half*r/w;v=p.centerY-ry*angle/half*r/h;
        } else {
            double lon=Math.atan2(x,z);
            if(Math.abs(lon)>Math.toRadians(p.capture/2.0))return null;
            u=.5+lon/Math.toRadians(p.capture);v=.5-Math.asin(y)/Math.PI;
        }
        if(u<0||u>1||v<0||v>1)return null;
        if(p.layout==Projection.SBS)u=(u+p.eye)*.5;
        if(p.layout==Projection.TB)v=(v+p.eye)*.5;
        return new double[]{u,v};
    }
}
