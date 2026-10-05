package top.liuwei.xbvr;
import top.liuwei.xbvr.domain.Projection;
import top.liuwei.xbvr.domain.ResourceIdentity;
import top.liuwei.xbvr.domain.SelectedFormatPolicy;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.view.*;
import android.widget.*;
import android.hardware.*;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import androidx.media3.common.*;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.*;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.ui.SubtitleView;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import static top.liuwei.xbvr.domain.Models.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlayerActivity extends Activity implements SensorEventListener {
    private static final String STATE_PLAY_WHEN_READY = "player.playWhenReady";
    private Store store;
    private Api api;
    private Detail detail;
    private Source source;
    private int selected;
    private ExoPlayer player;
    private Surface decoderSurface;
    private VrView vr;
    private SubtitleView subtitle;
    private Projection projection = new Projection();
    private boolean manual, active, seekTouch, gyro, gyroBase, wasPlaying = true, loaded, hdr;
    private long savedPosition;
    private String key, entryKey, url;
    private FrameLayout overlay;
    private TextView title, hint, time;
    private SeekBar seek;
    private ImageButton pause, gyroButton;
    private LinearLayout playerHeader, playerFooter, playerTransport;
    private int safeLeft, safeTop, safeRight, safeBottom;
    private boolean controlsShown = true, rendererFailed;
    private String pauseIcon = "";
    private int nightMode;
    private Dialog moreDialog;
    private final Set<Dialog> dialogs = new HashSet<>();
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
        try {
            JSONArray profiles = store.profiles();
            for (int i = 0; i < profiles.length(); i++) {
                JSONObject p = profiles.getJSONObject(i);
                if (p.optString("id").equals(getIntent().getStringExtra("profile")))
                    api = new Api(p);
            }
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
                                            title.setText(d.title);
                                            selected = 0;
                                            String previous =
                                                    store.prefs.getString("source:" + entryKey, "");
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
                                            hint.setText(tr(R.string.player_load_failed));
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
        nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        Ui.edgeToEdge(this, true);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
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
        root.addView(vr, new FrameLayout.LayoutParams(-1, -1));
        vr.setContentDescription(tr(R.string.player_surface_description));
        vr.onTap(() -> showControls(!controlsShown));
        vr.onDoubleTap(
                () -> {
                    jump(-10000);
                    showControls(true);
                },
                () -> {
                    jump(10000);
                    showControls(true);
                });
        subtitle = new SubtitleView(this);
        FrameLayout.LayoutParams sub = new FrameLayout.LayoutParams(-1, -1);
        sub.bottomMargin = Ui.dp(this, 170);
        root.addView(subtitle, sub);
        overlay = new FrameLayout(this);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        applyPlayerInsets();

        LinearLayout header = Ui.row(this);
        playerHeader = header;
        header.setPadding(Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 24));
        header.setBackground(
                new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[] {0xE6000000, 0x00000000}));
        header.addView(
                controlIcon("back", tr(R.string.player_back_library), this::finish),
                new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        title = Ui.text(this, getIntent().getStringExtra("title"), 17, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(
                controlIcon("fullscreen", tr(R.string.player_rotate), this::orientation),
                new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        header.addView(
                controlIcon("more", tr(R.string.player_more), this::more),
                new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        overlay.addView(header, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        header.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> positionTransport());

        LinearLayout transport = Ui.row(this);
        playerTransport = transport;
        transport.setGravity(Gravity.CENTER);
        ImageButton previous =
                controlIcon("previous", tr(R.string.player_rewind), () -> jump(-10000));
        transport.addView(
                previous, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));
        pause = controlIcon("play", tr(R.string.player_play), this::togglePlayback);
        pause.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));
        pause.setImageTintList(ColorStateList.valueOf(0xFF142026));
        pause.setBackground(Ui.ripple(this, 0xEEFFFFFF, 40));
        LinearLayout.LayoutParams big =
                new LinearLayout.LayoutParams(Ui.dp(this, 76), Ui.dp(this, 76));
        big.setMargins(Ui.dp(this, 28), 0, Ui.dp(this, 28), 0);
        transport.addView(pause, big);
        transport.addView(
                controlIcon("next", tr(R.string.player_forward), () -> jump(10000)),
                new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));
        overlay.addView(transport, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        LinearLayout footer = Ui.column(this);
        playerFooter = footer;
        footer.setPadding(Ui.dp(this, 18), Ui.dp(this, 28), Ui.dp(this, 18), Ui.dp(this, 12));
        footer.setBackground(
                new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[] {0x00000000, 0xDA000000, 0xF5000000}));
        time = Ui.text(this, "0:00 / 0:00", 12, 0xFFE2E8ED);
        time.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        footer.addView(time);
        seek = new SeekBar(this);
        seek.setMax(10000);
        seek.setContentDescription(tr(R.string.player_progress));
        seek.setProgressTintList(ColorStateList.valueOf(0xFF71E0CB));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(0x66FFFFFF));
        seek.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        seek.setPadding(0, 0, 0, 0);
        footer.addView(seek, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
        LinearLayout quick = Ui.row(this);
        quick.setGravity(Gravity.CENTER);
        addQuickIcon(quick, quickIcon("film", tr(R.string.player_format), this::formats));
        addQuickIcon(quick, quickIcon("library", tr(R.string.player_files), this::files));
        gyroButton = quickIcon("gyro", tr(R.string.player_gyro_enable), this::toggleGyro);
        addQuickIcon(quick, gyroButton);
        addQuickIcon(quick, quickIcon("reset", tr(R.string.player_reset_view), this::resetView));
        footer.addView(quick);
        hint = Ui.text(this, tr(R.string.player_loading), 11, 0xFFB7C3CA);
        hint.setMaxLines(2);
        hint.setEllipsize(TextUtils.TruncateAt.END);
        hint.setPadding(0, Ui.dp(this, 10), 0, 0);
        footer.addView(hint);
        overlay.addView(footer, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        footer.addOnLayoutChangeListener(
                (v, l, t, r, b, ol, ot, or, ob) -> {
                    positionSubtitles();
                    positionTransport();
                });
        seek.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    public void onStartTrackingTouch(SeekBar s) {
                        seekTouch = true;
                        main.removeCallbacks(hideControls);
                    }

                    public void onProgressChanged(SeekBar s, int p, boolean user) {
                        if (user && player != null && player.getDuration() > 0)
                            textIfChanged(
                                    time,
                                    Ui.time(player.getDuration() * p / 10000)
                                            + " / "
                                            + Ui.time(player.getDuration()));
                    }

                    public void onStopTrackingTouch(SeekBar s) {
                        if (player != null && player.getDuration() > 0)
                            player.seekTo(player.getDuration() * s.getProgress() / 10000);
                        seekTouch = false;
                        save();
                        scheduleHide();
                    }
                });
        adaptControls();
    }

    private void applyPlayerInsets() {
        overlay.setOnApplyWindowInsetsListener(
                (view, in) -> {
                    safeLeft = safeTop = safeRight = safeBottom = 0;
                    if (Build.VERSION.SDK_INT >= 30) {
                        android.graphics.Insets bars =
                                in.getInsets(
                                        WindowInsets.Type.systemBars()
                                                | WindowInsets.Type.displayCutout());
                        safeLeft = bars.left;
                        safeTop = bars.top;
                        safeRight = bars.right;
                        safeBottom =
                                Math.max(
                                        bars.bottom,
                                        in.getInsets(WindowInsets.Type.mandatorySystemGestures())
                                                .bottom);
                    } else {
                        DisplayCutout cut = in.getDisplayCutout();
                        if (cut != null) {
                            safeLeft = cut.getSafeInsetLeft();
                            safeTop = cut.getSafeInsetTop();
                            safeRight = cut.getSafeInsetRight();
                            safeBottom = cut.getSafeInsetBottom();
                        }
                        safeBottom =
                                Math.max(safeBottom, in.getMandatorySystemGestureInsets().bottom);
                    }
                    adaptControls();
                    positionSubtitles();
                    return in;
                });
        overlay.requestApplyInsets();
    }

    private void adaptControls() {
        if (playerHeader == null || playerFooter == null) return;
        boolean compact = getResources().getConfiguration().screenHeightDp < 480;
        playerHeader.setPadding(
                safeLeft + Ui.dp(this, 8),
                safeTop + Ui.dp(this, compact ? 6 : 12),
                safeRight + Ui.dp(this, 8),
                Ui.dp(this, compact ? 12 : 24));
        playerFooter.setPadding(
                safeLeft + Ui.dp(this, 18),
                Ui.dp(this, compact ? 12 : 28),
                safeRight + Ui.dp(this, 18),
                safeBottom + Ui.dp(this, compact ? 8 : 12));
        hint.setMaxLines(compact ? 1 : 2);
        overlay.post(this::positionTransport);
    }

    private void positionTransport() {
        if (playerTransport == null || playerFooter == null || overlay.getHeight() == 0) return;
        boolean compact = getResources().getConfiguration().screenHeightDp < 480;
        int center = Ui.dp(this, 76), side = Ui.dp(this, 56);
        float offset = 0;
        if (compact) {
            offset = (playerHeader.getHeight() - playerFooter.getHeight()) / 2f;
            int available =
                    overlay.getHeight()
                            - overlay.getPaddingTop()
                            - overlay.getPaddingBottom()
                            - playerHeader.getHeight()
                            - playerFooter.getHeight();
            int fit = Math.max(Ui.dp(this, 48), available - Ui.dp(this, 8));
            center = Math.min(center, fit);
            side = Math.min(side, fit);
        }
        if (playerTransport.getTranslationY() != offset) playerTransport.setTranslationY(offset);
        resizeTransportButton(playerTransport.getChildAt(0), side);
        resizeTransportButton(pause, center);
        resizeTransportButton(playerTransport.getChildAt(2), side);
    }

    private void resizeTransportButton(View button, int size) {
        android.view.ViewGroup.LayoutParams p = button.getLayoutParams();
        if (p.width == size && p.height == size) return;
        p.width = p.height = size;
        button.setLayoutParams(p);
        if (button == pause) {
            int padding =
                    size == Ui.dp(this, 76)
                            ? Ui.dp(this, 20)
                            : Math.max(Ui.dp(this, 10), (size - Ui.dp(this, 28)) / 2);
            button.setPadding(padding, padding, padding, padding);
        }
    }

    private ImageButton controlIcon(String icon, String description, Runnable action) {
        ImageButton b =
                Ui.icon(
                        this,
                        icon,
                        description,
                        () -> {
                            action.run();
                            scheduleHide();
                        });
        b.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        b.setBackground(Ui.ripple(this, 0x18000000, 28));
        return b;
    }

    private ImageButton quickIcon(String icon, String description, Runnable action) {
        ImageButton b = controlIcon(icon, description, action);
        b.setTooltipText(description);
        b.setBackground(Ui.ripple(this, 0x22FFFFFF, 24));
        return b;
    }

    private void addQuickIcon(LinearLayout row, ImageButton icon) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1);
        p.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        row.addView(icon, p);
    }

    private void orientation() {
        setRequestedOrientation(
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                        ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    private void togglePlayback() {
        if (player == null) return;
        if (player.getPlayWhenReady() && player.getPlaybackState() != Player.STATE_ENDED)
            player.pause();
        else {
            if (player.getPlaybackState() == Player.STATE_ENDED) player.seekTo(0);
            player.play();
        }
        save();
        updatePlayButton();
        scheduleHide();
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
        display(
                new AlertDialog.Builder(this)
                        .setTitle(tr(R.string.player_viewing_eye))
                        .setSingleChoiceItems(
                                choices,
                                projection.eye,
                                (d, index) -> {
                                    projection.eye = index;
                                    updateHint();
                                    vr.requestRender();
                                    save();
                                    d.dismiss();
                                })
                        .setNegativeButton(tr(R.string.player_cancel), null));
    }

    private void resetView() {
        projection.yaw = projection.pitch = 0;
        projection.viewFov = 75;
        gyroBase = false;
        vr.requestRender();
        save();
    }

    private void showControls(boolean show) {
        main.removeCallbacks(hideControls);
        controlsShown = show;
        overlay.animate().cancel();
        if (show) {
            overlay.setVisibility(View.VISIBLE);
            overlay.animate().alpha(1).setDuration(160).start();
            scheduleHide();
        } else
            overlay.animate()
                    .alpha(0)
                    .setDuration(220)
                    .withEndAction(
                            () -> {
                                if (!controlsShown) overlay.setVisibility(View.GONE);
                            })
                    .start();
        positionSubtitles();
    }

    private void positionSubtitles() {
        if (subtitle == null || overlay == null || playerFooter == null) return;
        int margin =
                controlsShown
                        ? Math.max(safeBottom + Ui.dp(this, 140), playerFooter.getHeight())
                                + Ui.dp(this, 12)
                        : safeBottom + Ui.dp(this, 24);
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) subtitle.getLayoutParams();
        if (p.bottomMargin != margin) {
            p.bottomMargin = margin;
            subtitle.setLayoutParams(p);
        }
    }

    private final Runnable hideControls =
            () -> {
                if (canHide()) showControls(false);
            };

    private boolean canHide() {
        return active
                && controlsShown
                && !seekTouch
                && dialogs.isEmpty()
                && player != null
                && player.isPlaying();
    }

    private void scheduleHide() {
        main.removeCallbacks(hideControls);
        if (canHide()) main.postDelayed(hideControls, 4200);
    }

    private void updatePlayButton() {
        if (pause == null) return;
        String icon =
                player != null
                                && player.getPlayWhenReady()
                                && player.getPlaybackState() != Player.STATE_ENDED
                        ? "pause"
                        : "play";
        if (!icon.equals(pauseIcon)) {
            pauseIcon = icon;
            pause.setImageDrawable(Ui.iconDrawable(icon, 0xFF142026));
            pause.setContentDescription(
                    icon.equals("pause") ? tr(R.string.player_pause) : tr(R.string.player_play));
        }
    }

    private <T extends Dialog> T present(T dialog) {
        dialogs.add(dialog);
        main.removeCallbacks(hideControls);
        dialog.setOnDismissListener(
                d -> {
                    dialogs.remove(dialog);
                    scheduleHide();
                });
        dialog.show();
        applyDialogPalette(dialog);
        return dialog;
    }

    private AlertDialog display(AlertDialog.Builder builder) {
        return present(builder.create());
    }

    private void error(Throwable failure) {
        if (isFinishing() || isDestroyed()) return;
        display(
                new AlertDialog.Builder(this)
                        .setTitle(tr(R.string.player_error_title))
                        .setMessage(Ui.errorMessage(this, failure))
                        .setPositiveButton(tr(R.string.player_dismiss), null));
    }

    private void playbackFailure(String phase, RuntimeException failure, String graphics) {
        wasPlaying = false;
        stopPlayer();
        PlaybackDiagnostics.record(this, phase, failure, graphics);
        main.removeCallbacks(hideControls);
        if (isFinishing() || isDestroyed()) return;
        if (hint != null)
            hint.setText(
                    rendererFailed
                            ? tr(R.string.player_renderer_failed)
                            : tr(R.string.player_startup_failed_hint));
        if (overlay != null) showControls(true);
        display(
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

    private void more() {
        Ui.Palette c = Ui.colors(this);
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        moreDialog = dialog;
        LinearLayout panel = Ui.column(this);
        panel.setTag("dialog-surface");
        panel.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 20), Ui.dp(this, 20));
        panel.setBackground(Ui.rounded(c.surface, 24, this));
        View handle = new View(this);
        handle.setTag("handle");
        handle.setBackground(Ui.rounded(c.border, 3, this));
        LinearLayout.LayoutParams hp =
                new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 4));
        hp.gravity = Gravity.CENTER;
        hp.bottomMargin = Ui.dp(this, 16);
        panel.addView(handle, hp);
        LinearLayout heading = Ui.row(this);
        TextView headingText = Ui.text(this, tr(R.string.player_settings), 20, c.text);
        headingText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.addView(headingText, new LinearLayout.LayoutParams(0, -2, 1));
        heading.addView(
                Ui.icon(this, "close", tr(R.string.player_close_settings), dialog::dismiss));
        panel.addView(heading);
        menuGroup(panel, tr(R.string.player_play), c);
        menuAction(panel, dialog, "clock", tr(R.string.player_chapters), this::chapters, c);
        menuAction(panel, dialog, "play", tr(R.string.player_speed), this::speed, c);
        menuAction(panel, dialog, "film", tr(R.string.player_tracks), this::tracks, c);
        menuGroup(panel, tr(R.string.player_view_group), c);
        menuAction(panel, dialog, "settings", tr(R.string.player_lens), this::lens, c);
        menuGroup(panel, tr(R.string.player_media_group), c);
        menuAction(panel, dialog, "heart", tr(R.string.player_favorites), this::favorites, c);
        menuAction(
                panel,
                dialog,
                "previous",
                tr(R.string.player_restart),
                () -> {
                    if (player != null) {
                        player.seekTo(0);
                        save();
                    }
                },
                c);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(panel);
        dialog.setContentView(scroll);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = .28f;
            window.setAttributes(attrs);
        }
        present(dialog);
        resizeMore();
    }

    private void menuGroup(LinearLayout panel, String label, Ui.Palette c) {
        TextView t = Ui.text(this, label, 11, c.muted);
        t.setTag("muted");
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, 14), 0, Ui.dp(this, 4));
        panel.addView(t);
    }

    private void menuAction(
            LinearLayout panel,
            Dialog dialog,
            String icon,
            String label,
            Runnable action,
            Ui.Palette c) {
        LinearLayout row = Ui.row(this);
        row.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        row.setMinimumHeight(Ui.dp(this, 48));
        row.setBackground(Ui.ripple(this, Color.TRANSPARENT, 14));
        ImageView image = new ImageView(this);
        image.setImageDrawable(Ui.iconDrawable(icon, c.text));
        row.addView(image, new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));
        TextView text = Ui.text(this, label, 15, c.text);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.leftMargin = Ui.dp(this, 14);
        row.addView(text, p);
        ImageView arrow = new ImageView(this);
        arrow.setTag("muted");
        arrow.setImageDrawable(Ui.iconDrawable("chevron", c.muted));
        row.addView(arrow, new LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18)));
        row.setContentDescription(label);
        row.setOnClickListener(
                v -> {
                    dialog.dismiss();
                    action.run();
                    scheduleHide();
                });
        panel.addView(row);
    }

    private void resizeMore() {
        if (moreDialog != null && moreDialog.isShowing()) {
            Window w = moreDialog.getWindow();
            if (w != null)
                w.setLayout(
                        getResources().getConfiguration().screenWidthDp >= 600
                                ? Ui.dp(this, 480)
                                : -1,
                        -2);
        }
    }

    private void applyDialogPalette(Dialog dialog) {
        Window w = dialog.getWindow();
        if (w == null) return;
        Ui.Palette c = Ui.colors(this);
        dialog.getContext().getTheme().rebase();
        if (dialog instanceof AlertDialog) Ui.decorateDialog(this, dialog);
        recolorDialog(w.getDecorView(), c);
    }

    private void recolorDialog(View v, Ui.Palette c) {
        if ("dialog-surface".equals(v.getTag())) v.setBackground(Ui.rounded(c.surface, 24, this));
        else if ("handle".equals(v.getTag())) v.setBackground(Ui.rounded(c.border, 3, this));
        if (v instanceof TextView) {
            TextView text = (TextView) v;
            text.setTextColor(
                    v instanceof Button ? c.accent : "muted".equals(v.getTag()) ? c.muted : c.text);
            text.setHintTextColor(c.muted);
        }
        if (v instanceof EditText) v.setBackgroundTintList(ColorStateList.valueOf(c.border));
        if (v instanceof CompoundButton)
            ((CompoundButton) v).setButtonTintList(ColorStateList.valueOf(c.accent));
        if (v instanceof CheckedTextView)
            ((CheckedTextView) v).setCheckMarkTintList(ColorStateList.valueOf(c.accent));
        if (v instanceof ImageView)
            ((ImageView) v)
                    .setImageTintList(
                            ColorStateList.valueOf("muted".equals(v.getTag()) ? c.muted : c.text));
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) recolorDialog(group.getChildAt(i), c);
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
        store.prefs.edit().putString("source:" + entryKey, source.url).apply();
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
                        subtitle.setCues(cues.cues);
                    }

                    @Override
                    public void onPlayerError(PlaybackException e) {
                        PlaybackDiagnostics.record(
                                PlayerActivity.this, "media." + e.getErrorCodeName(), e, "");
                        hint.setText(tr(R.string.player_media_failed, e.getErrorCodeName()));
                        showControls(true);
                    }

                    @Override
                    public void onIsPlayingChanged(boolean playing) {
                        updatePlayButton();
                        if (playing) scheduleHide();
                        else showControls(true);
                    }

                    @Override
                    public void onPlaybackStateChanged(int state) {
                        updatePlayButton();
                        if (state == Player.STATE_ENDED) showControls(true);
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
        subtitle.setCues(Collections.emptyList());
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
        textIfChanged(hint, status);
        hint.setContentDescription(status);
        if (gyroButton != null) {
            String description =
                    tr(gyro ? R.string.player_gyro_disable : R.string.player_gyro_enable);
            gyroButton.setSelected(gyro);
            gyroButton.setContentDescription(description);
            gyroButton.setTooltipText(description);
            gyroButton.setImageTintList(ColorStateList.valueOf(gyro ? 0xFFFFD54F : Color.WHITE));
            gyroButton.setBackground(Ui.ripple(this, gyro ? 0x33FFD54F : 0x22FFFFFF, 24));
        }
    }

    private void jump(long delta) {
        if (player != null) {
            player.seekTo(Math.max(0, player.getCurrentPosition() + delta));
            save();
        }
    }

    private void save() {
        if (key == null || !loaded) return;
        long pos = player == null ? savedPosition : player.getCurrentPosition();
        store.save(key, pos, projection, manual);
        store.prefs.edit().putLong("pos:" + entryKey, pos).apply();
    }

    private static void textIfChanged(TextView view, String value) {
        if (!value.contentEquals(view.getText())) view.setText(value);
    }

    private final Runnable ticker =
            new Runnable() {
                int ticks;

                public void run() {
                    if (player != null) {
                        long duration = Math.max(0, player.getDuration()),
                                position = player.getCurrentPosition();
                        updatePlayButton();
                        if (!seekTouch) {
                            textIfChanged(time, Ui.time(position) + " / " + Ui.time(duration));
                            seek.setProgress(
                                    duration > 0 ? (int) (position * 10000 / duration) : 0);
                        }
                        if (++ticks % 5 == 0) save();
                    }
                    if (active) main.postDelayed(this, 1000);
                }
            };

    private void formats() {
        if (!loaded) return;
        List<String> items =
                new ArrayList<>(
                        List.of(
                                tr(R.string.player_projection_layout),
                                tr(R.string.player_eye_option, eyeLabel())));
        if (projection.kind == Projection.FLAT && projection.layout != Projection.MONO)
            items.add(tr(R.string.player_packing_option));
        display(
                new AlertDialog.Builder(this)
                        .setTitle(
                                tr(
                                        R.string.player_format_title,
                                        Ui.projectionLabel(this, projection)))
                        .setItems(
                                items.toArray(new String[0]),
                                (d, index) -> {
                                    if (index == 0) projectionFormats();
                                    else if (index == 1) viewingEye();
                                    else packing();
                                })
                        .setNegativeButton(tr(R.string.player_cancel), null));
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
        display(
                new AlertDialog.Builder(this)
                        .setTitle(
                                tr(
                                        R.string.player_projection_title,
                                        Ui.projectionLabel(this, projection)))
                        .setItems(
                                modes,
                                (d, index) -> {
                                    if (index == 7) {
                                        automatic();
                                        return;
                                    }
                                    Projection next = inferSource();
                                    next.kind =
                                            index == 0
                                                    ? Projection.FLAT
                                                    : index <= 2
                                                            ? Projection.EQUIRECT
                                                            : Projection.FISHEYE;
                                    next.capture =
                                            index == 2
                                                    ? 360
                                                    : index == 4
                                                            ? 190
                                                            : index == 5
                                                                    ? 200
                                                                    : index == 6 ? 220 : 180;
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
                                    display(
                                            new AlertDialog.Builder(this)
                                                    .setTitle(tr(R.string.player_layout))
                                                    .setSingleChoiceItems(
                                                            new String[] {
                                                                tr(R.string.player_layout_mono),
                                                                tr(R.string.player_layout_sbs),
                                                                tr(R.string.player_layout_tb)
                                                            },
                                                            projection.layout,
                                                            (dialog, layout) -> {
                                                                next.layout = layout;
                                                                next.halfPacked =
                                                                        next.kind == Projection.FLAT
                                                                                && layout
                                                                                        != Projection
                                                                                                .MONO;
                                                                projection = next;
                                                                manual = true;
                                                                vr.settings = projection;
                                                                updateHint();
                                                                vr.requestRender();
                                                                save();
                                                                dialog.dismiss();
                                                                if (projection.halfPacked)
                                                                    packing();
                                                            }));
                                }));
    }

    private void packing() {
        display(
                new AlertDialog.Builder(this)
                        .setTitle(tr(R.string.player_packing))
                        .setSingleChoiceItems(
                                new String[] {
                                    tr(R.string.player_packing_half),
                                    tr(R.string.player_packing_full)
                                },
                                projection.halfPacked ? 0 : 1,
                                (d, p) -> {
                                    projection.halfPacked = p == 0;
                                    vr.requestRender();
                                    save();
                                    d.dismiss();
                                }));
    }

    private void files() {
        if (detail == null) return;
        String[] names =
                detail.sources.stream()
                        .map(s -> s.name + (s.filename.isBlank() ? "" : "\n" + s.filename))
                        .toArray(String[]::new);
        display(
                new AlertDialog.Builder(this)
                        .setTitle(tr(R.string.player_choose_file))
                        .setSingleChoiceItems(
                                names,
                                selected,
                                (d, i) -> {
                                    if (i != selected) select(i, true);
                                    d.dismiss();
                                }));
    }

    private void chapters() {
        if (detail == null) return;
        if (detail.tags.isEmpty()) {
            Toast.makeText(this, tr(R.string.player_no_chapters), Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels =
                detail.tags.stream()
                        .map(t -> Ui.time(t.time) + "  " + t.name)
                        .toArray(String[]::new);
        display(
                new AlertDialog.Builder(this)
                        .setTitle(tr(R.string.player_chapters))
                        .setItems(
                                labels,
                                (d, i) -> {
                                    if (player != null) {
                                        player.seekTo(detail.tags.get(i).time);
                                        save();
                                    }
                                }));
    }

    private void speed() {
        float[] rates = {.5f, .75f, 1, 1.25f, 1.5f, 2};
        display(
                new AlertDialog.Builder(this)
                        .setTitle(tr(R.string.player_speed))
                        .setItems(
                                new String[] {"0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×"},
                                (d, i) -> {
                                    if (player != null) player.setPlaybackSpeed(rates[i]);
                                }));
    }

    private void tracks() {
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
        display(
                new AlertDialog.Builder(this)
                        .setTitle(tr(R.string.player_tracks))
                        .setItems(labels.toArray(new String[0]), (d, i) -> actions.get(i).run()));
    }

    private void lens() {
        if (projection.kind == Projection.FLAT) {
            packing();
            return;
        }
        LinearLayout form = Ui.column(this);
        form.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        form.addView(Ui.text(this, tr(R.string.player_lens_help), 13, Ui.colors(this).muted));
        EditText cx = numeric(form, tr(R.string.player_center_x), projection.centerX),
                cy = numeric(form, tr(R.string.player_center_y), projection.centerY),
                r = numeric(form, tr(R.string.player_radius), projection.radius),
                rot = numeric(form, tr(R.string.player_rotation), projection.rotation);
        CheckBox mirror = new CheckBox(this);
        mirror.setText(tr(R.string.player_mirror));
        mirror.setChecked(projection.mirror);
        form.addView(mirror);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(tr(R.string.player_lens))
                        .setView(scroll)
                        .setNegativeButton(tr(R.string.player_cancel), null)
                        .setNeutralButton(
                                tr(R.string.player_defaults),
                                (d, w) -> {
                                    projection.centerX = projection.centerY = .5f;
                                    projection.radius = 1;
                                    projection.rotation = 0;
                                    projection.mirror = false;
                                    vr.requestRender();
                                    save();
                                })
                        .setPositiveButton(tr(R.string.player_apply), null)
                        .create();
        dialog.setOnShowListener(
                v ->
                        dialog.getButton(-1)
                                .setOnClickListener(
                                        b -> {
                                            try {
                                                float x = Float.parseFloat(cx.getText().toString()),
                                                        y =
                                                                Float.parseFloat(
                                                                        cy.getText().toString()),
                                                        radius =
                                                                Float.parseFloat(
                                                                        r.getText().toString()),
                                                        rotation =
                                                                Float.parseFloat(
                                                                        rot.getText().toString());
                                                if (!Float.isFinite(x)
                                                        || !Float.isFinite(y)
                                                        || !Float.isFinite(radius)
                                                        || !Float.isFinite(rotation)
                                                        || x < 0
                                                        || x > 1
                                                        || y < 0
                                                        || y > 1
                                                        || radius < .1
                                                        || radius > 2)
                                                    throw new IllegalArgumentException(
                                                            tr(R.string.player_lens_invalid));
                                                projection.centerX = x;
                                                projection.centerY = y;
                                                projection.radius = radius;
                                                projection.rotation = rotation;
                                                projection.mirror = mirror.isChecked();
                                                vr.requestRender();
                                                save();
                                                dialog.dismiss();
                                            } catch (Exception e) {
                                                error(e);
                                            }
                                        }));
        present(dialog);
    }

    private EditText numeric(LinearLayout form, String label, float value) {
        form.addView(Ui.text(this, label, 12, Ui.colors(this).muted));
        EditText e = new EditText(this);
        e.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER
                        | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setText(Float.toString(value));
        form.addView(e);
        return e;
    }

    private void favorites() {
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
        display(
                new AlertDialog.Builder(this)
                        .setTitle(tr(R.string.player_favorites))
                        .setItems(
                                items.toArray(new String[0]),
                                (d, i) -> {
                                    if (i == 0) {
                                        store.favorite(entryKey, !store.favorite(entryKey));
                                        Toast.makeText(
                                                        this,
                                                        tr(R.string.player_local_updated),
                                                        Toast.LENGTH_SHORT)
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
                                                                                                R
                                                                                                        .string
                                                                                                        .player_server_confirmed),
                                                                                        Toast
                                                                                                .LENGTH_SHORT)
                                                                                .show());
                                                    } catch (Exception e) {
                                                        runOnUiThread(() -> error(e));
                                                    }
                                                });
                                    }
                                }));
    }

    private void toggleGyro() {
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
        if (overlay != null) overlay.requestApplyInsets();
        adaptControls();
        resizeMore();
        int mode = c.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (mode != nightMode) {
            nightMode = mode;
            getTheme().rebase();
            for (Dialog d : new ArrayList<>(dialogs)) applyDialogPalette(d);
        }
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
        main.removeCallbacks(ticker);
        main.removeCallbacks(hideControls);
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
        for (Dialog d : new ArrayList<>(dialogs)) d.dismiss();
        main.removeCallbacks(hideControls);
        io.shutdownNow();
        if (vr != null) vr.release();
        super.onDestroy();
    }
}
