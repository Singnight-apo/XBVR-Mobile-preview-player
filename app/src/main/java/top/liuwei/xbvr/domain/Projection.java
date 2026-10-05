package top.liuwei.xbvr.domain;

/** Pure projection model/config shared by renderer and tests. Coordinates use top-left image UV; the ray maths lives in media/ProjectionMath. */
public final class Projection {
    public static final int FLAT=0, EQUIRECT=1, FISHEYE=2;
    public static final int MONO=0, SBS=1, TB=2;
    public int kind=FLAT, layout=MONO, capture=180, eye=0;
    public float yaw=0, pitch=0, viewFov=75, centerX=.5f, centerY=.5f, radius=1, rotation=0;
    public boolean mirror=false, halfPacked=false, known=false;
    public String reason="格式信息不足，请在格式菜单确认";
    public String label() { return (kind==FLAT?"平面":capture+"° "+(kind==FISHEYE?"等距鱼眼":"全景"))+" · "+new String[]{"单目","左右 SBS","上下 TB"}[layout]; }
}
