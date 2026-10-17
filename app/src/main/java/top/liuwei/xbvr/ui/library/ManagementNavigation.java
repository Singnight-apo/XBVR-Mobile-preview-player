package top.liuwei.xbvr.ui.library;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import top.liuwei.xbvr.R;
import top.liuwei.xbvr.ui.common.Ui;

/** Responsive navigation only; operations and their confirmation guards stay in ManagementView. */
final class ManagementNavigation {
    final LinearLayout root,content;
    private final MainView view;
    private final LinkedHashMap<String,TextView> items=new LinkedHashMap<>();
    ManagementNavigation(MainView view,ManagementLayout.Mode mode,Consumer<String> navigate,Runnable leave) {
        this.view=view;
        boolean sidebar=mode==ManagementLayout.Mode.SIDEBAR;
        root=sidebar?Ui.row(view.activity()):Ui.column(view.activity());
        root.setGravity(Gravity.TOP);
        content=Ui.column(view.activity());
        LinearLayout menu=sidebar?Ui.column(view.activity()):Ui.row(view.activity());
        menu.setPadding(dp(12),dp(12),dp(12),dp(12));
        if(sidebar) {
            TextView title=view.label(view.tr(R.string.main_brand),18,view.colors().text);
            title.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));title.setPadding(dp(12),dp(12),dp(12),dp(24));menu.addView(title);
            root.addView(menu,new LinearLayout.LayoutParams(dp(216),-1));
            View line=new View(view.activity());line.setBackgroundColor(view.colors().border);
            root.addView(line,new LinearLayout.LayoutParams(dp(1),-1));
        } else {
            HorizontalScrollView horizontal=new HorizontalScrollView(view.activity());
            horizontal.setHorizontalScrollBarEnabled(false);horizontal.setFillViewport(true);
            horizontal.addView(menu);root.addView(horizontal,new LinearLayout.LayoutParams(-1,-2));
        }
        String[] routes={"maintenance","scrapers","matching","tasks"};
        int[] labels={R.string.management_maintenance,R.string.management_scrapers,R.string.management_matching,R.string.management_tasks};
        String[] icons={"library","refresh","search","clock"};
        for(int i=0;i<routes.length;i++) {
            String route=routes[i];TextView item=view.label(view.tr(labels[i]),14,view.colors().muted);
            item.setGravity(Gravity.CENTER_VERTICAL);item.setMinHeight(dp(56));
            item.setPadding(dp(12),dp(10),dp(12),dp(10));item.setFocusable(true);
            item.setOnClickListener(v->navigate.accept(route));
            android.graphics.drawable.Drawable icon=Ui.iconDrawable(icons[i],view.colors().accent);icon.setBounds(0,0,dp(20),dp(20));item.setCompoundDrawablesRelative(icon,null,null,null);item.setCompoundDrawablePadding(dp(10));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(sidebar?-1:0,-2);
            if(sidebar)params.bottomMargin=dp(8);else {params.weight=1;params.setMarginEnd(dp(4));}
            menu.addView(item,params);items.put(route,item);
        }
        if(sidebar) {
            menu.addView(new View(view.activity()),new LinearLayout.LayoutParams(1,0,1));
            TextView back=view.label(view.tr(R.string.management_return_settings),14,view.colors().muted);
            back.setPadding(dp(12),dp(16),dp(12),dp(16));back.setMinHeight(dp(48));
            back.setFocusable(true);back.setOnClickListener(v->leave.run());menu.addView(back);
        }
        root.addView(content,sidebar?new LinearLayout.LayoutParams(0,-1,1):new LinearLayout.LayoutParams(-1,0,1));
    }
    void select(String route) {
        String section=ManagementLayout.section(route);
        for(java.util.Map.Entry<String,TextView> entry:items.entrySet()) {
            boolean selected=section.equals(entry.getKey());TextView item=entry.getValue();
            item.setSelected(selected);item.setTextColor(selected?view.colors().accent:view.colors().muted);
            item.setTypeface(null,selected?Typeface.BOLD:Typeface.NORMAL);
            item.setBackground(Ui.ripple(view.activity(),selected?view.colors().soft:view.colors().bg,12));
        }
    }
    private int dp(int value){return Ui.dp(view.activity(),value);}
}
