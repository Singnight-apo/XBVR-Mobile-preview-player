package top.liuwei.xbvr.domain;

/** One-finger routing and time preview. No player calls occur before a successful finish. */
public final class SeekGesture {
    public enum Mode {PENDING, PAN, SEEK, CANCELLED}
    private Mode mode=Mode.CANCELLED;
    private float startX,startY,width,slop;
    private boolean eligible;
    private long duration,startPosition,target;
    public void begin(float x,float y,float width,float usableHeight,float slop,long duration,long position) {
        this.startX=x;this.startY=y;this.width=Math.max(1,width);this.slop=Math.max(1,slop);
        this.duration=Math.max(0,duration);startPosition=Math.max(0,Math.min(position,this.duration));target=startPosition;
        eligible=duration>0&&width>0&&usableHeight>0&&y>=usableHeight*0.8f&&y<usableHeight;
        mode=Mode.PENDING;
    }
    public Mode move(float x,float y) {
        float dx=x-startX,dy=y-startY;
        if(mode==Mode.PENDING&&Math.hypot(dx,dy)>slop)
            mode=eligible&&Math.abs(dx)>Math.abs(dy)*1.5f?Mode.SEEK:Mode.PAN;
        if(mode==Mode.SEEK)target=(long)Math.max(0,Math.min(duration,startPosition+(double)dx/width*120000));
        return mode;
    }
    public Mode mode(){return mode;}
    public long target(){return target;}
    public long startPosition(){return startPosition;}
    public long duration(){return duration;}
    public long finish(){long result=mode==Mode.SEEK?target:-1;mode=Mode.CANCELLED;return result;}
    public void cancel(){mode=Mode.CANCELLED;}
    /** A remaining finger after pinch can pan, but cannot start seeking mid-contact. */
    public void continuePan(){mode=Mode.PAN;eligible=false;}
}
