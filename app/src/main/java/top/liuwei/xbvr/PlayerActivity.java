package top.liuwei.xbvr;
import top.liuwei.xbvr.data.ProfileJsonMapper;
import top.liuwei.xbvr.domain.Projection;
import top.liuwei.xbvr.domain.ResourceIdentity;
import top.liuwei.xbvr.domain.SelectedFormatPolicy;
import top.liuwei.xbvr.domain.ServerProfile;
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
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import static top.liuwei.xbvr.domain.Models.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlayerActivity extends Activity
        implements SensorEventListener, PlayerView.Actions, PlayerDialogs.Menu {
    private static final String STATE_PLAY_WHEN_READY = "player.playWhenReady";
    private Store store;
    private Api api;
    private Detail detail;
    private Source source;
    private int selected;
    private ExoPlayer player;
    private Surface decoderSurface;
    private VrView vr;
    private PlayerView view;
    private PlayerDialogs dialogs;
    private Projection projection = new Projection();
    private boolean manual, active, gyro, gyroBase, wasPlaying = true, loaded, hdr;
    private long savedPosition;
    private String key, entryKey, url;
    private boolean rendererFailed;
    private SensorManager sensors;
    private Sensor rotationSensor;
    private float baseYaw, basePitch;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) wasPlaying = state.getBoolean(STATE_PLAY_WHEN_READY, true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        store = new Store(this);
        url = getIntent().getStringExtra("url");
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
        try {
            entryKey = store.playbackKey(api.id, url);
            build();
            sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
            if (sensors != null) {
                rotationSensor = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
                if (rotationSensor == null)
                    rotationSensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            }
            io.execute(
                    () -> {
                        try {
                            Detail d = api.detail(url);
                            runOnUiThread(
                                    () -> {
                                        if (isFinishing() || isDestroyed()) return;
                                        try {
                                            detail = d;
                                            view.setTitle(d.title);
                                            selected = 0;
                                            String previous =
                                                    store.selectedSource(entryKey);
                                            for (int i = 0; i < d.sources.size(); i++)
                                                if (ResourceIdentity.of(d.sources.get(i).url)
                                                        .equals(ResourceIdentity.of(previous)))
                                                    selected = i;
                                            select(selected, false);
                                        } catch (RuntimeException e) {
                                            playbackFailure("media.select", e, "");
                                        }
                                    });
                        } catch (Exception e) {
                            runOnUiThread(
                                    () -> {
                                        if (!isFinishing() && !isDestroyed()) {
                                            view.setHint(tr(R.string.player_load_failed));
                                            error(e);
                                        }
                                    });
                        }
                    });
        } catch (RuntimeException e) {
            playbackFailure("player.create", e, "");
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
                            if (isDestroyed() || isFinishing() || rendererFailed) return;
                            decoderSurface = s;
                            if (player != null && active)
                                try {
                                    player.setVideoSurface(s);
                                } catch (RuntimeException e) {
                                    playbackFailure("player.surface", e, "");
                                }
                        });
        vr.onFailure(
                (phase, failure, diagnostic) -> {
                    if (isFinishing() || isDestroyed()) return;
                    rendererFailed = true;
                    playbackFailure(phase, failure, diagnostic);
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
        if (player == null) return;
        if (player.getPlayWhenReady() && player.getPlaybackState() != Player.STATE_ENDED)
            player.pause();
        else {
            if (player.getPlaybackState() == Player.STATE_ENDED) player.seekTo(0);
            player.play();
        }
        save();
        updatePlayButton();
        view.scheduleHide();
    }

    private String eyeLabel() {
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
                index -> {
                    projection.eye = index;
                    updateHint();
                    vr.requestRender();
                    save();
                });
    }

    @Override
    public void resetView() {
        projection.yaw = projection.pitch = 0;
        projection.viewFov = 75;
        gyroBase = false;
        vr.requestRender();
        save();
    }

    private void updatePlayButton() {
        view.updatePlayButton(
                player != null
                        && player.getPlayWhenReady()
                        && player.getPlaybackState() != Player.STATE_ENDED);
    }

    private void error(Throwable failure) {
        if (isFinishing() || isDestroyed()) return;
        view.error(failure);
    }

    @Override
    public boolean hasPlayer() {
        return player != null;
    }

    @Override
    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    @Override
    public void seekPreview(int progress) {
        if (player != null && player.getDuration() > 0)
            view.setTime(
                    Ui.time(player.getDuration() * progress / 10000)
                            + " / "
                            + Ui.time(player.getDuration()));
    }

    @Override
    public void seekFinished(int progress) {
        if (player != null && player.getDuration() > 0)
            player.seekTo(player.getDuration() * progress / 10000);
        save();
    }

    private void playbackFailure(String phase, RuntimeException failure, String graphics) {
        wasPlaying = false;
        stopPlayer();
        PlaybackDiagnostics.record(this, phase, failure, graphics);
        view.cancelHide();
        if (isFinishing() || isDestroyed()) return;
        view.setHint(
                rendererFailed
                        ? tr(R.string.player_renderer_failed)
                        : tr(R.string.player_startup_failed_hint));
        view.showControls(true);
        view.display(
                new AlertDialog.Builder(this)
                        .setTitle(tr(R.string.player_startup_failed))
                        .setMessage(
                                tr(
                                        R.string.player_startup_message,
                                        failure.getClass().getSimpleName()))
                        .setPositiveButton(
                                tr(R.string.player_view_diagnostics),
                                (d, w) -> PlaybackDiagnostics.show(this))
                        .setNegativeButton(tr(R.string.player_back_library), (d, w) -> finish()));
    }

    private void stopPlayer() {
        ExoPlayer current = player;
        if (current == null) return;
        player = null;
        try {
            savedPosition = current.getCurrentPosition();
        } catch (RuntimeException e) {
            PlaybackDiagnostics.record(this, "player.position", e, "");
        }
        try {
            current.clearVideoSurface();
        } catch (RuntimeException e) {
            PlaybackDiagnostics.record(this, "player.detach", e, "");
        }
        try {
            current.release();
        } catch (RuntimeException e) {
            PlaybackDiagnostics.record(this, "player.release", e, "");
        }
        updatePlayButton();
    }

    @Override
    public void more() {
        dialogs.more(this);
    }

    @Override
    public void restart() {
        if (player != null) {
            player.seekTo(0);
            save();
        }
    }

    private void select(int index, boolean keep) {
        long position = player == null ? savedPosition : player.getCurrentPosition();
        boolean playing = player == null ? wasPlaying : player.getPlayWhenReady();
        save();
        selected = index;
        source = detail.sources.get(index);
        key = store.playbackKey(api.id, source.url);
        projection = inferSource();
        manual = store.restore(key, projection);
        vr.settings = projection;
        vr.videoWidth = source.width > 0 ? source.width : 1920;
        vr.videoHeight = source.height > 0 ? source.height : 1080;
        vr.videoPixelAspect = 1;
        hdr = false;
        vr.requestRender();
        savedPosition = keep ? position : store.position(key);
        wasPlaying = playing;
        store.selectedSource(entryKey, source.url);
        loaded = true;
        gyroBase = false;
        updateHint();
        if (active) startPlayer();
    }

    private Projection inferSource() {
        return SelectedFormatPolicy.infer(detail, source);
    }

    private void automatic() {
        if (source == null) return;
        Projection inferred = inferSource();
        inferred.eye = projection.eye;
        inferred.yaw = projection.yaw;
        inferred.pitch = projection.pitch;
        inferred.viewFov = projection.viewFov;
        projection = inferred;
        manual = false;
        vr.settings = projection;
        gyroBase = false;
        updateHint();
        vr.requestRender();
        save();
    }

    private void startPlayer() {
        if (source == null || rendererFailed) return;
        try {
            createPlayer();
        } catch (RuntimeException e) {
            playbackFailure("player.initialize", e, "");
        }
    }

    private void createPlayer() {
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
                    public void onPlaybackStateChanged(int state) {
                        updatePlayButton();
                        if (state == Player.STATE_ENDED) view.showControls(true);
                    }

                    @Override
                    public void onTracksChanged(Tracks tracks) {
                        hdr = false;
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
                                            hdr = true;
                                    }
                        updateHint();
                    }
                });
        MediaItem.Builder item = new MediaItem.Builder().setUri(source.url);
        List<MediaItem.SubtitleConfiguration> subs = new ArrayList<>();
        for (Subtitle s : detail.subtitles) {
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
        player.seekTo(savedPosition);
        player.setPlayWhenReady(wasPlaying);
        player.prepare();
    }

    private void updateHint() {
        String status =
                Ui.projectionLabel(this, projection)
                        + " · "
                        + eyeLabel()
                        + " · "
                        + Ui.projectionReason(this, projection)
                        + " · "
                        + tr(gyro ? R.string.player_gyro_hint : R.string.player_touch_hint)
                        + (hdr ? " · " + tr(R.string.player_hdr_hint) : "");
        view.setStatus(status, gyro);
    }

    @Override
    public void jump(long delta) {
        if (player != null) {
            player.seekTo(Math.max(0, player.getCurrentPosition() + delta));
            save();
        }
    }

    private void save() {
        if (key == null || !loaded) return;
        long pos = player == null ? savedPosition : player.getCurrentPosition();
        store.save(key, pos, projection, manual);
        store.entryPosition(entryKey, pos);
    }

    private final Runnable ticker =
            new Runnable() {
                int ticks;

                public void run() {
                    if (player != null) {
                        long duration = Math.max(0, player.getDuration()),
                                position = player.getCurrentPosition();
                        updatePlayButton();
                        if (!view.isSeeking()) {
                            view.setTime(Ui.time(position) + " / " + Ui.time(duration));
                            view.setProgress(
                                    duration > 0 ? (int) (position * 10000 / duration) : 0);
                        }
                        if (++ticks % 5 == 0) save();
                    }
                    if (active) main.postDelayed(this, 1000);
                }
            };

    @Override
    public void formats() {
        if (!loaded) return;
        List<String> items =
                new ArrayList<>(
                        List.of(
                                tr(R.string.player_projection_layout),
                                tr(R.string.player_eye_option, eyeLabel())));
        if (projection.kind == Projection.FLAT && projection.layout != Projection.MONO)
            items.add(tr(R.string.player_packing_option));
        dialogs.formats(
                tr(R.string.player_format_title, Ui.projectionLabel(this, projection)),
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
        if (!loaded) return;
        dialogs.projectionFormats(
                tr(R.string.player_projection_title, Ui.projectionLabel(this, projection)),
                modes,
                index -> {
                    if (index == 7) {
                        automatic();
                        return;
                    }
                    Projection next = inferSource();
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
                                projection = next;
                                manual = true;
                                vr.settings = projection;
                                updateHint();
                                vr.requestRender();
                                save();
                                if (projection.halfPacked) packing();
                            });
                });
    }

    private void packing() {
        dialogs.packing(
                tr(R.string.player_packing),
                new String[] {
                    tr(R.string.player_packing_half), tr(R.string.player_packing_full)
                },
                projection.halfPacked ? 0 : 1,
                p -> {
                    projection.halfPacked = p == 0;
                    vr.requestRender();
                    save();
                });
    }

    @Override
    public void files() {
        if (detail == null) return;
        String[] names =
                detail.sources.stream()
                        .map(s -> s.name + (s.filename.isBlank() ? "" : "\n" + s.filename))
                        .toArray(String[]::new);
        dialogs.files(
                tr(R.string.player_choose_file),
                names,
                selected,
                i -> {
                    if (i != selected) select(i, true);
                });
    }

    @Override
    public void chapters() {
        if (detail == null) return;
        if (detail.tags.isEmpty()) {
            Toast.makeText(this, tr(R.string.player_no_chapters), Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels =
                detail.tags.stream()
                        .map(t -> Ui.time(t.time) + "  " + t.name)
                        .toArray(String[]::new);
        dialogs.chapters(
                tr(R.string.player_chapters),
                labels,
                i -> {
                    if (player != null) {
                        player.seekTo(detail.tags.get(i).time);
                        save();
                    }
                });
    }

    @Override
    public void speed() {
        float[] rates = {.5f, .75f, 1, 1.25f, 1.5f, 2};
        dialogs.speed(
                tr(R.string.player_speed),
                new String[] {"0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×"},
                i -> {
                    if (player != null) player.setPlaybackSpeed(rates[i]);
                });
    }

    @Override
    public void tracks() {
        if (player == null) return;
        List<String> labels =
                new ArrayList<>(
                        List.of(tr(R.string.player_audio_auto), tr(R.string.player_subtitles_off)));
        List<Runnable> actions = new ArrayList<>();
        actions.add(
                () ->
                        player.setTrackSelectionParameters(
                                player.getTrackSelectionParameters()
                                        .buildUpon()
                                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                        .build()));
        actions.add(
                () ->
                        player.setTrackSelectionParameters(
                                player.getTrackSelectionParameters()
                                        .buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                        .build()));
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
        if (projection.kind == Projection.FLAT) {
            packing();
            return;
        }
        dialogs.lens(
                tr(R.string.player_lens),
                projection,
                (x, y, radius, rotation, mirror) -> {
                    projection.centerX = x;
                    projection.centerY = y;
                    projection.radius = radius;
                    projection.rotation = rotation;
                    projection.mirror = mirror;
                    vr.requestRender();
                    save();
                });
    }

    @Override
    public void favorites() {
        if (detail == null) return;
        List<String> items = new ArrayList<>();
        items.add(
                store.favorite(entryKey)
                        ? tr(R.string.player_local_unfavorite)
                        : tr(R.string.player_local_favorite));
        if (detail.writeFavorite)
            items.add(
                    detail.favorite
                            ? tr(R.string.player_server_unfavorite)
                            : tr(R.string.player_server_favorite));
        dialogs.favorites(
                tr(R.string.player_favorites),
                items.toArray(new String[0]),
                i -> {
                    if (i == 0) {
                        store.favorite(entryKey, !store.favorite(entryKey));
                        Toast.makeText(this, tr(R.string.player_local_updated), Toast.LENGTH_SHORT)
                                .show();
                    } else {
                        boolean value = !detail.favorite;
                        io.execute(
                                () -> {
                                    try {
                                        api.favorite(detail, value);
                                        runOnUiThread(
                                                () ->
                                                        Toast.makeText(
                                                                        this,
                                                                        tr(
                                                                                R.string
                                                                                        .player_server_confirmed),
                                                                        Toast.LENGTH_SHORT)
                                                                .show());
                                    } catch (Exception e) {
                                        runOnUiThread(() -> error(e));
                                    }
                                });
                    }
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
                    active
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
        if (!gyro || !active) return;
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
                STATE_PLAY_WHEN_READY, player == null ? wasPlaying : player.getPlayWhenReady());
        save();
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onStart() {
        super.onStart();
        active = true;
        if (view != null) view.setActive(true);
        gyroBase = false;
        if (vr != null) vr.onResume();
        if (loaded) startPlayer();
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
        active = false;
        if (view != null) view.setActive(false);
        main.removeCallbacks(ticker);
        if (view != null) view.cancelHide();
        if (sensors != null) sensors.unregisterListener(this);
        save();
        if (player != null) {
            wasPlaying = player.getPlayWhenReady();
            stopPlayer();
        }
        if (vr != null) vr.onPause();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (view != null) {
            view.dismissDialogs();
            view.cancelHide();
        }
        io.shutdownNow();
        if (vr != null) vr.release();
        super.onDestroy();
    }
}
