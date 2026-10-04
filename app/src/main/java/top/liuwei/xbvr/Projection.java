package top.liuwei.xbvr;

import java.util.Locale;
import java.util.regex.Pattern;

/** Pure math/model shared by renderer and tests. Coordinates use top-left image UV. */
public final class Projection {
    public static final int FLAT=0, EQUIRECT=1, FISHEYE=2;
    public static final int MONO=0, SBS=1, TB=2;
    public int kind=FLAT, layout=MONO, capture=180, eye=0;
    public float yaw=0, pitch=0, viewFov=75, centerX=.5f, centerY=.5f, radius=1, rotation=0;
    public boolean mirror=false, halfPacked=false, known=false;
    public String reason="格式信息不足，请在格式菜单确认";
    private static boolean has(String s,String regex) { return Pattern.compile(regex).matcher(s).find(); }
    private static final String EDGE="(?:^|[ _./-])", END="(?:$|[ _./-])";
    private static boolean token(String s,String pattern){return has(s,EDGE+"(?:"+pattern+")"+END);}
    private static int layout(String s) {
        if(token(s,"tb|ftb|htb|3dv|over[ _-]?under|top[ _-]?bottom"))return TB;
        if(token(s,"sbs|fsbs|hsbs|lr|3dh|side[ _-]?by[ _-]?side"))return SBS;
        if(token(s,"mono|off|2d|none"))return MONO;
        return -1;
    }
    public static Projection infer(String metadata,String filename,String stereo,double fov) {
        Projection p=new Projection();
        String m=metadata==null?"":metadata.trim().toLowerCase(Locale.ROOT);
        String n=filename==null?"":filename.toLowerCase(Locale.ROOT);
        String specific="mkx[ _-]?(?:200|220|22)|vrca[ _-]?220|rf[ _-]?52|fisheye(?:[ _-]?(?:180|190|200|220))?|f180|180f|vr[ _-]?(?:190|200|220)";
        String unsupported="dual[ _-]?fisheye|front[ _-]?back|fb360|eac|cubemap|cube";
        boolean authoritative=has(m,"^(?:flat|none|perspective|sbs_mono|360|(?:180|360)[ _-](?:mono|sbs|lr|tb))$")||(!m.equals("fisheye")&&token(m,specific))||token(m,unsupported);
        // Reliable selected-file metadata wins; lens tags refine only generic scene projection.
        String basis=authoritative?m:token(n,unsupported)||token(n,specific)?n:!m.isBlank()?m:n;
        if(token(basis,unsupported)) {
            p.reason="前后双鱼眼 / Cube / EAC 尚不支持，请选择已展开文件"; return p;
        }
        if(token(basis,specific)) {
            p.kind=FISHEYE; p.layout=SBS; p.known=true;
            p.capture=has(basis,"(?:220|mkx[ _-]?22(?![0-9]))")?220:has(basis,"200")?200:has(basis,"(?:190|rf[ _-]?52)")?190:180;
            if(basis.equals(m)&&m.equals("fisheye")&&fov>=180&&fov<=220)p.capture=(int)fov;
        } else if(token(basis,"360|sphere|equirectangular360")) {
            p.kind=EQUIRECT; p.capture=360; p.known=true; p.layout=SBS;
        } else if(token(basis,"180|dome|equirectangular|vr180")) {
            p.kind=EQUIRECT; p.capture=180; p.known=true; p.layout=SBS;
        } else if(token(basis,"flat|none|perspective|2d|sbs|fsbs|hsbs|tb|ftb|htb|3dh|3dv|overunder")||basis.equals("sbs_mono")) { p.kind=FLAT; p.known=true; }
        int metaLayout=layout(m);
        // 'flat/none' describes projection, not stereo; SBS_MONO is the legacy single-eye SBS mode.
        if(m.equals("none"))metaLayout=-1;
        if(m.equals("sbs_mono"))metaLayout=SBS;
        if(m.equals("360")||m.equals("fisheye_200")||m.equals("fisheye_220"))metaLayout=MONO;
        int chosen=metaLayout>=0?metaLayout:layout(stereo==null?"":stereo.toLowerCase(Locale.ROOT));
        if(chosen<0)chosen=layout(n);
        if(chosen>=0)p.layout=chosen;
        if(p.kind==FLAT && p.layout!=MONO) p.halfPacked=!token(m+" "+n,"full[ _-]?(?:sbs|tb)|fsbs|ftb");
        if(p.known) p.reason="自动识别："+p.label()+(p.capture==190 && p.kind==FISHEYE?"；通用等距预设，未作 RF52 专用标定":"");
        return p;
    }
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
