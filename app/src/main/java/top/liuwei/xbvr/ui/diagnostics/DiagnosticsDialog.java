package top.liuwei.xbvr.ui.diagnostics;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import top.liuwei.xbvr.R;
import top.liuwei.xbvr.ui.common.Ui;

/**
 * The presentation half of the old {@code PlaybackDiagnostics}: it renders the bounded report and
 * the copy dialog. Storage, redaction and the crash handler stay in the Data diagnostics store; the
 * page obtains the finished report from the composition root and passes it in.
 */
public final class DiagnosticsDialog {
    private DiagnosticsDialog(){}

    public static void show(Activity activity,String report){
        if(activity==null||activity.isFinishing()||activity.isDestroyed())return;
        try{
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
}
