package top.liuwei.xbvr;
import android.app.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.*;
import android.widget.*;
/** Shared styling resolved from the current system night configuration. */
public final class Ui {
    public static final int BG=0xFF101216,PANEL=0xFF1B1E24,TEXT=0xFFF2F4F7,MUTED=0xFFA4ACB7,ACCENT=0xFF65D6AE;
    public static final class Palette {
        public final int bg,surface,raised,text,muted,border,accent,onAccent,soft;
        private Palette(boolean d){bg=d?BG:0xFFF5F7F8;surface=d?PANEL:0xFFFFFFFF;raised=d?0xFF252931:0xFFEDF1F3;text=d?TEXT:0xFF172028;muted=d?MUTED:0xFF667480;border=d?0xFF303640:0xFFDCE3E7;accent=d?ACCENT:0xFF087C62;onAccent=d?0xFF10251D:Color.WHITE;soft=d?0xFF1E362D:0xFFDDF2EA;}
    }
    public static boolean dark(Context c){return (c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;}
    public static Palette colors(Context c){return new Palette(dark(c));}
    public static int dp(Context c,float n){return (int)(n*c.getResources().getDisplayMetrics().density+.5f);}
    public static TextView text(Activity a,String s,int size,int color){TextView t=new TextView(a);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");return t;}
    public static Drawable rounded(int color,float radiusDp,Context c){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radiusDp));return d;}
    public static Drawable ripple(Context c,int background,float radiusDp){return new RippleDrawable(ColorStateList.valueOf(dark(c)?0x24FFFFFF:0x18000000),rounded(background,radiusDp,c),rounded(Color.WHITE,radiusDp,c));}
    public static Button button(Activity a,String s,Runnable action){Palette p=colors(a);Button b=new Button(a);b.setText(s);b.setTextSize(14);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setTextColor(p.accent);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(a,48));b.setMinimumHeight(dp(a,48));b.setPadding(dp(a,16),0,dp(a,16),0);b.setBackground(ripple(a,p.soft,16));b.setStateListAnimator(null);b.setOnClickListener(v->action.run());return b;}
    public static Drawable iconDrawable(String name,int color){return new IconDrawable(name,color);}
    public static ImageButton icon(Activity a,String name,String label,Runnable action){ImageButton b=new ImageButton(a);b.setImageDrawable(iconDrawable(name,colors(a).text));b.setContentDescription(label);b.setScaleType(ImageView.ScaleType.FIT_CENTER);b.setPadding(dp(a,12),dp(a,12),dp(a,12),dp(a,12));b.setMinimumWidth(dp(a,48));b.setMinimumHeight(dp(a,48));b.setBackground(ripple(a,Color.TRANSPARENT,24));b.setLayoutParams(new android.view.ViewGroup.LayoutParams(dp(a,48),dp(a,48)));b.setOnClickListener(v->action.run());return b;}
    public static LinearLayout column(Activity a){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);return l;}
    public static LinearLayout row(Activity a){LinearLayout l=new LinearLayout(a);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    public static void decorateDialog(Activity a,Dialog d){Window w=d.getWindow();if(w!=null){Palette p=colors(a);w.setBackgroundDrawable(rounded(p.surface,24,a));w.setNavigationBarColor(p.surface);}}
    public static void error(Activity a,Throwable e){if(!a.isFinishing()){AlertDialog d=new AlertDialog.Builder(a).setTitle(R.string.common_error_title).setMessage(errorMessage(a,e)).setPositiveButton(R.string.common_ok,null).create();d.show();decorateDialog(a,d);}}

    public static String projectionLabel(Context c,Projection p){String kind=p.kind==Projection.FLAT?c.getString(R.string.projection_flat):p.capture+"° "+c.getString(p.kind==Projection.FISHEYE?R.string.projection_fisheye:R.string.projection_panorama);return kind+" · "+c.getString(new int[]{R.string.projection_mono,R.string.projection_sbs,R.string.projection_tb}[p.layout]);}
    public static String projectionReason(Context c,Projection p){String reason=p.reason;if(reason.startsWith("自动识别："))return c.getString(R.string.projection_auto,projectionLabel(c,p))+(p.capture==190&&p.kind==Projection.FISHEYE?c.getString(R.string.projection_rf52):"");if(reason.equals("使用已保存的手动格式"))return c.getString(R.string.projection_saved);if(reason.startsWith("手动设置"))return c.getString(R.string.projection_manual)+(reason.contains("RF52")?c.getString(R.string.projection_manual_rf52):"");if(reason.equals("格式信息不足，请在格式菜单确认"))return c.getString(R.string.projection_unknown);if(reason.equals("前后双鱼眼 / Cube / EAC 尚不支持，请选择已展开文件"))return c.getString(R.string.projection_unsupported);return reason;}
    public static String errorMessage(Context c,Throwable e){String message=e.getMessage();if(message==null)return e.getClass().getSimpleName();switch(message){
        case "请输入 HTTP/HTTPS 地址；账号请填在独立字段":return c.getString(R.string.common_error_0);
        case "播放器账号或密码错误":return c.getString(R.string.common_error_1);
        case "需要播放器账号，请在服务器设置中登录":return c.getString(R.string.common_error_2);
        case "目录接口没有 scenes；请确认 XBVR 已启用 DeoVR":return c.getString(R.string.common_error_3);
        case "详情地址跨服务器，未转发账号，请检查 XBVR 返回的地址":return c.getString(R.string.common_error_4);
        case "接口重定向到另一服务器，已停止转发账号；请填写最终 XBVR 地址":return c.getString(R.string.common_error_5);
        case "接口返回为空，请在 XBVR 开启播放器接口":return c.getString(R.string.common_error_6);
        case "接口未返回 JSON；检查反向代理地址与登录页":return c.getString(R.string.common_error_7);
        case "接口重定向次数过多，请检查代理配置":return c.getString(R.string.common_error_8);
        case "场景没有可用媒体文件，请检查 XBVR 文件可用状态":return c.getString(R.string.common_error_9);
        case "服务器未开放收藏写入":return c.getString(R.string.common_error_10);
        case "服务器未确认收藏结果":return c.getString(R.string.common_error_11);
        case "重新读取后收藏状态不一致":return c.getString(R.string.common_error_12);
        case "找不到服务器配置":return c.getString(R.string.common_error_13);
        default:java.util.regex.Matcher match=java.util.regex.Pattern.compile("接口 HTTP (\\d+)；检查地址、认证与接口设置").matcher(message);return match.matches()?c.getString(R.string.common_http_error,match.group(1)):message;
    }}
    public static String time(long ms){long s=Math.max(0,ms)/1000;return s>=3600?String.format(java.util.Locale.ROOT,"%d:%02d:%02d",s/3600,(s/60)%60,s%60):String.format(java.util.Locale.ROOT,"%d:%02d",s/60,s%60);}
    public static void edgeToEdge(Activity a,boolean player){Window w=a.getWindow();w.getDecorView();boolean light=!player&&!dark(a);w.setStatusBarColor(Color.TRANSPARENT);w.setNavigationBarColor(Color.TRANSPARENT);w.setStatusBarContrastEnforced(false);w.setNavigationBarContrastEnforced(false);WindowManager.LayoutParams lp=w.getAttributes();lp.layoutInDisplayCutoutMode=Build.VERSION.SDK_INT>=30?WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS:WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;w.setAttributes(lp);
        if(Build.VERSION.SDK_INT>=30){w.setDecorFitsSystemWindows(false);WindowInsetsController ctl=w.getInsetsController();if(ctl!=null){int mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;ctl.setSystemBarsAppearance(light?mask:0,mask);if(player){ctl.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);ctl.hide(WindowInsets.Type.systemBars());}else ctl.show(WindowInsets.Type.systemBars());}}
        else{int flags=View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;if(light)flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;if(player)flags|=View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;w.getDecorView().setSystemUiVisibility(flags);}w.getDecorView().requestApplyInsets();
    }
    public static void insets(View v){safeInsets(v,false);}
    public static void playerInsets(View v){safeInsets(v,true);}
    private static void safeInsets(View view,boolean player){final int bl=view.getPaddingLeft(),bt=view.getPaddingTop(),br=view.getPaddingRight(),bb=view.getPaddingBottom();view.setOnApplyWindowInsetsListener((v,in)->{int l=0,t=0,r=0,b=0;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=in.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());l=bars.left;t=bars.top;r=bars.right;b=bars.bottom;if(!player)b=Math.max(b,in.getInsets(WindowInsets.Type.ime()).bottom);else b=Math.max(b,in.getInsets(WindowInsets.Type.mandatorySystemGestures()).bottom);}else{DisplayCutout cut=in.getDisplayCutout();if(cut!=null){l=cut.getSafeInsetLeft();t=cut.getSafeInsetTop();r=cut.getSafeInsetRight();b=cut.getSafeInsetBottom();}if(!player){l=Math.max(l,in.getSystemWindowInsetLeft());t=Math.max(t,in.getSystemWindowInsetTop());r=Math.max(r,in.getSystemWindowInsetRight());b=Math.max(b,in.getSystemWindowInsetBottom());}else b=Math.max(b,in.getMandatorySystemGestureInsets().bottom);}v.setPadding(bl+l,bt+t,br+r,bb+b);return in;});view.requestApplyInsets();}
}


