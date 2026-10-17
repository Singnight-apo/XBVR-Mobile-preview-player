package top.liuwei.xbvr.ui.library;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.function.Consumer;
import java.util.function.Function;
import top.liuwei.xbvr.R;
import top.liuwei.xbvr.domain.StorageSummary;
import top.liuwei.xbvr.domain.StorageSummary.Volume;
import top.liuwei.xbvr.ui.common.Ui;

/** Read-only presentation. Confirmation, server identity and writes stay in ManagementView. */
final class StorageDirectoryViews {
    private final MainView view;
    private final Function<Volume,Button> scan;
    private final Consumer<Volume> details;
    private final Function<Volume,String> lastScan;
    final java.util.Map<TextView,Long> scanLabels=new java.util.HashMap<>();
    StorageDirectoryViews(MainView view,Function<Volume,Button> scan,Consumer<Volume> details,
                          Function<Volume,String> lastScan) {
        this.view=view;this.scan=scan;this.details=details;this.lastScan=lastScan;
    }
    String count(Long value){return value==null?view.tr(R.string.management_unknown):java.text.NumberFormat.getIntegerInstance().format(value);}
    String bytes(Long value){String formatted=StorageSize.format(value,view.activity().getResources().getConfiguration().getLocales().get(0));return formatted==null?view.tr(R.string.management_unknown):formatted;}
    private TextView label(String value,int size,int color){TextView label=view.label(value,size,color);label.setIncludeFontPadding(false);label.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));return label;}
    private boolean tablet(){android.content.res.Configuration c=view.activity().getResources().getConfiguration();return c.smallestScreenWidthDp>=600&&c.screenWidthDp>=600;}
    private int dp(int n){return Ui.dp(view.activity(),n);}
    private String status(Volume volume){return view.tr(volume.available==null?R.string.management_unknown:Boolean.TRUE.equals(volume.available)?R.string.storage_available:R.string.storage_unavailable);}
    private TextView statusLabel(Volume volume){
        int color=Boolean.TRUE.equals(volume.available)?view.colors().accent:view.colors().muted;
        TextView value=label(status(volume),12,color);
        android.graphics.drawable.Drawable dot=view.activity().getDrawable(android.R.drawable.presence_online).mutate();
        dot.setTint(color);dot.setBounds(0,0,dp(12),dp(12));value.setCompoundDrawablesRelative(dot,null,null,null);value.setCompoundDrawablePadding(dp(6));return value;
    }
    void addTo(LinearLayout container,StorageSummary snapshot,ManagementLayout.StorageMode mode){
        if(mode==ManagementLayout.StorageMode.TABLE)container.addView(columns());
        for(int i=0;i<snapshot.volumes.size();i++){
            Volume volume=snapshot.volumes.get(i);
            if(i>0){View line=new View(view.activity());line.setBackgroundColor(view.colors().border);container.addView(line,view.spacing(-1,1,16,0,16,0));}
            container.addView(mode==ManagementLayout.StorageMode.TABLE?tableRow(volume):
                mode==ManagementLayout.StorageMode.COMPACT?landscapeRow(volume):group(volume),new LinearLayout.LayoutParams(-1,-2));
        }
    }
    private LinearLayout columns(){
        LinearLayout row=Ui.row(view.activity());row.setPadding(dp(16),dp(12),dp(16),dp(12));
        row.setBackground(Ui.rounded(view.colors().raised,12,view.activity()));
        cell(row,view.tr(R.string.management_directory_column),0,1,true,false);
        cell(row,view.tr(R.string.storage_status),92,0,true,false);
        cell(row,view.tr(R.string.storage_files),64,0,true,true);
        cell(row,view.tr(R.string.storage_unmatched),92,0,true,true);
        cell(row,view.tr(R.string.storage_capacity),96,0,true,true);
        cell(row,view.tr(R.string.management_scan_column),108,0,true,false);
        cell(row,view.tr(R.string.management_operation_column),144,0,true,false);return row;
    }
    private void cell(LinearLayout row,String text,int width,float weight,boolean heading,boolean numeric){
        TextView value=label(text,heading?12:14,heading?view.colors().muted:view.colors().text);
        value.setGravity((numeric?Gravity.END:Gravity.START)|Gravity.CENTER_VERTICAL);
        value.setPadding(dp(6),dp(4),dp(6),dp(4));
        row.addView(value,new LinearLayout.LayoutParams(dp(width),-2,weight));
    }
    private TextView path(Volume volume,int size){
        TextView title=label(volume.path,size,view.colors().text);
        title.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));title.setMaxLines(3);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setFocusable(true);title.setOnClickListener(v->details.accept(volume));return title;
    }
    private ImageButton more(Volume volume){return Ui.icon(view.activity(),"more_vertical",view.tr(R.string.storage_directory_details,volume.path),()->details.accept(volume));}
    private LinearLayout actions(Volume volume){
        LinearLayout row=Ui.row(view.activity());row.addView(scan.apply(volume),new LinearLayout.LayoutParams(dp(96),-2));
        row.addView(more(volume),new LinearLayout.LayoutParams(dp(48),dp(48)));return row;
    }
    private LinearLayout tableRow(Volume volume){
        LinearLayout row=Ui.row(view.activity());row.setPadding(dp(16),dp(16),dp(16),dp(16));row.setMinimumHeight(dp(84));
        row.addView(path(volume,15),new LinearLayout.LayoutParams(0,-2,1));
        row.addView(statusLabel(volume),new LinearLayout.LayoutParams(dp(92),-2));
        cell(row,count(volume.totalFiles),64,0,false,true);
        cell(row,count(volume.unmatchedFiles),92,0,false,true);
        cell(row,bytes(volume.totalBytes),96,0,false,true);
        TextView time=time(volume,true);time.setPadding(dp(6),0,dp(6),0);
        row.addView(time,new LinearLayout.LayoutParams(dp(108),-2));
        row.addView(actions(volume),new LinearLayout.LayoutParams(dp(144),-2));return row;
    }
    private TextView time(Volume volume,boolean ageOnly){
        TextView label=label(lastScan.apply(volume),12,view.colors().muted);label.setTag(ageOnly);
        android.graphics.drawable.Drawable clock=Ui.iconDrawable("clock",view.colors().muted);clock.setBounds(0,0,dp(18),dp(18));
        label.setCompoundDrawablesRelative(clock,null,null,null);label.setCompoundDrawablePadding(dp(6));
        scanLabels.put(label,top.liuwei.xbvr.domain.ScanTimestamp.parse(volume.lastScan));return label;
    }
    private LinearLayout metric(int name,String value){
        LinearLayout box=Ui.column(view.activity());
        TextView caption=label(view.tr(name),12,view.colors().muted);caption.setMaxLines(2);
        box.addView(caption);TextView number=label(value,16,view.colors().text);
        number.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));box.addView(number,view.spacing(-1,-2,0,3,0,0));return box;
    }
    private LinearLayout metrics(Volume volume){
        LinearLayout row=Ui.row(view.activity());row.setGravity(Gravity.TOP);
        int[] names={R.string.storage_files,R.string.storage_unmatched,R.string.storage_capacity};
        String[] values={count(volume.totalFiles),count(volume.unmatchedFiles),bytes(volume.totalBytes)};
        for(int i=0;i<names.length;i++){
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
            if(i<names.length-1)p.setMarginEnd(dp(8));row.addView(metric(names[i],values[i]),p);
        }
        return row;
    }
    private LinearLayout group(Volume volume){
        LinearLayout group=Ui.column(view.activity());group.setPadding(dp(16),dp(8),dp(16),0);
        boolean tablet=tablet();
        LinearLayout heading=Ui.row(view.activity());heading.addView(path(volume,15),new LinearLayout.LayoutParams(0,-2,1));
        if(tablet)heading.addView(statusLabel(volume),view.spacing(-2,-2,12,0,4,0));
        heading.addView(more(volume),new LinearLayout.LayoutParams(dp(48),dp(48)));group.addView(heading);
        if(!tablet)group.addView(statusLabel(volume));
        group.addView(metrics(volume),view.spacing(-1,-2,0,8,0,8));
        View divider=new View(view.activity());divider.setBackgroundColor(view.colors().border);
        group.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        LinearLayout footer=Ui.row(view.activity());
        TextView timestamp=time(volume,false);
        footer.addView(timestamp,new LinearLayout.LayoutParams(0,-2,1));
        footer.addView(scan.apply(volume),view.spacing(dp(112),-2,12,0,0,0));
        group.addView(footer);return group;
    }
    private LinearLayout landscapeRow(Volume volume){
        LinearLayout row=Ui.row(view.activity());row.setPadding(dp(16),dp(12),dp(16),dp(12));row.setMinimumHeight(dp(80));
        LinearLayout title=Ui.column(view.activity());title.addView(path(volume,15));
        title.addView(statusLabel(volume),view.spacing(-1,-2,0,4,0,0));
        title.addView(time(volume,false),view.spacing(-1,-2,0,3,0,0));
        row.addView(title,new LinearLayout.LayoutParams(0,-2,1.15f));
        LinearLayout.LayoutParams stats=new LinearLayout.LayoutParams(0,-2,1f);stats.setMarginStart(dp(16));stats.setMarginEnd(dp(12));
        row.addView(metrics(volume),stats);
        row.addView(actions(volume),new LinearLayout.LayoutParams(dp(144),-2));return row;
    }
}
