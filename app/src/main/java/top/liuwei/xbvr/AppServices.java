package top.liuwei.xbvr;

import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.SensorManager;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import java.util.concurrent.Executor;
import top.liuwei.xbvr.data.BitmapCoverRepository;
import top.liuwei.xbvr.data.DefaultLibraryRepository;
import top.liuwei.xbvr.data.DefaultMediaDetailsRepository;
import top.liuwei.xbvr.data.DiagnosticsStore;
import top.liuwei.xbvr.data.FavoriteStore;
import top.liuwei.xbvr.data.LibraryCache;
import top.liuwei.xbvr.data.LocalSettings;
import top.liuwei.xbvr.data.PlaybackStore;
import top.liuwei.xbvr.data.ProfileStore;
import top.liuwei.xbvr.data.XbvrApi;
import top.liuwei.xbvr.data.XbvrProtocol;
import top.liuwei.xbvr.domain.CoverRepository;
import top.liuwei.xbvr.domain.CoverSettings;
import top.liuwei.xbvr.domain.DiagnosticsSink;
import top.liuwei.xbvr.domain.FavoriteRepository;
import top.liuwei.xbvr.domain.LibraryRepository;
import top.liuwei.xbvr.domain.MediaDetailsRepository;
import top.liuwei.xbvr.domain.PlaybackRepository;
import top.liuwei.xbvr.domain.ProfileRepository;
import top.liuwei.xbvr.domain.ServerProfile;
import top.liuwei.xbvr.media.GyroController;
import top.liuwei.xbvr.media.Media3PlaybackSession;

/**
 * The single manual composition entry. It builds the local stores, the diagnostics sink and the
 * gyro controller over the application Context, and hands each page the domain interfaces it needs.
 * Storage, HTTP, JSON and the media engine are constructed here only; the pages never name a Data
 * implementation.
 *
 * <p>Everything page-lifecycle-owned stays with the page: the page creates its own executors, its
 * cover repository and its media session through {@link ForProfile}, and closes them itself. Nothing
 * here is a static global, so a destroyed page cannot leak into the next one.
 */
public final class AppServices {
    private final Context context;
    private final ProfileStore profiles;
    private final LocalSettings settings;
    private final PlaybackStore playback;
    private final FavoriteStore favorites;
    private final LibraryCache cache;
    private final DiagnosticsStore diagnostics;

    public AppServices(Context context) {
        this.context = context.getApplicationContext();
        this.profiles = new ProfileStore(this.context);
        this.settings = new LocalSettings(this.context);
        this.playback = new PlaybackStore(settings);
        this.favorites = new FavoriteStore(settings);
        this.cache = new LibraryCache(this.context);
        this.diagnostics = new DiagnosticsStore(this.context);
    }

    /** Persisted server configurations, including which one is active. */
    public ProfileRepository profiles() {
        return profiles;
    }

    /** Resume positions, saved view state and the selected source per scene. */
    public PlaybackRepository playback() {
        return playback;
    }

    /** Locally stored favourites. */
    public FavoriteRepository favorites() {
        return favorites;
    }

    /** Per-server cover settings. */
    public CoverSettings coverSettings() {
        return settings;
    }

    /** The Domain recorder backed by the Data diagnostics store. */
    public DiagnosticsSink diagnostics() {
        return (phase, failure, graphics) -> diagnostics.record(phase, failure, graphics);
    }

    /** The bounded, redacted diagnostics report the page renders in its dialog. */
    public String diagnosticsReport() {
        return diagnostics.report();
    }

    /** Server address normalisation; the data-layer protocol owns the rule. */
    public String normalizeBase(String value) {
        return XbvrProtocol.base(value);
    }

    /** The gyro controller over the application sensor service; the page supplies its sink. */
    public GyroController gyro(GyroController.Sink sink) {
        return new GyroController(
                (SensorManager) context.getSystemService(Context.SENSOR_SERVICE), sink);
    }

    /** Binds one stored server profile to its authenticated client and its per-page repositories. */
    public ForProfile forProfile(ServerProfile value) {
        return new ForProfile(value);
    }

    /**
     * The client and repositories scoped to one active server. One {@link XbvrApi} instance keeps a
     * single CookieJar and Basic chain shared by the JSON calls, the cover requests and the media
     * data source, exactly as the old single client did.
     */
    public final class ForProfile {
        private final XbvrApi api;

        private ForProfile(ServerProfile value) {
            this.api = new XbvrApi(value);
        }

        public String id() {
            return api.id;
        }

        public String base() {
            return api.base;
        }

        /** Media3 data source over the authenticated client: same interceptors and CookieJar. */
        public DataSource.Factory mediaDataSourceFactory() {
            return new OkHttpDataSource.Factory(api.client);
        }

        /** Three-phase library loader on the page's executor and main poster. */
        public LibraryRepository library(Executor io, Executor main) {
            return new DefaultLibraryRepository(api, cache, io, main);
        }

        /** A fresh per-server cover repository; the page drops the previous server's cache. */
        public CoverRepository<Bitmap> covers(Executor io, Executor main) {
            return new BitmapCoverRepository(api.client, io, main);
        }

        /** Detail and favourite reads for the player page. */
        public MediaDetailsRepository details() {
            return new DefaultMediaDetailsRepository(api);
        }

        /** The page's one media engine, built over the authenticated media data source. */
        public Media3PlaybackSession mediaSession(
                Context host, Media3PlaybackSession.Listener listener) {
            return new Media3PlaybackSession(
                    host, mediaDataSourceFactory(), diagnostics(), listener);
        }
    }
}
