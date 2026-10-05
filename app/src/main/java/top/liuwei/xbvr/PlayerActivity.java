package top.liuwei.xbvr;
import top.liuwei.xbvr.data.DefaultMediaDetailsRepository;
import top.liuwei.xbvr.data.ProfileJsonMapper;
import top.liuwei.xbvr.domain.PlayerPort;
import top.liuwei.xbvr.domain.Projection;
import top.liuwei.xbvr.domain.ResourceIdentity;
import top.liuwei.xbvr.domain.ServerProfile;
import top.liuwei.xbvr.domain.TrackOption;
import top.liuwei.xbvr.ui.player.PlaybackController;
import top.liuwei.xbvr.ui.player.PlaybackUiState;
import top.liuwei.xbvr.ui.player.PlayerDialogs;
import top.liuwei.xbvr.ui.player.PlayerView;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.view.*;
import android.widget.*;
import android.hardware.*;
import android.content.res.Configuration;
import androidx.media3.common.*;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.*;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import java.util.*;
import java.util.concurrent.*;
import static top.liuwei.xbvr.domain.Models.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlayerActivity extends Activity
        implements SensorEventListener, PlayerView.Actions, PlayerDialogs.Menu {
    private static final String STATE_PLAY_WHEN_READY = "player.playWhenReady";
    private Store store;
    private Api api;
    private ExoPlayer player;
    private Surface decoderSurface;
    private VrView vr;
    private PlayerView view;
    private PlayerDialogs dialogs;
    private boolean gyro, gyroBase;
    private SensorManager sensors;
    private Sensor rotationSensor;
    private float baseYaw, basePitch;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Engine engine = new Engine();
    private final Page page = new Page();
    private PlaybackController controller;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        boolean restoredPlaying = state == null || state.getBoolean(STATE_PLAY_WHEN_READY, true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        store = new Store(this);
        String url = getIntent().getStringExtra("url");
        view = new PlayerView(this, this);
        dialogs = new PlayerDialogs(this, view);
        try {
            ServerProfile profile = store.serverProfile(getIntent().getStringExtra("profile"));
            if (profile != null) api = new Api(ProfileJsonMapper.toJson(profile));
            if (api == null) throw new IllegalStateException(tr(R.string.player_missing_profile));
        } catch (Exception e) {
            error(e);
            finish();
            return;
        }
        controller =
                new PlaybackController(
                        engine,
                        store,
                        store,
                        new DefaultMediaDetailsRepository(api),
                        api.id,
                        io,
                        r -> main.post(r),
                        page);
        controller.state().wasPlaying = restoredPlaying;
        try {
            String entryKey = store.playbackKey(api.id, url);
            build();
            sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
            if (sensors != null) {
                rotationSensor = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
                if (rotationSensor == null)
                    rotationSensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            }
            controller.open(entryKey, url);
        } catch (RuntimeException e) {
            controller.playbackFailure("player.create", e);
        }
    }

    private String tr(int id, Object... args) {
        return getString(id, args);
    }

    private void build() {
        Ui.edgeToEdge(this, true);
        vr =
                new VrView(
                        this,
                        s -> {
                            if (isDestroyed() || isFinishing() || controller.state().rendererFailed)
                                return;
                            decoderSurface = s;
                            if (player != null && controller.state().active)
                                try {
                                    player.setVideoSurface(s);
                                } catch (RuntimeException e) {
                                    controller.playbackFailure("player.surface", e);
                                }
                        });
        vr.onFailure(
                (phase, failure, diagnostic) -> {
                    if (isFinishing() || isDestroyed() || controller.state().rendererFailed) return;
                    controller.state().rendererFailed = true;
                    controller.playbackFailure(phase, failure, diagnostic);
                    try {
                        vr.release();
                    } catch (RuntimeException cleanup) {
                        failure.addSuppressed(cleanup);
                        PlaybackDiagnostics.record(this, phase, failure, diagnostic);
                    }
                    decoderSurface = null;
                });
        vr.setContentDescription(tr(R.string.player_surface_description));
        vr.onTap(view::toggleControls);
        vr.onDoubleTap(
                () -> {
                    jump(-10000);
                    view.showControls(true);
                },
                () -> {
                    jump(10000);
                    view.showControls(true);
                });
        view.build(vr, getIntent().getStringExtra("title"));
    }

    @Override
    public void back() {
        finish();
    }

    @Override
    public void rotate() {
        setRequestedOrientation(
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                        ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    @Override
    public void togglePlayback() {
        controller.togglePlayback();
    }

    private String eyeLabel() {
        Projection projection = controller.state().projection;
        return projection.layout == Projection.MONO
                ? tr(R.string.player_mono_eye)
                : projection.layout == Projection.SBS
                        ? (projection.eye == 0
                                ? tr(R.string.player_left_eye)
                                : tr(R.string.player_right_eye))
                        : (projection.eye == 0
                                ? tr(R.string.player_top_eye)
                                : tr(R.string.player_bottom_eye));
    }

    private void viewingEye() {
        Projection projection = controller.state().projection;
        if (projection.layout == Projection.MONO) {
            Toast.makeText(this, tr(R.string.player_mono_warning), Toast.LENGTH_SHORT).show();
            return;
        }
        String[] choices =
                projection.layout == Projection.SBS
                        ? new String[] {tr(R.string.player_left_eye), tr(R.string.player_right_eye)}
                        : new String[] {
                            tr(R.string.player_top_eye), tr(R.string.player_bottom_eye)
                        };
        dialogs.viewingEye(
                tr(R.string.player_viewing_eye),
                choices,
                projection.eye,
                index -> controller.eye(index));
    }

    @Override
    public void resetView() {
        controller.resetView();
    }

    private void updatePlayButton() {
        view.updatePlayButton(controller.playing());
    }

    private void updateHint() {
        if (controller == null || view == null) return;
        PlaybackUiState s = controller.state();
        String status =
                Ui.projectionLabel(this, s.projection)
                        + " · "
                        + eyeLabel()
                        + " · "
                        + Ui.projectionReason(this, s.projection)
                        + " · "
                        + tr(gyro ? R.string.player_gyro_hint : R.string.player_touch_hint)
                        + (s.hdr ? " · " + tr(R.string.player_hdr_hint) : "");
        view.setStatus(status, gyro);
    }

    private void error(Throwable failure) {
        if (isFinishing() || isDestroyed()) return;
        view.error(failure);
    }

    @Override
    public boolean hasPlayer() {
        return engine.hasEngine();
    }

    @Override
    public boolean isPlaying() {
        return engine.isPlaying();
    }

    @Override
    public void seekPreview(int progress) {
        controller.seekPreview(progress);
    }

    @Override
    public void seekFinished(int progress) {
        controller.seekFinished(progress);
    }

    @Override
    public void more() {
        dialogs.more(this);
    }

    @Override
    public void restart() {
        controller.restart();
    }

    private void automatic() {
        controller.automatic();
    }

    private void startPlayer() {
        controller.prepareIfLoaded();
    }

    private void createPlayer(
            Source source, List<Subtitle> subtitles, long position, boolean play) {
        if (source == null) return;
        if (player != null) {
            player.release();
            player = null;
        }
        player =
                new ExoPlayer.Builder(this)
                        .setMediaSourceFactory(
                                new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                                        new OkHttpDataSource.Factory(api.client)))
                        .build();
        player.addListener(
                new Player.Listener() {
                    @Override
                    public void onVideoSizeChanged(VideoSize size) {
                        vr.videoWidth = Math.max(1, size.width);
                        vr.videoHeight = Math.max(1, size.height);
                        vr.videoPixelAspect = size.pixelWidthHeightRatio;
                        vr.requestRender();
                    }

                    @Override
                    public void onCues(CueGroup cues) {
                        view.setCues(cues.cues);
                    }

                    @Override
                    public void onPlayerError(PlaybackException e) {
                        PlaybackDiagnostics.record(
                                PlayerActivity.this, "media." + e.getErrorCodeName(), e, "");
                        view.setHint(tr(R.string.player_media_failed, e.getErrorCodeName()));
                        view.showControls(true);
                    }

                    @Override
                    public void onIsPlayingChanged(boolean playing) {
                        updatePlayButton();
                        if (playing) view.scheduleHide();
                        else view.showControls(true);
                    }

                    @Override
                    public void onPlaybackStateChanged(int playbackState) {
                        updatePlayButton();
                        if (playbackState == Player.STATE_ENDED) view.showControls(true);
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
                        controller.hdr(value);
                    }
                });
        MediaItem.Builder item = new MediaItem.Builder().setUri(source.url);
        List<MediaItem.SubtitleConfiguration> subs = new ArrayList<>();
        for (Subtitle s : subtitles) {
            String path = (s.name + " " + s.url).toLowerCase(Locale.ROOT);
            String mime = path.contains(".vtt") ? MimeTypes.TEXT_VTT : MimeTypes.APPLICATION_SUBRIP;
            subs.add(
                    new MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(s.url))
                            .setMimeType(mime)
                            .setLanguage(s.language)
                            .setLabel(s.name)
                            .build());
        }
        item.setSubtitleConfigurations(subs);
        view.setCues(Collections.emptyList());
        player.setMediaItem(item.build());
        if (decoderSurface != null && decoderSurface.isValid())
            player.setVideoSurface(decoderSurface);
        player.seekTo(position);
        player.setPlayWhenReady(play);
        player.prepare();
    }

    @Override
    public void jump(long delta) {
        controller.jump(delta);
    }

    private final Runnable ticker =
            new Runnable() {
                public void run() {
                    if (controller == null) return;
                    controller.tick();
                    if (controller.state().active) main.postDelayed(this, 1000);
                }
            };

    @Override
    public void formats() {
        if (!controller.state().loaded) return;
        PlaybackUiState s = controller.state();
        List<String> items =
                new ArrayList<>(
                        List.of(
                                tr(R.string.player_projection_layout),
                                tr(R.string.player_eye_option, eyeLabel())));
        if (s.projection.kind == Projection.FLAT && s.projection.layout != Projection.MONO)
            items.add(tr(R.string.player_packing_option));
        dialogs.formats(
                tr(R.string.player_format_title, Ui.projectionLabel(this, s.projection)),
                items.toArray(new String[0]),
                index -> {
                    if (index == 0) projectionFormats();
                    else if (index == 1) viewingEye();
                    else packing();
                });
    }

    private void projectionFormats() {
        String[] modes = {
            tr(R.string.player_flat),
            tr(R.string.player_panorama180),
            tr(R.string.player_panorama360),
            tr(R.string.player_fisheye180),
            tr(R.string.player_fisheye190),
            tr(R.string.player_fisheye200),
            tr(R.string.player_fisheye220),
            tr(R.string.player_automatic)
        };
        if (!controller.state().loaded) return;
        Projection projection = controller.state().projection;
        dialogs.projectionFormats(
                tr(R.string.player_projection_title, Ui.projectionLabel(this, projection)),
                modes,
                index -> {
                    if (index == 7) {
                        automatic();
                        return;
                    }
                    Projection next = controller.inferSource();
                    next.kind =
                            index == 0
                                    ? Projection.FLAT
                                    : index <= 2 ? Projection.EQUIRECT : Projection.FISHEYE;
                    next.capture =
                            index == 2
                                    ? 360
                                    : index == 4
                                            ? 190
                                            : index == 5 ? 200 : index == 6 ? 220 : 180;
                    next.eye = projection.eye;
                    next.yaw = projection.yaw;
                    next.pitch = projection.pitch;
                    next.viewFov = projection.viewFov;
                    next.layout = projection.layout;
                    next.centerX = projection.centerX;
                    next.centerY = projection.centerY;
                    next.radius = projection.radius;
                    next.rotation = projection.rotation;
                    next.mirror = projection.mirror;
                    next.known = true;
                    next.reason = "手动设置" + (index == 4 ? "；未作 RF52 专用标定" : "");
                    dialogs.layout(
                            tr(R.string.player_layout),
                            new String[] {
                                tr(R.string.player_layout_mono),
                                tr(R.string.player_layout_sbs),
                                tr(R.string.player_layout_tb)
                            },
                            projection.layout,
                            layout -> {
                                next.layout = layout;
                                next.halfPacked =
                                        next.kind == Projection.FLAT && layout != Projection.MONO;
                                controller.manual(next);
                                if (next.halfPacked) packing();
                            });
                });
    }

    private void packing() {
        Projection projection = controller.state().projection;
        dialogs.packing(
                tr(R.string.player_packing),
                new String[] {
                    tr(R.string.player_packing_half), tr(R.string.player_packing_full)
                },
                projection.halfPacked ? 0 : 1,
                p -> controller.packing(p == 0));
    }

    @Override
    public void files() {
        Detail detail = controller.state().detail;
        if (detail == null) return;
        String[] names =
                detail.sources.stream()
                        .map(s -> s.name + (s.filename.isBlank() ? "" : "\n" + s.filename))
                        .toArray(String[]::new);
        dialogs.files(
                tr(R.string.player_choose_file),
                names,
                controller.state().selected,
                i -> {
                    if (i != controller.state().selected) controller.select(i, true);
                });
    }

    @Override
    public void chapters() {
        Detail detail = controller.state().detail;
        if (detail == null) return;
        if (detail.tags.isEmpty()) {
            Toast.makeText(this, tr(R.string.player_no_chapters), Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels =
                detail.tags.stream()
                        .map(t -> Ui.time(t.time) + "  " + t.name)
                        .toArray(String[]::new);
        dialogs.chapters(tr(R.string.player_chapters), labels, i -> controller.chapter(i));
    }

    @Override
    public void speed() {
        float[] rates = {.5f, .75f, 1, 1.25f, 1.5f, 2};
        dialogs.speed(
                tr(R.string.player_speed),
                new String[] {"0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×"},
                i -> controller.speed(rates[i]));
    }

    @Override
    public void tracks() {
        if (player == null) return;
        List<String> labels =
                new ArrayList<>(
                        List.of(tr(R.string.player_audio_auto), tr(R.string.player_subtitles_off)));
        List<Runnable> actions = new ArrayList<>();
        actions.add(() -> engine.audioAuto());
        actions.add(() -> engine.subtitlesOff());
        for (Tracks.Group g : player.getCurrentTracks().getGroups())
            if (g.getType() == C.TRACK_TYPE_AUDIO || g.getType() == C.TRACK_TYPE_TEXT) {
                for (int i = 0; i < g.length; i++) {
                    if (!g.isTrackSupported(i)) continue;
                    Format f = g.getTrackFormat(i);
                    int track = i;
                    labels.add(
                            tr(
                                    g.getType() == C.TRACK_TYPE_AUDIO
                                            ? R.string.player_audio_track
                                            : R.string.player_subtitle_track,
                                    f.label != null
                                            ? f.label
                                            : f.language != null
                                                    ? f.language
                                                    : tr(R.string.player_unknown_track),
                                    i + 1));
                    actions.add(
                            () ->
                                    player.setTrackSelectionParameters(
                                            player.getTrackSelectionParameters()
                                                    .buildUpon()
                                                    .setTrackTypeDisabled(g.getType(), false)
                                                    .setOverrideForType(
                                                            new TrackSelectionOverride(
                                                                    g.getMediaTrackGroup(),
                                                                    List.of(track)))
                                                    .build()));
                }
            }
        dialogs.tracks(tr(R.string.player_tracks), labels.toArray(new String[0]), actions);
    }

    @Override
    public void lens() {
        if (controller.state().projection.kind == Projection.FLAT) {
            packing();
            return;
        }
        dialogs.lens(
                tr(R.string.player_lens),
                controller.state().projection,
                (x, y, radius, rotation, mirror) ->
                        controller.lens(x, y, radius, rotation, mirror));
    }

    @Override
    public void favorites() {
        if (controller.state().detail == null) return;
        List<String> items = new ArrayList<>();
        items.add(
                controller.localFavorite()
                        ? tr(R.string.player_local_unfavorite)
                        : tr(R.string.player_local_favorite));
        if (controller.serverFavoriteAvailable())
            items.add(
                    controller.serverFavoriteValue()
                            ? tr(R.string.player_server_unfavorite)
                            : tr(R.string.player_server_favorite));
        dialogs.favorites(
                tr(R.string.player_favorites),
                items.toArray(new String[0]),
                i -> {
                    if (i == 0) controller.toggleLocalFavorite();
                    else controller.serverFavorite(!controller.serverFavoriteValue());
                });
    }

    @Override
    public void toggleGyro() {
        if (rotationSensor == null) {
            Toast.makeText(this, tr(R.string.player_no_sensor), Toast.LENGTH_SHORT).show();
            return;
        }
        gyroBase = false;
        if (gyro) {
            sensors.unregisterListener(this);
            gyro = false;
        } else {
            gyro =
                    controller.state().active
                            && sensors.registerListener(
                                    this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
            if (!gyro)
                Toast.makeText(this, tr(R.string.player_sensor_unavailable), Toast.LENGTH_SHORT)
                        .show();
        }
        updateHint();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!gyro || !controller.state().active) return;
        float[] mat = new float[9], remap = new float[9], angles = new float[3];
        SensorManager.getRotationMatrixFromVector(mat, event.values);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int x = SensorManager.AXIS_X, y = SensorManager.AXIS_Z;
        if (rotation == Surface.ROTATION_90) {
            x = SensorManager.AXIS_Z;
            y = SensorManager.AXIS_MINUS_X;
        } else if (rotation == Surface.ROTATION_270) {
            x = SensorManager.AXIS_MINUS_Z;
            y = SensorManager.AXIS_X;
        } else if (rotation == Surface.ROTATION_180) {
            x = SensorManager.AXIS_MINUS_X;
            y = SensorManager.AXIS_MINUS_Z;
        }
        SensorManager.remapCoordinateSystem(mat, x, y, remap);
        SensorManager.getOrientation(remap, angles);
        float yaw = (float) Math.toDegrees(angles[0]), pitch = (float) Math.toDegrees(angles[1]);
        if (gyroBase) {
            Projection projection = controller.state().projection;
            projection.yaw += RenderMath.wrappedDelta(yaw, baseYaw);
            projection.pitch = RenderMath.gyroPitch(projection.pitch, pitch, basePitch);
            vr.requestRender();
        }
        baseYaw = yaw;
        basePitch = pitch;
        gyroBase = true;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) Ui.edgeToEdge(this, true);
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        gyroBase = false;
        Ui.edgeToEdge(this, true);
        view.onConfigurationChanged(c);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean(
                STATE_PLAY_WHEN_READY,
                engine.hasEngine() ? engine.playWhenReady() : controller.state().wasPlaying);
        controller.save();
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (controller == null) return;
        controller.start();
        if (view != null) view.setActive(true);
        if (vr != null) vr.onResume();
        startPlayer();
        main.removeCallbacks(ticker);
        main.post(ticker);
        if (gyro
                && rotationSensor != null
                && !sensors.registerListener(
                        this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)) {
            gyro = false;
            updateHint();
        }
    }

    @Override
    protected void onStop() {
        if (controller != null) controller.stop();
        if (view != null) view.setActive(false);
        main.removeCallbacks(ticker);
        if (view != null) view.cancelHide();
        if (sensors != null) sensors.unregisterListener(this);
        if (vr != null) vr.onPause();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (view != null) {
            view.dismissDialogs();
            view.cancelHide();
        }
        if (controller != null) controller.destroy();
        io.shutdownNow();
        if (vr != null) vr.release();
        super.onDestroy();
    }

    /** Thin PlayerPort adapter over the activity's existing Media3 engine and surface. */
    private final class Engine implements PlayerPort {
        @Override
        public void prepare(Source source, List<Subtitle> subtitles, long position, boolean play) {
            createPlayer(source, subtitles, position, play);
        }

        @Override
        public boolean hasEngine() {
            return player != null;
        }

        @Override
        public long position() {
            return player.getCurrentPosition();
        }

        @Override
        public long duration() {
            return player == null ? 0 : player.getDuration();
        }

        @Override
        public boolean playWhenReady() {
            return player != null && player.getPlayWhenReady();
        }

        @Override
        public boolean isPlaying() {
            return player != null && player.isPlaying();
        }

        @Override
        public boolean ended() {
            return player != null && player.getPlaybackState() == Player.STATE_ENDED;
        }

        @Override
        public void play() {
            if (player != null) player.play();
        }

        @Override
        public void pause() {
            if (player != null) player.pause();
        }

        @Override
        public void seekTo(long position) {
            if (player != null) player.seekTo(position);
        }

        @Override
        public void speed(float value) {
            if (player != null) player.setPlaybackSpeed(value);
        }

        @Override
        public List<TrackOption> tracks() {
            trackRefs.clear();
            List<TrackOption> options = new ArrayList<>();
            if (player == null) return options;
            for (Tracks.Group g : player.getCurrentTracks().getGroups()) {
                boolean audio = g.getType() == C.TRACK_TYPE_AUDIO;
                if (!audio && g.getType() != C.TRACK_TYPE_TEXT) continue;
                for (int i = 0; i < g.length; i++) {
                    Format f = g.getTrackFormat(i);
                    options.add(
                            new TrackOption(
                                    "t" + trackRefs.size(),
                                    audio ? TrackOption.Kind.AUDIO : TrackOption.Kind.TEXT,
                                    f.label != null ? f.label : f.language,
                                    f.sampleMimeType != null
                                            ? f.sampleMimeType
                                            : f.containerMimeType,
                                    g.isTrackSupported(i),
                                    g.isTrackSelected(i)));
                    trackRefs.add(new TrackRef(g, i));
                }
            }
            return options;
        }

        @Override
        public void audioAuto() {
            if (player == null) return;
            player.setTrackSelectionParameters(
                    player.getTrackSelectionParameters()
                            .buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                            .build());
        }

        @Override
        public void subtitlesOff() {
            if (player == null) return;
            player.setTrackSelectionParameters(
                    player.getTrackSelectionParameters()
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build());
        }

        @Override
        public void selectTrack(String token) {
            if (player == null || token == null) return;
            int index;
            try {
                index = Integer.parseInt(token.substring(1));
            } catch (RuntimeException ignored) {
                return;
            }
            if (index < 0 || index >= trackRefs.size()) return;
            TrackRef ref = trackRefs.get(index);
            player.setTrackSelectionParameters(
                    player.getTrackSelectionParameters()
                            .buildUpon()
                            .setTrackTypeDisabled(ref.group.getType(), false)
                            .setOverrideForType(
                                    new TrackSelectionOverride(
                                            ref.group.getMediaTrackGroup(),
                                            List.of(ref.index)))
                            .build());
        }

        @Override
        public void stop() {
            ExoPlayer current = player;
            if (current == null) return;
            player = null;
            try {
                current.clearVideoSurface();
            } catch (RuntimeException e) {
                PlaybackDiagnostics.record(PlayerActivity.this, "player.detach", e, "");
            }
            try {
                current.release();
            } catch (RuntimeException e) {
                PlaybackDiagnostics.record(PlayerActivity.this, "player.release", e, "");
            }
            updatePlayButton();
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

    private final List<TrackRef> trackRefs = new ArrayList<>();

    /** Page listener: maps the controller's semantic events onto Android views and diagnostics. */
    private final class Page implements PlaybackController.Listener {
        @Override
        public boolean alive() {
            return !isFinishing() && !isDestroyed();
        }

        @Override
        public boolean isSeeking() {
            return view != null && view.isSeeking();
        }

        @Override
        public void title(String value) {
            view.setTitle(value);
        }

        @Override
        public void hint() {
            updateHint();
        }

        @Override
        public void settings() {
            if (vr != null) vr.settings = controller.state().projection;
        }

        @Override
        public void videoSize() {
            Source s = controller.state().source;
            if (vr == null || s == null) return;
            vr.videoWidth = s.width > 0 ? s.width : 1920;
            vr.videoHeight = s.height > 0 ? s.height : 1080;
            vr.videoPixelAspect = 1;
        }

        @Override
        public void render() {
            if (vr != null) vr.requestRender();
        }

        @Override
        public void gyroBaseReset() {
            gyroBase = false;
        }

        @Override
        public void playButton() {
            updatePlayButton();
        }

        @Override
        public void time(long position, long duration) {
            view.setTime(Ui.time(position) + " / " + Ui.time(duration));
        }

        @Override
        public void progress(int value) {
            view.setProgress(value);
        }

        @Override
        public void showControls() {
            view.showControls(true);
        }

        @Override
        public void scheduleHide() {
            view.scheduleHide();
        }

        @Override
        public void loadFailed(Throwable failure) {
            view.setHint(tr(R.string.player_load_failed));
            error(failure);
        }

        @Override
        public void playbackFailed(String phase, RuntimeException failure, String graphics) {
            PlaybackDiagnostics.record(PlayerActivity.this, phase, failure, graphics);
            view.cancelHide();
            if (!alive()) return;
            view.setHint(
                    controller.state().rendererFailed
                            ? tr(R.string.player_renderer_failed)
                            : tr(R.string.player_startup_failed_hint));
            view.showControls(true);
            view.display(
                    new AlertDialog.Builder(PlayerActivity.this)
                            .setTitle(tr(R.string.player_startup_failed))
                            .setMessage(
                                    tr(
                                            R.string.player_startup_message,
                                            failure.getClass().getSimpleName()))
                            .setPositiveButton(
                                    tr(R.string.player_view_diagnostics),
                                    (d, w) -> PlaybackDiagnostics.show(PlayerActivity.this))
                            .setNegativeButton(
                                    tr(R.string.player_back_library),
                                    (d, w) -> finish()));
        }

        @Override
        public void engineFailure(String phase, RuntimeException failure) {
            PlaybackDiagnostics.record(PlayerActivity.this, phase, failure, "");
        }

        @Override
        public void localFavoriteUpdated() {
            Toast.makeText(
                            PlayerActivity.this,
                            tr(R.string.player_local_updated),
                            Toast.LENGTH_SHORT)
                    .show();
        }

        @Override
        public void serverFavoriteConfirmed() {
            Toast.makeText(
                            PlayerActivity.this,
                            tr(R.string.player_server_confirmed),
                            Toast.LENGTH_SHORT)
                    .show();
        }

        @Override
        public void serverFavoriteFailed(Throwable failure) {
            error(failure);
        }
    }
}
