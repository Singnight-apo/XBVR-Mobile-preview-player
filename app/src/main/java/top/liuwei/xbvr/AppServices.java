package top.liuwei.xbvr;

import android.content.Context;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import okhttp3.OkHttpClient;
import top.liuwei.xbvr.domain.DiagnosticsSink;

/**
 * Temporary manual composition entry. T18 exposes only the media data source factory and the
 * diagnostics adapter over the existing {@link PlaybackDiagnostics}; the page keeps its own
 * lifecycle wiring and T22 finishes the remaining dependency assembly here.
 *
 * <p>The factory is built from the already authenticated, T10-hardened client that the page
 * received, so the media stream keeps the same interceptors and CookieJar and never falls back to
 * a fresh default client.
 */
public final class AppServices {
    private final Context context;

    public AppServices(Context context) {
        this.context = context.getApplicationContext();
    }

    /** The old recorder behind the Domain port; T21 swaps the implementation for DiagnosticsStore. */
    public DiagnosticsSink diagnostics() {
        return (phase, failure, graphics) ->
                PlaybackDiagnostics.record(context, phase, failure, graphics);
    }

    /** Media3 data source over the page's existing client; the same interceptor/CookieJar chain. */
    public DataSource.Factory mediaDataSourceFactory(OkHttpClient client) {
        return new OkHttpDataSource.Factory(client);
    }
}
