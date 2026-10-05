package top.liuwei.xbvr.domain;

/** Pure math/model shared by renderer and tests. Coordinates use top-left image UV. */
public final class Projection {
    public static final int FLAT=0, EQUIRECT=1, FISHEYE=2;
    public static final int MONO=0, SBS=1, TB=2;
    public int kind=FLAT, layout=MONO, capture=180, eye=0;
    public float yaw=0, pitch=0, viewFov=75, centerX=.5f, centerY=.5f, radius=1, rotation=0;
    public boolean mirror=false, halfPacked=false, known=false;
    public String reason="格式信息不足，请在格式菜单确认";
    public String label() { return (kind==FLAT?"平面":capture+"° "+(kind==FISHEYE?"等距鱼眼":"全景"))+" · "+new String[]{"单目","左右 SBS","上下 TB"}[layout]; }
    public double[] map(double x,double y,double z,int width,int height) {
        double norm=Math.sqrt(x*x+y*y+z*z);if(!Double.isFinite(norm)||norm<1e-12||width<=0||height<=0)return null;x/=norm;y/=norm;z/=norm;
        double u,v;
        if(kind==FISHEYE) {
            double angle=Math.acos(Math.max(-1,Math.min(1,z))), half=Math.toRadians(capture/2.0);
            if(angle>half) return null;
            double l=Math.hypot(x,y), qx=l<1e-9?0:x/l,qy=l<1e-9?0:y/l;
            double a=Math.toRadians(rotation), rx=(qx*Math.cos(a)-qy*Math.sin(a))*(mirror?-1:1),ry=qx*Math.sin(a)+qy*Math.cos(a);
            double w=width/(layout==SBS?2.0:1),h=height/(layout==TB?2.0:1),r=Math.min(w,h)*.5*radius;
            u=centerX+rx*angle/half*r/w;v=centerY-ry*angle/half*r/h;
        } else {
            double lon=Math.atan2(x,z);
            if(Math.abs(lon)>Math.toRadians(capture/2.0))return null;
            u=.5+lon/Math.toRadians(capture);v=.5-Math.asin(y)/Math.PI;
        }
        if(u<0||u>1||v<0||v>1)return null;
        if(layout==SBS)u=(u+eye)*.5;
        if(layout==TB)v=(v+eye)*.5;
        return new double[]{u,v};
    }
}
