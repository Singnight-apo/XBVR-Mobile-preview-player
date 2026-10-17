package top.liuwei.xbvr.ui.library;

import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.*;
import top.liuwei.xbvr.R;
import top.liuwei.xbvr.domain.StorageSummary;
import top.liuwei.xbvr.ui.common.Ui;

/** The approved overview hierarchy; receives already guarded action buttons from ManagementView. */
final class StorageOverviewViews {
    private final MainView view;
    private final StorageDirectoryViews format;
    private boolean listKnown;
    StorageOverviewViews(MainView view,StorageDirectoryViews format){this.view=view;this.format=format;}
    private int dp(int value){return Ui.dp(view.activity(),value);}
    void addTo(LinearLayout body,StorageSummary snapshot,int availableWidth,boolean listKnown,boolean compact,boolean wide,Button add,Button all,Button matching){
        this.listKnown=listKnown;
        add.setTextColor(add.isEnabled()?view.colors().onAccent:view.colors().muted);
        add.setBackground(Ui.ripple(view.activity(),add.isEnabled()?view.colors().accent:view.colors().raised,12));
        all.setTextColor(all.isEnabled()?view.colors().onAccent:view.colors().muted);
        all.setBackground(Ui.ripple(view.activity(),all.isEnabled()?view.colors().accent:view.colors().raised,12));
        matching.setText(view.tr(!wide&&!compact?R.string.storage_matching_short_count:R.string.storage_matching_count,format.count(snapshot.unmatchedFiles)));
        matching.setContentDescription(view.tr(R.string.storage_matching_count,format.count(snapshot.unmatchedFiles)));
        if(compact){
            LinearLayout row=Ui.row(view.activity());LinearLayout title=Ui.column(view.activity());
            title.addView(label(view.tr(R.string.storage_heading),18,true));
            title.addView(view.label(format.bytes(snapshot.totalBytes)+" · "+counts(snapshot),12,view.colors().muted),view.spacing(-1,-2,0,4,0,0));
            row.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            row.addView(add,view.spacing(-2,-2,12,0,0,0));row.addView(all,view.spacing(-2,-2,8,0,0,0));
            body.addView(row,view.spacing(-1,-2,0,4,0,16));return;
        }
        LinearLayout heading=Ui.row(view.activity());heading.addView(label(view.tr(R.string.storage_heading),20,true),new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(add);body.addView(heading,view.spacing(-1,-2,0,4,0,16));
        LinearLayout summary=Ui.column(view.activity());
        TextView amount=label(format.bytes(snapshot.totalBytes),32,true);
        TextView explanation=view.label(view.tr(R.string.storage_total_capacity),12,view.colors().muted);
        int actionWidth=wide?Math.round(all.getPaint().measureText(all.getText().toString())+matching.getPaint().measureText(matching.getText().toString()))+dp(122):0;
        boolean inline=amount.getPaint().measureText(amount.getText().toString())+explanation.getPaint().measureText(explanation.getText().toString())+dp(10)<=dp(availableWidth)-actionWidth;
        LinearLayout capacity=inline?Ui.row(view.activity()):Ui.column(view.activity());
        capacity.setBaselineAligned(false);capacity.setGravity(Gravity.BOTTOM);capacity.addView(amount);
        LinearLayout.LayoutParams caption=inline?new LinearLayout.LayoutParams(0,-2,1):new LinearLayout.LayoutParams(-1,-2);
        if(inline){caption.setMarginStart(dp(10));caption.bottomMargin=dp(6);}else caption.topMargin=dp(4);
        capacity.addView(explanation,caption);
        summary.addView(capacity);summary.addView(view.label(counts(snapshot),13,view.colors().muted),view.spacing(-1,-2,0,4,0,0));
        LinearLayout actions=Ui.row(view.activity());
        if(wide){
            LinearLayout strip=Ui.row(view.activity());strip.setGravity(Gravity.BOTTOM);
            strip.setBaselineAligned(false);
            strip.addView(summary,new LinearLayout.LayoutParams(0,-2,1));
            actions.addView(all);actions.addView(matching,view.spacing(-2,-2,10,0,0,0));strip.addView(actions);
            body.addView(strip,view.spacing(-1,-2,0,0,0,12));
        }else{
            body.addView(summary,view.spacing(-1,-2,0,0,0,16));
            LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1);left.setMarginEnd(dp(8));
            actions.addView(all,left);actions.addView(matching,new LinearLayout.LayoutParams(0,-2,1));
            body.addView(actions,view.spacing(-1,-2,0,0,0,16));
        }
    }
    private String counts(StorageSummary snapshot){return !listKnown?view.tr(R.string.management_unknown):view.tr(R.string.storage_counts,snapshot.volumes.size(),format.count(snapshot.totalFiles),format.count(snapshot.unmatchedFiles));}
    private TextView label(String text,int size,boolean bold){TextView value=view.label(text,size,view.colors().text);value.setIncludeFontPadding(false);value.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return value;}
}
