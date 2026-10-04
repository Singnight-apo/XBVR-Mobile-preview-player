package top.liuwei.xbvr;

import android.app.Application;

public final class XbvrApplication extends Application {
    @Override public void onCreate(){super.onCreate();PlaybackDiagnostics.install(this);}
}
