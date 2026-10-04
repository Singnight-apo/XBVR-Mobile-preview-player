package top.liuwei.xbvr;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.content.res.Resources;
import android.content.res.ColorStateList;
/** Original line icons sharing a 24-unit coordinate system. */
public final class IconDrawable extends Drawable {
    private final String name;private final int baseColor;private ColorStateList tint;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    public IconDrawable(String name,int color){this.name=name;this.baseColor=color;p.setColor(color);p.setStrokeWidth(1.8f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
    private void line(Canvas c,float x,float y,float X,float Y){c.drawLine(x,y,X,Y,p);}
    private void path(Canvas c,float... a){Path h=new Path();h.moveTo(a[0],a[1]);for(int i=2;i<a.length;i+=2)h.lineTo(a[i],a[i+1]);c.drawPath(h,p);}
    @Override public void draw(Canvas canvas){Rect b=getBounds();int save=canvas.save();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);Canvas c=canvas;
        switch(name){
            case "back":path(c,14,5,7,12,14,19);line(c,7,12,21,12);break;
            case "chevron":path(c,9,5,16,12,9,19);break;
            case "search":c.drawCircle(10.5f,10.5f,6.5f,p);line(c,15.3f,15.3f,21,21);break;
            case "close":line(c,6,6,18,18);line(c,18,6,6,18);break;
            case "play":p.setStyle(Paint.Style.FILL);path(c,8,4,20,12,8,20,8,4);p.setStyle(Paint.Style.STROKE);break;
            case "pause":p.setStyle(Paint.Style.FILL);c.drawRoundRect(6,4,10,20,1,1,p);c.drawRoundRect(14,4,18,20,1,1,p);p.setStyle(Paint.Style.STROKE);break;
            case "previous":case "next":boolean prev=name.equals("previous");if(!prev){c.translate(24,0);c.scale(-1,1);}path(c,7,5,3,9,7,13);c.drawArc(4,5,21,22,210,280,false,p);p.setStyle(Paint.Style.FILL);p.setTextSize(7);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextAlign(Paint.Align.CENTER);if(!prev){c.translate(24,0);c.scale(-1,1);}c.drawText("10",12,15,p);p.setStyle(Paint.Style.STROKE);break;
            case "fullscreen":path(c,9,4,4,4,4,9);path(c,15,4,20,4,20,9);path(c,4,15,4,20,9,20);path(c,20,15,20,20,15,20);break;
            case "collapse":path(c,4,9,9,9,9,4);path(c,20,9,15,9,15,4);path(c,9,20,9,15,4,15);path(c,15,20,15,15,20,15);break;
            case "heart":Path h=new Path();h.moveTo(12,21);h.cubicTo(2,14,1,7,6,4);h.cubicTo(9,2,12,5,12,6);h.cubicTo(12,5,15,2,18,4);h.cubicTo(23,7,22,14,12,21);c.drawPath(h,p);break;
            case "clock":c.drawCircle(12,12,9,p);path(c,12,6,12,12,16,15);break;
            case "server":c.drawRoundRect(3,4,21,11,2,2,p);c.drawRoundRect(3,14,21,21,2,2,p);line(c,7,7.5f,7.1f,7.5f);line(c,7,17.5f,7.1f,17.5f);line(c,11,7.5f,17,7.5f);line(c,11,17.5f,17,17.5f);break;
            case "library":c.drawRoundRect(3,4,9,20,1,1,p);c.drawRoundRect(12,4,18,20,1,1,p);line(c,20,7,22,20);break;
            case "film":c.drawRoundRect(3,3,21,21,3,3,p);line(c,8,3,8,21);line(c,16,3,16,21);for(int y=7;y<=17;y+=5){line(c,3,y,8,y);line(c,16,y,21,y);}break;
            case "eye":Path eye=new Path();eye.moveTo(2,12);eye.cubicTo(7,3,17,3,22,12);eye.cubicTo(17,21,7,21,2,12);c.drawPath(eye,p);c.drawCircle(12,12,3,p);break;
            case "settings":c.drawCircle(12,12,4,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;line(c,12+(float)Math.cos(a)*8,12+(float)Math.sin(a)*8,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10);}c.drawCircle(12,12,8,p);break;
            case "more":p.setStyle(Paint.Style.FILL);for(int x=5;x<=19;x+=7)c.drawCircle(x,12,1.7f,p);p.setStyle(Paint.Style.STROKE);break;
            case "check":path(c,4,12,9,17,20,6);break;
            case "filter":line(c,4,6,20,6);line(c,7,12,17,12);line(c,10,18,14,18);break;
            case "refresh":case "reset":c.drawArc(4,4,20,20,35,290,false,p);path(c,20,4,20,10,14,10);break;
            case "gyro":c.drawOval(3,7,21,17,p);c.drawOval(7,3,17,21,p);c.drawCircle(12,12,2,p);break;
            default:c.drawRoundRect(4,4,20,20,4,4,p);break;
        }canvas.restoreToCount(save);
    }
    @Override public int getIntrinsicWidth(){return (int)(24*Resources.getSystem().getDisplayMetrics().density);}
    @Override public int getIntrinsicHeight(){return getIntrinsicWidth();}
    @Override public void setAlpha(int alpha){p.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);invalidateSelf();}
    @Override public void setTint(int color){setTintList(ColorStateList.valueOf(color));}
    @Override public void setTintList(ColorStateList colors){tint=colors;p.setColor(tint==null?baseColor:tint.getColorForState(getState(),tint.getDefaultColor()));invalidateSelf();}
    @Override protected boolean onStateChange(int[] state){if(tint==null)return false;int color=tint.getColorForState(state,tint.getDefaultColor());if(color==p.getColor())return false;p.setColor(color);invalidateSelf();return true;}
    @Override public boolean isStateful(){return tint!=null&&tint.isStateful();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}

