package top.liuwei.xbvr.ui.player;

import java.util.concurrent.Executor;
import top.liuwei.xbvr.domain.FavoriteRepository;
import top.liuwei.xbvr.domain.MediaDetailsRepository;
import top.liuwei.xbvr.domain.Models.Detail;
import top.liuwei.xbvr.domain.PlaybackRepository;
import top.liuwei.xbvr.domain.PlayerPort;
import top.liuwei.xbvr.domain.Projection;
import top.liuwei.xbvr.domain.ResourceIdentity;
import top.liuwei.xbvr.domain.SelectedFormatPolicy;

/**
 * Coordinates the player page's playback decisions and persistence: the source-selection order, the
 * format inference/restore, the save and ticker cadence and the favourites write rules. It owns no
 * Android type and calls no client directly: the surface and the Media3 engine arrive through the
 * injected {@link PlayerPort}, detail/favourite reads through {@link MediaDetailsRepository}, and
 * every visible effect through {@link Listener}, which runs on the main thread.
 *
 * <p>{@code view}, {@code generation} and {@code active} are main-thread managed. Detail loading
 * keeps the old single-threaded executor; results are delivered through the injected main executor.
 */
public final class PlaybackController {
    /** Page callbacks. Every method is invoked on the main thread. */
    public interface Listener {
        /** False while the page is finishing or destroyed; late results are then dropped. */
        boolean alive();

        /** True while the user drags the seek bar; the ticker must not overwrite the drag. */
        boolean isSeeking();

        void title(String value);

        void hint();

        /** Apply the projection to the renderer (without requesting a frame). */
        void settings();

        /** Apply the selected source's image size to the renderer. */
        void videoSize();

        void render();

        void gyroBaseReset();

        void playButton();

        void time(long position, long duration);

        void progress(int value);

        void showControls();

        void scheduleHide();

        void loadFailed(Throwable failure);

        void playbackFailed(String phase, RuntimeException failure, String graphics);

        /** Diagnostics-only failure (no dialog), e.g. a position read while stopping. */
        void engineFailure(String phase, RuntimeException failure);

        void localFavoriteUpdated();

        void serverFavoriteConfirmed();

        void serverFavoriteFailed(Throwable failure);
    }

    private final PlayerPort port;
    private final PlaybackRepository playback;
    private final FavoriteRepository favorites;
    private final MediaDetailsRepository media;
    private final String profileId;
    private final Executor io;
    private final Executor main;
    private final Listener listener;
    private final PlaybackUiState state = new PlaybackUiState();
    private boolean destroyed;
    private int ticks;

    public PlaybackController(
            PlayerPort port,
            PlaybackRepository playback,
            FavoriteRepository favorites,
            MediaDetailsRepository media,
            String profileId,
            Executor io,
            Executor main,
            Listener listener) {
        this.port = port;
        this.playback = playback;
        this.favorites = favorites;
        this.media = media;
        this.profileId = profileId;
        this.io = io;
        this.main = main;
        this.listener = listener;
    }

    public PlaybackUiState state() {
        return state;
    }

    /** Reads the scene detail on the IO executor, then selects the previously used source. */
    public void open(String entryKey, String url) {
        state.entryKey = entryKey;
        io.execute(
                () -> {
                    try {
                        Detail loaded = media.detail(url);
                        main.execute(
                                () -> {
                                    if (!alive()) return;
                                    try {
                                        applyDetail(loaded);
                                    } catch (RuntimeException e) {
                                        playbackFailure("media.select", e);
                                    }
                                });
                    } catch (Exception e) {
                        main.execute(
                                () -> {
                                    if (!alive()) return;
                                    listener.loadFailed(e);
                                });
                    }
                });
    }

    private void applyDetail(Detail loaded) {
        state.detail = loaded;
        listener.title(loaded.title);
        state.selected = 0;
        String previous = playback.selectedSource(state.entryKey);
        for (int i = 0; i < loaded.sources.size(); i++)
            if (ResourceIdentity.of(loaded.sources.get(i).url)
                    .equals(ResourceIdentity.of(previous))) state.selected = i;
        select(state.selected, false);
    }

    /**
     * Switches the active source. Reads the engine position and play intent, saves the old source,
     * then switches, restores the saved view, records the source and only prepares the engine when
     * the page is active.
     */
    public void select(int index, boolean keep) {
        if (state.detail == null) return;
        long position = port.hasEngine() ? port.position() : state.savedPosition;
        boolean playing = port.hasEngine() ? port.playWhenReady() : state.wasPlaying;
        save();
        state.selected = index;
        state.source = state.detail.sources.get(index);
        state.fileKey = ResourceIdentity.playbackKey(profileId, state.source.url);
        state.projection = inferSource();
        state.manual = playback.restore(state.fileKey, state.projection);
        listener.settings();
        listener.videoSize();
        state.hdr = false;
        listener.render();
        state.savedPosition = keep ? position : playback.position(state.fileKey);
        state.wasPlaying = playing;
        playback.selectedSource(state.entryKey, state.source.url);
        state.loaded = true;
        listener.gyroBaseReset();
        listener.hint();
        if (state.active) prepare();
    }

    /** Format inference for the currently selected source. */
    public Projection inferSource() {
        return SelectedFormatPolicy.infer(state.detail, state.source);
    }

    /** Restores the automatic format while keeping the current view and eye. Never rebuilds. */
    public void automatic() {
        if (state.source == null) return;
        Projection inferred = inferSource();
        inferred.eye = state.projection.eye;
        inferred.yaw = state.projection.yaw;
        inferred.pitch = state.projection.pitch;
        inferred.viewFov = state.projection.viewFov;
        state.projection = inferred;
        state.manual = false;
        listener.settings();
        listener.gyroBaseReset();
        listener.hint();
        listener.render();
        save();
    }

    /** Commits a manually chosen projection; the engine keeps running. */
    public void manual(Projection next) {
        state.projection = next;
        state.manual = true;
        listener.settings();
        listener.hint();
        listener.render();
        save();
    }

    /** Applies the packing choice; only the projection and persistence change. */
    public void packing(boolean half) {
        state.projection.halfPacked = half;
        listener.render();
        save();
    }

    /** Switches the viewed eye; only the projection and persistence change. */
    public void eye(int index) {
        state.projection.eye = index;
        listener.hint();
        listener.render();
        save();
    }

    /** Applies validated lens values; only the projection and persistence change. */
    public void lens(float x, float y, float radius, float rotation, boolean mirror) {
        state.projection.centerX = x;
        state.projection.centerY = y;
        state.projection.radius = radius;
        state.projection.rotation = rotation;
        state.projection.mirror = mirror;
        listener.render();
        save();
    }

    /** Recentres the view and resets the gyro baseline; never rebuilds the engine. */
    public void resetView() {
        state.projection.yaw = state.projection.pitch = 0;
        state.projection.viewFov = 75;
        listener.gyroBaseReset();
        listener.render();
        save();
    }

    /** Writes the current file view/position and the scene entry position together. */
    public void save() {
        if (state.fileKey == null || !state.loaded) return;
        long position = port.hasEngine() ? port.position() : state.savedPosition;
        playback.save(state.fileKey, position, state.projection, state.manual);
        playback.entryPosition(state.entryKey, position);
    }

    /** One ticker second: refresh time/progress and save every fifth tick. */
    public void tick() {
        if (!port.hasEngine()) return;
        long duration = Math.max(0, port.duration()), position = port.position();
        listener.playButton();
        if (!listener.isSeeking()) {
            listener.time(position, duration);
            listener.progress(duration > 0 ? (int) (position * 10000 / duration) : 0);
        }
        if (++ticks % 5 == 0) save();
    }

    /** True while the engine should show the pause icon. */
    public boolean playing() {
        return port.hasEngine() && port.playWhenReady() && !port.ended();
    }

    public void togglePlayback() {
        if (!port.hasEngine()) return;
        if (port.playWhenReady() && !port.ended()) port.pause();
        else {
            if (port.ended()) port.seekTo(0);
            port.play();
        }
        save();
        listener.playButton();
        listener.scheduleHide();
    }

    public void jump(long delta) {
        if (!port.hasEngine()) return;
        port.seekTo(Math.max(0, port.position() + delta));
        save();
    }

    public void seekPreview(int progress) {
        if (port.hasEngine() && port.duration() > 0)
            listener.time(port.duration() * progress / 10000, port.duration());
    }

    public void seekFinished(int progress) {
        if (port.hasEngine() && port.duration() > 0)
            port.seekTo(port.duration() * progress / 10000);
        save();
    }

    public void restart() {
        if (!port.hasEngine()) return;
        port.seekTo(0);
        save();
    }

    public void chapter(int index) {
        if (!port.hasEngine() || state.detail == null || index < 0 || index >= state.detail.tags.size())
            return;
        port.seekTo(state.detail.tags.get(index).time);
        save();
    }

    public void speed(float value) {
        port.speed(value);
    }

    public boolean localFavorite() {
        return favorites.favorite(state.entryKey);
    }

    /** True when the loaded detail permits a server favourite write. */
    public boolean serverFavoriteAvailable() {
        return state.detail != null && state.detail.writeFavorite;
    }

    public boolean serverFavoriteValue() {
        return state.detail != null && state.detail.favorite;
    }

    public void toggleLocalFavorite() {
        if (state.entryKey == null) return;
        favorites.favorite(state.entryKey, !favorites.favorite(state.entryKey));
        listener.localFavoriteUpdated();
    }

    /**
     * Writes the server favourite on the IO executor. Without write permission no request is made
     * at all; with permission the confirmation only fires once the repository has returned.
     */
    public void serverFavorite(boolean value) {
        if (state.detail == null || !state.detail.writeFavorite) return;
        final Detail target = state.detail;
        io.execute(
                () -> {
                    try {
                        media.favorite(target, value);
                        main.execute(
                                () -> {
                                    if (alive()) listener.serverFavoriteConfirmed();
                                });
                    } catch (Exception e) {
                        main.execute(
                                () -> {
                                    if (alive()) listener.serverFavoriteFailed(e);
                                });
                    }
                });
    }

    /** Updates the HDR flag from the engine's selected video track and refreshes the hint. */
    public void hdr(boolean value) {
        state.hdr = value;
        listener.hint();
    }

    /** Page became active: reset the gyro baseline and remember that playback is allowed. */
    public void start() {
        state.active = true;
        listener.gyroBaseReset();
    }

    /** Prepares the current source after the page resumed; no-op until a source was loaded. */
    public void prepareIfLoaded() {
        if (state.loaded) prepare();
    }

    /** Page is stopping: save, remember the play intent/position, then release the engine. */
    public void stop() {
        state.active = false;
        save();
        if (!port.hasEngine()) return;
        state.wasPlaying = port.playWhenReady();
        state.savedPosition = port.position();
        port.stop();
    }

    /** Stops and reports a playback failure; the engine is released first. */
    public void playbackFailure(String phase, RuntimeException failure) {
        playbackFailure(phase, failure, "");
    }

    public void playbackFailure(String phase, RuntimeException failure, String graphics) {
        state.wasPlaying = false;
        state.failurePhase = phase;
        state.failure = failure;
        stopEngine();
        listener.playbackFailed(phase, failure, graphics);
    }

    /** Drops any later result from the detail or favourite executors. */
    public void destroy() {
        destroyed = true;
    }

    private void prepare() {
        if (state.source == null || state.rendererFailed) return;
        try {
            port.prepare(
                    state.source,
                    state.detail == null ? java.util.List.of() : state.detail.subtitles,
                    state.savedPosition,
                    state.wasPlaying);
        } catch (RuntimeException e) {
            playbackFailure("player.initialize", e);
        }
    }

    private void stopEngine() {
        if (!port.hasEngine()) return;
        try {
            state.savedPosition = port.position();
        } catch (RuntimeException e) {
            listener.engineFailure("player.position", e);
        }
        port.stop();
    }

    private boolean alive() {
        return !destroyed && listener.alive();
    }
}
