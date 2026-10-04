package top.liuwei.xbvr;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ApplicationExitInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Process;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Local, bounded diagnostics. Throwable messages and raw exit traces are never recorded. */
public final class PlaybackDiagnostics {
    private static final int MAX_BYTES=30*1024;
    private static final String FILE_NAME="playback-diagnostics.txt";
    private static boolean installed;
    private static String lastRendererDetails="";
    private PlaybackDiagnostics(){}

    public static synchronized void install(Context context){
        if(installed)return;
        try{
            final Context app=context.getApplicationContext();
            final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread,failure)->{
                record(app,"uncaught",failure,"");
                // Preserve Android's normal reporting and process termination.
                if(previous!=null)previous.uncaughtException(thread,failure);
                else{Process.killProcess(Process.myPid());System.exit(10);}
            });
            installed=true;
        }catch(Throwable ignored){/* Diagnostic installation must not prevent startup. */}
    }

    public static synchronized void record(Context context,String phase,Throwable failure,String deviceRendererDetails){
        try{
            StringBuilder text=new StringBuilder(context.getString(R.string.diag_recorded)).append(time(System.currentTimeMillis())).append('\n');
            text.append(device(context));
            text.append(context.getString(R.string.diag_phase)).append(safe(phase,100)).append('\n');
            String renderer=safe(deviceRendererDetails,5000);
            if(!renderer.isEmpty())lastRendererDetails=renderer;else renderer=lastRendererDetails;
            if(!renderer.isEmpty())text.append(context.getString(R.string.diag_renderer)).append(renderer).append('\n');
            Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
            for(int depth=0;failure!=null&&depth<8&&seen.add(failure);depth++,failure=failure.getCause()){
                text.append(depth==0?context.getString(R.string.diag_exception):context.getString(R.string.diag_cause)).append(failure.getClass().getName()).append('\n');
                StackTraceElement[] frames=failure.getStackTrace();
                for(int i=0;i<Math.min(frames.length,64);i++)text.append("  at ").append(safe(frames[i].toString(),500)).append('\n');
                if(frames.length>64)text.append(context.getString(R.string.diag_frames));
            }
            if(seen.isEmpty())text.append(context.getString(R.string.diag_no_exception));
            byte[] bytes=bounded(text.toString());
            File target=new File(context.getFilesDir(),FILE_NAME),temp=new File(context.getFilesDir(),FILE_NAME+".tmp");
            try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);}
            if(!temp.renameTo(target)){
                try(FileOutputStream out=new FileOutputStream(target)){out.write(bytes);}
                temp.delete();
            }
        }catch(Throwable ignored){/* Never turn a diagnostic failure into another crash. */}
    }

    public static String report(Context context){
        try{
            StringBuilder text=new StringBuilder(context.getString(R.string.diag_title)).append(device(context));
            text.append(context.getString(R.string.diag_last_record));
            String saved=read(context);
            text.append(saved.isEmpty()?context.getString(R.string.diag_no_record):saved);
            text.append(context.getString(R.string.diag_exit_records)).append(exits(context));
            text.append(context.getString(R.string.diag_local_only));
            return text.toString();
        }catch(Throwable ignored){return context.getString(R.string.diag_unavailable);}
    }

    public static void show(Activity activity){
        if(activity==null||activity.isFinishing()||activity.isDestroyed())return;
        try{
            final String report=report(activity);
            Ui.Palette colors=Ui.colors(activity);
            TextView text=Ui.text(activity,report,12,colors.text);text.setTextIsSelectable(true);
            text.setPadding(Ui.dp(activity,20),Ui.dp(activity,12),Ui.dp(activity,20),Ui.dp(activity,12));
            ScrollView scroll=new ScrollView(activity);scroll.addView(text);
            AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.diag_dialog_title))
                .setView(scroll).setNegativeButton(activity.getString(R.string.diag_close),null).setPositiveButton(activity.getString(R.string.diag_copy),(d,which)->{
                    try{
                        ClipboardManager clipboard=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        if(clipboard!=null){clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.diag_clipboard),report));Toast.makeText(activity,activity.getString(R.string.diag_copied),Toast.LENGTH_SHORT).show();}
                    }catch(Throwable ignored){/* Selectable report text remains available. */}
                }).create();
            dialog.show();Ui.decorateDialog(activity,dialog);
        }catch(Throwable ignored){/* Activity may have been destroyed while opening the dialog. */}
    }

    private static String device(Context context){
        String version=context.getString(R.string.diag_unknown);
        try{PackageInfo p=context.getPackageManager().getPackageInfo(context.getPackageName(),0);version=safe(p.versionName,60)+" ("+p.getLongVersionCode()+")";}catch(Throwable ignored){}
        return context.getString(R.string.diag_device)+safe(Build.MANUFACTURER,100)+" / "+safe(Build.MODEL,100)+context.getString(R.string.diag_android)+safe(Build.VERSION.RELEASE,60)+context.getString(R.string.diag_api)+Build.VERSION.SDK_INT+context.getString(R.string.diag_version)+version+"\n";
    }

    private static synchronized String read(Context context){
        try(FileInputStream in=new FileInputStream(new File(context.getFilesDir(),FILE_NAME))){
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int count;
            while(out.size()<MAX_BYTES&&(count=in.read(buffer,0,Math.min(buffer.length,MAX_BYTES-out.size())))>0)out.write(buffer,0,count);
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }catch(Throwable ignored){return "";}
    }

    private static String exits(Context context){
        if(Build.VERSION.SDK_INT<30)return context.getString(R.string.diag_api29);
        try{
            ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            if(manager==null)return context.getString(R.string.diag_exits_unavailable);
            List<ApplicationExitInfo> entries=manager.getHistoricalProcessExitReasons(context.getPackageName(),0,3);
            if(entries.isEmpty())return context.getString(R.string.diag_exits_empty);
            StringBuilder text=new StringBuilder();
            for(ApplicationExitInfo entry:entries.subList(0,Math.min(3,entries.size()))){
                text.append(time(entry.getTimestamp())).append(" · ").append(reason(context,entry.getReason())).append(" (").append(entry.getReason()).append(")\n");
                // Free-form descriptions may contain private information. Emit categories only.
                String description=entry.getDescription();
                if(description!=null){String value=description.toLowerCase(Locale.ROOT);String category=value.contains("memory")||value.contains("lmk")?context.getString(R.string.diag_memory):value.contains("anr")?context.getString(R.string.diag_anr):value.contains("signal")?context.getString(R.string.diag_signal):value.contains("crash")?context.getString(R.string.diag_crash):context.getString(R.string.diag_other_note);text.append(context.getString(R.string.diag_system_note)).append(category).append('\n');}
            }
            return text.toString();
        }catch(Throwable ignored){return context.getString(R.string.diag_exits_failed);}
    }

    private static String reason(Context context,int value){
        switch(value){
            case ApplicationExitInfo.REASON_EXIT_SELF:return context.getString(R.string.diag_exit_self);
            case ApplicationExitInfo.REASON_SIGNALED:return context.getString(R.string.diag_signaled);
            case ApplicationExitInfo.REASON_LOW_MEMORY:return context.getString(R.string.diag_low_memory);
            case ApplicationExitInfo.REASON_CRASH:return context.getString(R.string.diag_java_crash);
            case ApplicationExitInfo.REASON_CRASH_NATIVE:return context.getString(R.string.diag_native_crash);
            case ApplicationExitInfo.REASON_ANR:return context.getString(R.string.diag_anr);
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:return context.getString(R.string.diag_init_failed);
            case ApplicationExitInfo.REASON_PERMISSION_CHANGE:return context.getString(R.string.diag_permission_changed);
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:return context.getString(R.string.diag_resource_limit);
            case ApplicationExitInfo.REASON_USER_REQUESTED:return context.getString(R.string.diag_requested);
            case ApplicationExitInfo.REASON_USER_STOPPED:return context.getString(R.string.diag_user_stopped);
            case ApplicationExitInfo.REASON_DEPENDENCY_DIED:return context.getString(R.string.diag_dependency_died);
            case ApplicationExitInfo.REASON_OTHER:return context.getString(R.string.diag_other);
            default:return context.getString(R.string.diag_unknown_reason);
        }
    }

    private static String safe(String value,int limit){
        if(value==null)return "";
        value=value.substring(0,Math.min(value.length(),limit));
        return value.replaceAll("(?i)(?:https?|rtsp|file|content)://[^\\s]+","[URL omitted]")
            .replaceAll("(?im)(?:authorization|cookie|username|user|password|passwd|pwd|login|token|secret|credential|账号|用户名|密码)\\s*[:=][^\\r\\n]*","[credentials omitted]")
            .replaceAll("(?i)(?:Basic|Bearer)\\s+[A-Za-z0-9+/=._-]+","[credentials omitted]");
    }

    private static byte[] bounded(String value){
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        if(bytes.length<=MAX_BYTES)return bytes;
        String suffix="\n…report truncated at 30KB\n";
        int limit=MAX_BYTES-suffix.getBytes(StandardCharsets.UTF_8).length;
        while(limit>0&&(bytes[limit]&0xC0)==0x80)limit--;
        ByteArrayOutputStream out=new ByteArrayOutputStream(MAX_BYTES);out.write(bytes,0,limit);byte[] tail=suffix.getBytes(StandardCharsets.UTF_8);out.write(tail,0,tail.length);return out.toByteArray();
    }

    private static String time(long timestamp){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.ROOT).format(new java.util.Date(timestamp));}
}
