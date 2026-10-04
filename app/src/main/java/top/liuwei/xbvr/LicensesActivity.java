package top.liuwei.xbvr;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reads packaged legal documents in full, without network access or translation. */
public final class LicensesActivity extends Activity {
    private static final String DIRECTORY="licenses";
    private static final String STATE_FILE="licenses.file",STATE_SCROLL="licenses.scroll",STATE_CHOOSER="licenses.chooser";
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final List<String> files=new ArrayList<>();
    private TextView filename,document;
    private ScrollView scroll;
    private android.widget.Button choose;
    private AlertDialog chooser;
    private String selected="";
    private int loadVersion,restoreScroll;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(state!=null){selected=state.getString(STATE_FILE,"");restoreScroll=state.getInt(STATE_SCROLL,0);}
        boolean restoreChooser=state!=null&&state.getBoolean(STATE_CHOOSER,false);
        Ui.edgeToEdge(this,false);
        Ui.Palette c=Ui.colors(this);
        LinearLayout root=Ui.column(this);root.setBackgroundColor(c.bg);setContentView(root);Ui.insets(root);
        LinearLayout header=Ui.row(this);header.setPadding(Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,8));
        header.addView(Ui.icon(this,"back",getString(R.string.licenses_back),this::finish),new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        TextView title=Ui.text(this,getString(R.string.licenses_title),20,c.text);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));root.addView(header);
        TextView explanation=Ui.text(this,getString(R.string.licenses_help),13,c.muted);explanation.setPadding(Ui.dp(this,16),0,Ui.dp(this,16),Ui.dp(this,8));root.addView(explanation);
        choose=Ui.button(this,getString(R.string.licenses_choose),this::chooseFile);choose.setEnabled(false);choose.setContentDescription(getString(R.string.licenses_choose));
        LinearLayout.LayoutParams action=new LinearLayout.LayoutParams(-1,-2);action.setMargins(Ui.dp(this,16),0,Ui.dp(this,16),Ui.dp(this,8));root.addView(choose,action);
        filename=Ui.text(this,"",12,c.muted);filename.setPadding(Ui.dp(this,16),0,Ui.dp(this,16),Ui.dp(this,8));root.addView(filename);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(c.surface);
        document=Ui.text(this,getString(R.string.licenses_loading),14,c.text);document.setTypeface(Typeface.MONOSPACE);document.setTextIsSelectable(true);document.setLineSpacing(Ui.dp(this,2),1);document.setPadding(Ui.dp(this,16),Ui.dp(this,16),Ui.dp(this,16),Ui.dp(this,24));scroll.addView(document,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        io.execute(()->{
            try{
                List<String> found=new ArrayList<>();collect(DIRECTORY,found);Collections.sort(found,String.CASE_INSENSITIVE_ORDER);
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;files.addAll(found);choose.setEnabled(!files.isEmpty());if(files.isEmpty()){document.setText(R.string.licenses_empty);return;}if(!files.contains(selected)){selected=preferredDocument();restoreScroll=0;}load(selected,restoreScroll);if(restoreChooser)chooseFile();});
            }catch(Exception failure){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())document.setText(R.string.licenses_read_failed);});}
        });
    }
    private void collect(String folder,List<String> result)throws java.io.IOException{
        String[] children=getAssets().list(folder);if(children==null)return;
        for(String name:children){String path=folder+"/"+name;String[] nested=getAssets().list(path);if(nested!=null&&nested.length>0)collect(path,result);else{String lower=name.toLowerCase(Locale.ROOT);if(lower.endsWith(".txt")||lower.endsWith(".md")||lower.endsWith(".json"))result.add(path.substring(DIRECTORY.length()+1));}}
    }
    private String preferredDocument(){
        for(String name:files)if(name.equalsIgnoreCase("README.md"))return name;
        for(String name:files)if(name.equalsIgnoreCase("NOTICE.txt")||name.equalsIgnoreCase("NOTICE.md"))return name;
        return files.get(0);
    }
    private void chooseFile(){
        if(files.isEmpty()||chooser!=null&&chooser.isShowing())return;
        chooser=new AlertDialog.Builder(this).setTitle(R.string.licenses_choose).setSingleChoiceItems(files.toArray(new String[0]),files.indexOf(selected),(dialog,index)->{load(files.get(index),0);dialog.dismiss();}).setNegativeButton(R.string.licenses_cancel,null).create();chooser.show();Ui.decorateDialog(this,chooser);
    }
    private void load(String name,int position){
        if(!files.contains(name))return;
        selected=name;restoreScroll=position;filename.setText(name);document.setText(R.string.licenses_loading);scroll.scrollTo(0,0);int request=++loadVersion;
        io.execute(()->{
            try{
                StringBuilder text=new StringBuilder();char[] buffer=new char[4096];
                try(InputStream input=getAssets().open(DIRECTORY+"/"+name);Reader reader=new InputStreamReader(input,StandardCharsets.UTF_8)){int count;while((count=reader.read(buffer))!=-1)text.append(buffer,0,count);}
                runOnUiThread(()->{if(isFinishing()||isDestroyed()||request!=loadVersion)return;document.setText(text.toString());scroll.post(()->{if(!isDestroyed()&&request==loadVersion){scroll.scrollTo(0,position);restoreScroll=0;}});});
            }catch(Exception failure){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()&&request==loadVersion)document.setText(R.string.licenses_read_failed);});}
        });
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putString(STATE_FILE,selected);state.putInt(STATE_SCROLL,restoreScroll>0?restoreScroll:scroll.getScrollY());state.putBoolean(STATE_CHOOSER,chooser!=null&&chooser.isShowing());super.onSaveInstanceState(state);}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)Ui.edgeToEdge(this,false);}
    @Override protected void onDestroy(){if(chooser!=null)chooser.dismiss();io.shutdownNow();super.onDestroy();}
}
