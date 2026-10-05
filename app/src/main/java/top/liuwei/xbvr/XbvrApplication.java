package top.liuwei.xbvr;

import android.app.Application;
import top.liuwei.xbvr.data.DiagnosticsStore;

public final class XbvrApplication extends Application {
    @Override public void onCreate(){super.onCreate();DiagnosticsStore.install(this);}
}
