package top.liuwei.xbvr;
import top.liuwei.xbvr.data.DefaultMediaDetailsRepository;
import top.liuwei.xbvr.data.ProfileJsonMapper;
import top.liuwei.xbvr.domain.Projection;
import top.liuwei.xbvr.domain.ResourceIdentity;
import top.liuwei.xbvr.domain.ServerProfile;
import top.liuwei.xbvr.media.Media3PlaybackSession;
import top.liuwei.xbvr.media.RenderMath;
import top.liuwei.xbvr.media.VrView;
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
import androidx.media3.common.text.Cue;
import java.util.*;
import java.util.concurrent.*;
import static top.liuwei.xbvr.domain.Models.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlayerActivity extends Activity
        implements SensorEventListener, PlayerView.Actions, PlayerDialogs.Menu {
    private static final String STATE_PLAY_WHEN_READY = "player.playWhenReady";
    private Store store;
    private Api api;
    private AppServices services;
    private Media3PlaybackSession session;
    private VrView vr;
    private PlayerView view;
    private PlayerDialogs dialogs;
    private boolean gyro, gyroBase;
    private SensorManager sensors;
    private Sensor rotationSensor;
    private float baseYaw, basePitch;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
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
        services = new AppServices(this);
        session =
                new Media3PlaybackSession(
                        this,
                        services.mediaDataSourceFactory(api.client),
                        services.diagnostics(),
                        mediaListener());
        controller =
                new PlaybackController(
                        session,
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
                            try {
                                session.attachSurface(s);
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
                    session.clearSurface();
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

    /**
     * Maps the Media3 session's callbacks onto the views and the controller, exactly as the old
     * inline {@code Player.Listener} did. Runs on the main thread only.
     */
    private Media3PlaybackSession.Listener mediaListener() {
        return new Media3PlaybackSession.Listener() {
            @Override
            public void videoSize(int width, int height, float pixelWidthHeightRatio) {
                vr.setVideoSize(Math.max(1, width), Math.max(1, height), pixelWidthHeightRatio);
                vr.requestRender();
            }

            @Override
            public void cues(List<Cue> cues) {
                view.setCues(cues);
            }

            @Override
            public void playbackError(PlaybackException e) {
                PlaybackDiagnostics.record(
                        PlayerActivity.this, "media." + e.getErrorCodeName(), e, "");
                view.setHint(tr(R.string.player_media_failed, e.getErrorCodeName()));
                view.showControls(true);
            }

            @Override
            public void isPlayingChanged(boolean playing) {
                updatePlayButton();
                if (playing) view.scheduleHide();
                else view.showControls(true);
            }

            @Override
            public void playbackStateChanged(int playbackState) {
                updatePlayButton();
                if (playbackState == Player.STATE_ENDED) view.showControls(true);
            }

            @Override
            public void hdrChanged(boolean value) {
                controller.hdr(value);
            }

            @Override
            public void stopped() {
                updatePlayButton();
            }
        };
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
        return session.hasEngine();
    }

    @Override
    public boolean isPlaying() {
        return session.isPlaying();
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
        if (!session.hasEngine()) return;
        List<String> labels =
                new ArrayList<>(
                        List.of(tr(R.string.player_audio_auto), tr(R.string.player_subtitles_off)));
        List<Runnable> actions = new ArrayList<>();
        actions.add(() -> session.audioAuto());
        actions.add(() -> session.subtitlesOff());
        for (Tracks.Group g : session.currentTracks().getGroups())
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
                                    session.overrideTrack(
                                            g.getType(), g.getMediaTrackGroup(), track));
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
                session.hasEngine() ? session.playWhenReady() : controller.state().wasPlaying);
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
            if (vr != null) vr.setSettings(controller.state().projection);
        }

        @Override
        public void videoSize() {
            Source s = controller.state().source;
            if (vr == null || s == null) return;
            vr.setVideoSize(s.width > 0 ? s.width : 1920, s.height > 0 ? s.height : 1080, 1);
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
