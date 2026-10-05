package top.liuwei.xbvr.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** Pure format inference: projection kind, captured field of view and stereo layout. */
public final class FormatInference {
    private FormatInference(){}
    private static boolean has(String s,String regex) { return Pattern.compile(regex).matcher(s).find(); }
    private static final String EDGE="(?:^|[ _./-])", END="(?:$|[ _./-])";
    private static boolean token(String s,String pattern){return has(s,EDGE+"(?:"+pattern+")"+END);}
    private static int layout(String s) {
        if(token(s,"tb|ftb|htb|3dv|over[ _-]?under|top[ _-]?bottom"))return Projection.TB;
        if(token(s,"sbs|fsbs|hsbs|lr|3dh|side[ _-]?by[ _-]?side"))return Projection.SBS;
        if(token(s,"mono|off|2d|none"))return Projection.MONO;
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
            p.kind=Projection.FISHEYE; p.layout=Projection.SBS; p.known=true;
            p.capture=has(basis,"(?:220|mkx[ _-]?22(?![0-9]))")?220:has(basis,"200")?200:has(basis,"(?:190|rf[ _-]?52)")?190:180;
            if(basis.equals(m)&&m.equals("fisheye")&&fov>=180&&fov<=220)p.capture=(int)fov;
        } else if(token(basis,"360|sphere|equirectangular360")) {
            p.kind=Projection.EQUIRECT; p.capture=360; p.known=true; p.layout=Projection.SBS;
        } else if(token(basis,"180|dome|equirectangular|vr180")) {
            p.kind=Projection.EQUIRECT; p.capture=180; p.known=true; p.layout=Projection.SBS;
        } else if(token(basis,"flat|none|perspective|2d|sbs|fsbs|hsbs|tb|ftb|htb|3dh|3dv|overunder")||basis.equals("sbs_mono")) { p.kind=Projection.FLAT; p.known=true; }
        int metaLayout=layout(m);
        // 'flat/none' describes projection, not stereo; SBS_MONO is the legacy single-eye SBS mode.
        if(m.equals("none"))metaLayout=-1;
        if(m.equals("sbs_mono"))metaLayout=Projection.SBS;
        if(m.equals("360")||m.equals("fisheye_200")||m.equals("fisheye_220"))metaLayout=Projection.MONO;
        int chosen=metaLayout>=0?metaLayout:layout(stereo==null?"":stereo.toLowerCase(Locale.ROOT));
        if(chosen<0)chosen=layout(n);
        if(chosen>=0)p.layout=chosen;
        if(p.kind==Projection.FLAT && p.layout!=Projection.MONO) p.halfPacked=!token(m+" "+n,"full[ _-]?(?:sbs|tb)|fsbs|ftb");
        if(p.known) p.reason="自动识别："+p.label()+(p.capture==190 && p.kind==Projection.FISHEYE?"；通用等距预设，未作 RF52 专用标定":"");
        return p;
    }
}
