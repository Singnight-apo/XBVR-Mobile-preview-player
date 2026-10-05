package top.liuwei.xbvr;

import android.app.Activity;
import android.os.Bundle;
import top.liuwei.xbvr.ui.licenses.LicenseView;

/** Hosts the packaged legal documents view; all document work stays in {@link LicenseView}. */
public final class LicensesActivity extends Activity {
    private LicenseView view;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        view=new LicenseView(this);
        view.onCreate(state);
    }

    @Override protected void onSaveInstanceState(Bundle state){
        view.onSaveInstanceState(state);
        super.onSaveInstanceState(state);
    }

    @Override public void onWindowFocusChanged(boolean focused){
        super.onWindowFocusChanged(focused);
        view.onWindowFocusChanged(focused);
    }

    @Override protected void onDestroy(){
        view.onDestroy();
        super.onDestroy();
    }
}
