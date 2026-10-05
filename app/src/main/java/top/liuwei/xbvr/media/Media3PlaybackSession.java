package top.liuwei.xbvr.media;

import android.content.Context;
import android.net.Uri;
import android.view.Surface;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import top.liuwei.xbvr.domain.DiagnosticsSink;
import top.liuwei.xbvr.domain.Models.Source;
import top.liuwei.xbvr.domain.Models.Subtitle;
import top.liuwei.xbvr.domain.PlayerPort;
import top.liuwei.xbvr.domain.TrackOption;

/**
 * The page's one Media3 engine and the surface bound to it. It is the only {@link PlayerPort} the
 * controller sees, it builds every MediaItem/subtitle configuration the same way the old activity
 * did, and it owns exactly one player: {@link PlaybackLifecycle} holds the only engine field, so
 * preparing a new source first runs the old stop order (position → detach → release) and the page
 * can never end up with two players or two audio streams.
 *
 * <p>Only the Android main thread touches this class. The Media3-specific surface and listener
 * members stay here; {@link PlayerPort} and Domain stay free of Surface, ExoPlayer and Cue types.
 */
@OptIn(markerClass = UnstableApi.class)
public final class Media3PlaybackSession implements PlayerPort {
    /**
     * Media3-specific callbacks. They carry the original Media3 types (Cue, PlaybackException,
     * playback state) because the owning page is the only consumer; nothing here reaches Domain.
     */
    public interface Listener {
        void videoSize(int width, int height, float pixelWidthHeightRatio);

        void cues(List<Cue> cues);

        void playbackError(PlaybackException failure);

        void isPlayingChanged(boolean playing);

        void playbackStateChanged(int playbackState);

        void hdrChanged(boolean value);

        /** The engine was released; the page refreshes its transport button. */
        void stopped();
    }

    private final Context context;
    private final DataSource.Factory dataSourceFactory;
    private final DiagnosticsSink diagnostics;
    private final Listener listener;
    private final List<TrackRef> trackRefs = new ArrayList<>();
    private PlaybackLifecycle lifecycle;
    private Surface surface;

    public Media3PlaybackSession(
            Context context,
            DataSource.Factory dataSourceFactory,
            DiagnosticsSink diagnostics,
            Listener listener) {
        this.context = context;
        this.dataSourceFactory = dataSourceFactory;
        this.diagnostics = diagnostics;
        this.listener = listener;
        this.lifecycle = new PlaybackLifecycle(null, diagnostics, 0);
    }

    /** Stores the decoder surface and binds it to a live engine; safe before the engine exists. */
    public void attachSurface(Surface value) {
        surface = value;
        Media3Backend backend = engine();
        if (backend == null || value == null || !value.isValid()) return;
        backend.player.setVideoSurface(value);
    }

    /** Drops the surface; used when the renderer fails and the page no longer owns a decoder. */
    public void clearSurface() {
        surface = null;
        Media3Backend backend = engine();
        if (backend == null) return;
        try {
            backend.player.clearVideoSurface();
        } catch (RuntimeException e) {
            diagnostics.record("player.detach", e, "");
        }
    }

    /** Media3-specific: current track groups for the page's track menu (its labels stay in UI). */
    public Tracks currentTracks() {
        Media3Backend backend = engine();
        return backend == null ? Tracks.EMPTY : backend.player.getCurrentTracks();
    }

    /** Media3-specific: the page's per-track override, identical to the old menu action. */
    public void overrideTrack(int trackType, TrackGroup group, int index) {
        Media3Backend backend = engine();
        if (backend == null) return;
        backend.player.setTrackSelectionParameters(
                backend.player
                        .getTrackSelectionParameters()
                        .buildUpon()
                        .setTrackTypeDisabled(trackType, false)
                        .setOverrideForType(new TrackSelectionOverride(group, List.of(index)))
                        .build());
    }

    @Override
    public void prepare(Source source, List<Subtitle> subtitles, long position, boolean play) {
        if (source == null) return;
        // The policy releases the previous engine before the replacement is built.
        lifecycle.stop();
        ExoPlayer player =
                new ExoPlayer.Builder(context)
                        .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
                        .build();
        Media3Backend backend = new Media3Backend(player);
        lifecycle = new PlaybackLifecycle(backend, diagnostics, position);
        player.addListener(
                new Player.Listener() {
                    @Override
                    public void onVideoSizeChanged(VideoSize size) {
                        listener.videoSize(size.width, size.height, size.pixelWidthHeightRatio);
                    }

                    @Override
                    public void onCues(CueGroup cues) {
                        listener.cues(cues.cues);
                    }

                    @Override
                    public void onPlayerError(PlaybackException failure) {
                        listener.playbackError(failure);
                    }

                    @Override
                    public void onIsPlayingChanged(boolean playing) {
                        listener.isPlayingChanged(playing);
                    }

                    @Override
                    public void onPlaybackStateChanged(int playbackState) {
                        listener.playbackStateChanged(playbackState);
                    }

                    @Override
                    public void onTracksChanged(Tracks tracks) {
                        boolean value = false;
                        for (Tracks.Group g : tracks.getGroups())
                            if (g.getType() == C.TRACK_TYPE_VIDEO)
                                for (int i = 0; i < g.length; i++)
                                    if (g.isTrackSelected(i)) {
                                        Format f = g.getTrackFormat(i);
                                        if (f.colorInfo != null
                                                && (f.colorInfo.colorTransfer
                                                                == C.COLOR_TRANSFER_ST2084
                                                        || f.colorInfo.colorTransfer
                                                                == C.COLOR_TRANSFER_HLG))
                                            value = true;
                                    }
                        listener.hdrChanged(value);
                    }
                });
        MediaItem.Builder item = new MediaItem.Builder().setUri(source.url);
        List<MediaItem.SubtitleConfiguration> subs = new ArrayList<>();
        for (Subtitle s : subtitles) {
            String path = (s.name + " " + s.url).toLowerCase(Locale.ROOT);
            String mime = path.contains(".vtt") ? MimeTypes.TEXT_VTT : MimeTypes.APPLICATION_SUBRIP;
            subs.add(
                    new MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url))
                            .setMimeType(mime)
                            .setLanguage(s.language)
                            .setLabel(s.name)
                            .build());
        }
        item.setSubtitleConfigurations(subs);
        listener.cues(Collections.emptyList());
        player.setMediaItem(item.build());
        if (surface != null && surface.isValid()) player.setVideoSurface(surface);
        player.seekTo(position);
        player.setPlayWhenReady(play);
        player.prepare();
    }

    @Override
    public boolean hasEngine() {
        return lifecycle.hasEngine();
    }

    @Override
    public long position() {
        Media3Backend backend = engine();
        return backend == null ? lifecycle.savedPosition() : backend.position();
    }

    @Override
    public long duration() {
        Media3Backend backend = engine();
        return backend == null ? 0 : backend.player.getDuration();
    }

    @Override
    public boolean playWhenReady() {
        Media3Backend backend = engine();
        return backend != null && backend.player.getPlayWhenReady();
    }

    @Override
    public boolean isPlaying() {
        Media3Backend backend = engine();
        return backend != null && backend.player.isPlaying();
    }

    @Override
    public boolean ended() {
        Media3Backend backend = engine();
        return backend != null && backend.player.getPlaybackState() == Player.STATE_ENDED;
    }

    @Override
    public void play() {
        Media3Backend backend = engine();
        if (backend != null) backend.player.play();
    }

    @Override
    public void pause() {
        Media3Backend backend = engine();
        if (backend != null) backend.player.pause();
    }

    @Override
    public void seekTo(long position) {
        Media3Backend backend = engine();
        if (backend != null) backend.player.seekTo(position);
    }

    @Override
    public void speed(float value) {
        Media3Backend backend = engine();
        if (backend != null) backend.player.setPlaybackSpeed(value);
    }

    @Override
    public List<TrackOption> tracks() {
        trackRefs.clear();
        List<TrackOption> options = new ArrayList<>();
        Media3Backend backend = engine();
        if (backend == null) return options;
        for (Tracks.Group g : backend.player.getCurrentTracks().getGroups()) {
            boolean audio = g.getType() == C.TRACK_TYPE_AUDIO;
            if (!audio && g.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < g.length; i++) {
                Format f = g.getTrackFormat(i);
                options.add(
                        new TrackOption(
                                "t" + trackRefs.size(),
                                audio ? TrackOption.Kind.AUDIO : TrackOption.Kind.TEXT,
                                f.label != null ? f.label : f.language,
                                f.sampleMimeType != null ? f.sampleMimeType : f.containerMimeType,
                                g.isTrackSupported(i),
                                g.isTrackSelected(i)));
                trackRefs.add(new TrackRef(g, i));
            }
        }
        return options;
    }

    @Override
    public void audioAuto() {
        Media3Backend backend = engine();
        if (backend == null) return;
        backend.player.setTrackSelectionParameters(
                backend.player
                        .getTrackSelectionParameters()
                        .buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        .build());
    }

    @Override
    public void subtitlesOff() {
        Media3Backend backend = engine();
        if (backend == null) return;
        backend.player.setTrackSelectionParameters(
                backend.player
                        .getTrackSelectionParameters()
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build());
    }

    @Override
    public void selectTrack(String token) {
        Media3Backend backend = engine();
        if (backend == null || token == null) return;
        int index;
        try {
            index = Integer.parseInt(token.substring(1));
        } catch (RuntimeException ignored) {
            return;
        }
        if (index < 0 || index >= trackRefs.size()) return;
        TrackRef ref = trackRefs.get(index);
        overrideTrack(ref.group.getType(), ref.group.getMediaTrackGroup(), ref.index);
    }

    @Override
    public void stop() {
        if (!lifecycle.hasEngine()) return;
        lifecycle.stop();
        trackRefs.clear();
        listener.stopped();
    }

    private Media3Backend engine() {
        PlaybackLifecycle.Backend backend = lifecycle.backend();
        return backend instanceof Media3Backend ? (Media3Backend) backend : null;
    }

    /** The three teardown operations the policy owns; the only place a player is released. */
    private static final class Media3Backend implements PlaybackLifecycle.Backend {
        private final ExoPlayer player;

        Media3Backend(ExoPlayer player) {
            this.player = player;
        }

        @Override
        public long position() {
            return player.getCurrentPosition();
        }

        @Override
        public void detach() {
            player.clearVideoSurface();
        }

        @Override
        public void release() {
            player.release();
        }
    }

    private static final class TrackRef {
        final Tracks.Group group;
        final int index;

        TrackRef(Tracks.Group group, int index) {
            this.group = group;
            this.index = index;
        }
    }
}
