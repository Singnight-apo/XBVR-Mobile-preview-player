package top.liuwei.xbvr.ui.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.media3.ui.SubtitleView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import top.liuwei.xbvr.R;
import top.liuwei.xbvr.ui.common.Ui;
import top.liuwei.xbvr.media.VrView;

/**
 * Owns the player layout: safe margins, transport and subtitle positioning, playback-button state,
 * the show/hide animation, the dialog palette and every open dialog. The GL surface itself is still
 * constructed and bound by the page; this view only places it. Android handlers and animations stay
 * here, never in Domain.
 */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlayerView {
    /** Everything in the player that is not presentation. */
    public interface Actions {
        void back();

        void rotate();

        void more();

        void togglePlayback();

        void jump(long delta);

        void formats();

        void files();

        void toggleGyro();

        void resetView();

        void seekPreview(int progress);

        void seekFinished(int progress);

        boolean hasPlayer();

        boolean isPlaying();
    }

    private final Activity activity;
    private final Actions actions;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Dialog> dialogs = new HashSet<>();

    private SubtitleView subtitle;
    private FrameLayout overlay;
    private TextView title, hint, time;
    private SeekBar seek;
    private ImageButton pause, gyroButton;
    private LinearLayout playerHeader, playerFooter, playerTransport;
    private int safeLeft, safeTop, safeRight, safeBottom;
    private boolean controlsShown = true, seeking, active;
    private String pauseIcon = "";
    private int nightMode;
    private Dialog moreDialog;

    public PlayerView(Activity activity, Actions actions) {
        this.activity = activity;
        this.actions = actions;
    }

    /** Builds the player content view and places the already-constructed GL surface in it. */
    public void build(VrView vr, String titleText) {
        nightMode = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.BLACK);
        activity.setContentView(root);
        root.addView(vr, new FrameLayout.LayoutParams(-1, -1));
        subtitle = new SubtitleView(activity);
        FrameLayout.LayoutParams sub = new FrameLayout.LayoutParams(-1, -1);
        sub.bottomMargin = Ui.dp(activity, 170);
        root.addView(subtitle, sub);
        overlay = new FrameLayout(activity);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        applyPlayerInsets();

        LinearLayout header = Ui.row(activity);
        playerHeader = header;
        header.setPadding(
                Ui.dp(activity, 8), Ui.dp(activity, 12), Ui.dp(activity, 8), Ui.dp(activity, 24));
        header.setBackground(
                new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[] {0xE6000000, 0x00000000}));
        header.addView(
                controlIcon("back", tr(R.string.player_back_library), actions::back),
                new LinearLayout.LayoutParams(Ui.dp(activity, 48), Ui.dp(activity, 48)));
        title = Ui.text(activity, titleText, 17, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(Ui.dp(activity, 8), 0, Ui.dp(activity, 8), 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(
                controlIcon("fullscreen", tr(R.string.player_rotate), actions::rotate),
                new LinearLayout.LayoutParams(Ui.dp(activity, 48), Ui.dp(activity, 48)));
        header.addView(
                controlIcon("more", tr(R.string.player_more), actions::more),
                new LinearLayout.LayoutParams(Ui.dp(activity, 48), Ui.dp(activity, 48)));
        overlay.addView(header, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        header.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> positionTransport());

        LinearLayout transport = Ui.row(activity);
        playerTransport = transport;
        transport.setGravity(Gravity.CENTER);
        ImageButton previous =
                controlIcon("previous", tr(R.string.player_rewind), () -> actions.jump(-10000));
        transport.addView(
                previous, new LinearLayout.LayoutParams(Ui.dp(activity, 56), Ui.dp(activity, 56)));
        pause = controlIcon("play", tr(R.string.player_play), actions::togglePlayback);
        pause.setPadding(
                Ui.dp(activity, 20),
                Ui.dp(activity, 20),
                Ui.dp(activity, 20),
                Ui.dp(activity, 20));
        pause.setImageTintList(ColorStateList.valueOf(0xFF142026));
        pause.setBackground(Ui.ripple(activity, 0xEEFFFFFF, 40));
        LinearLayout.LayoutParams big =
                new LinearLayout.LayoutParams(Ui.dp(activity, 76), Ui.dp(activity, 76));
        big.setMargins(Ui.dp(activity, 28), 0, Ui.dp(activity, 28), 0);
        transport.addView(pause, big);
        transport.addView(
                controlIcon("next", tr(R.string.player_forward), () -> actions.jump(10000)),
                new LinearLayout.LayoutParams(Ui.dp(activity, 56), Ui.dp(activity, 56)));
        overlay.addView(transport, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        LinearLayout footer = Ui.column(activity);
        playerFooter = footer;
        footer.setPadding(
                Ui.dp(activity, 18), Ui.dp(activity, 28), Ui.dp(activity, 18), Ui.dp(activity, 12));
        footer.setBackground(
                new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[] {0x00000000, 0xDA000000, 0xF5000000}));
        time = Ui.text(activity, "0:00 / 0:00", 12, 0xFFE2E8ED);
        time.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        footer.addView(time);
        seek = new SeekBar(activity);
        seek.setMax(10000);
        seek.setContentDescription(tr(R.string.player_progress));
        seek.setProgressTintList(ColorStateList.valueOf(0xFF71E0CB));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(0x66FFFFFF));
        seek.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        seek.setPadding(0, 0, 0, 0);
        footer.addView(seek, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 48)));
        LinearLayout quick = Ui.row(activity);
        quick.setGravity(Gravity.CENTER);
        addQuickIcon(quick, quickIcon("film", tr(R.string.player_format), actions::formats));
        addQuickIcon(quick, quickIcon("library", tr(R.string.player_files), actions::files));
        gyroButton = quickIcon("gyro", tr(R.string.player_gyro_enable), actions::toggleGyro);
        addQuickIcon(quick, gyroButton);
        addQuickIcon(quick, quickIcon("reset", tr(R.string.player_reset_view), actions::resetView));
        footer.addView(quick);
        hint = Ui.text(activity, tr(R.string.player_loading), 11, 0xFFB7C3CA);
        hint.setMaxLines(2);
        hint.setEllipsize(TextUtils.TruncateAt.END);
        hint.setPadding(0, Ui.dp(activity, 10), 0, 0);
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
                        seeking = true;
                        cancelHide();
                    }

                    public void onProgressChanged(SeekBar s, int p, boolean user) {
                        if (user) actions.seekPreview(p);
                    }

                    public void onStopTrackingTouch(SeekBar s) {
                        seeking = false;
                        actions.seekFinished(s.getProgress());
                        scheduleHide();
                    }
                });
        adaptControls();
    }

    private String tr(int id, Object... args) {
        return activity.getString(id, args);
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

    /** Re-applies the safe margins and compact layout for the current configuration. */
    public void adaptControls() {
        if (playerHeader == null || playerFooter == null) return;
        boolean compact = activity.getResources().getConfiguration().screenHeightDp < 480;
        playerHeader.setPadding(
                safeLeft + Ui.dp(activity, 8),
                safeTop + Ui.dp(activity, compact ? 6 : 12),
                safeRight + Ui.dp(activity, 8),
                Ui.dp(activity, compact ? 12 : 24));
        playerFooter.setPadding(
                safeLeft + Ui.dp(activity, 18),
                Ui.dp(activity, compact ? 12 : 28),
                safeRight + Ui.dp(activity, 18),
                safeBottom + Ui.dp(activity, compact ? 8 : 12));
        hint.setMaxLines(compact ? 1 : 2);
        overlay.post(this::positionTransport);
    }

    /** Centres the transport row and shrinks its buttons when the safe area is tight. */
    public void positionTransport() {
        if (playerTransport == null || playerFooter == null || overlay.getHeight() == 0) return;
        boolean compact = activity.getResources().getConfiguration().screenHeightDp < 480;
        int center = Ui.dp(activity, 76), side = Ui.dp(activity, 56);
        float offset = 0;
        if (compact) {
            offset = (playerHeader.getHeight() - playerFooter.getHeight()) / 2f;
            int available =
                    overlay.getHeight()
                            - overlay.getPaddingTop()
                            - overlay.getPaddingBottom()
                            - playerHeader.getHeight()
                            - playerFooter.getHeight();
            int fit = Math.max(Ui.dp(activity, 48), available - Ui.dp(activity, 8));
            center = Math.min(center, fit);
            side = Math.min(side, fit);
        }
        if (playerTransport.getTranslationY() != offset)
            playerTransport.setTranslationY(offset);
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
                    size == Ui.dp(activity, 76)
                            ? Ui.dp(activity, 20)
                            : Math.max(Ui.dp(activity, 10), (size - Ui.dp(activity, 28)) / 2);
            button.setPadding(padding, padding, padding, padding);
        }
    }

    private ImageButton controlIcon(String icon, String description, Runnable action) {
        ImageButton b =
                Ui.icon(
                        activity,
                        icon,
                        description,
                        () -> {
                            action.run();
                            scheduleHide();
                        });
        b.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        b.setBackground(Ui.ripple(activity, 0x18000000, 28));
        return b;
    }

    private ImageButton quickIcon(String icon, String description, Runnable action) {
        ImageButton b = controlIcon(icon, description, action);
        b.setTooltipText(description);
        b.setBackground(Ui.ripple(activity, 0x22FFFFFF, 24));
        return b;
    }

    private void addQuickIcon(LinearLayout row, ImageButton icon) {
        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(0, Ui.dp(activity, 48), 1);
        p.setMargins(Ui.dp(activity, 4), 0, Ui.dp(activity, 4), 0);
        row.addView(icon, p);
    }

    /** Shows or hides the overlay with the original 160 ms / 220 ms animation timings. */
    public void showControls(boolean show) {
        if (overlay == null) return;
        cancelHide();
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

    /** Flips the overlay; bound to the surface tap by the page. */
    public void toggleControls() {
        showControls(!controlsShown);
    }

    /** Keeps the subtitle block clear of the footer while the controls are visible. */
    public void positionSubtitles() {
        if (subtitle == null || overlay == null || playerFooter == null) return;
        int margin =
                controlsShown
                        ? Math.max(safeBottom + Ui.dp(activity, 140), playerFooter.getHeight())
                                + Ui.dp(activity, 12)
                        : safeBottom + Ui.dp(activity, 24);
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
        return ControlsVisibility.canHide(
                active,
                controlsShown,
                seeking,
                dialogs.size(),
                actions.hasPlayer(),
                actions.isPlaying());
    }

    /** Mirrors the page's onStart/onStop window. */
    public void setActive(boolean value) {
        active = value;
    }

    /** Removes any pending auto-hide; the 4200 ms delay is only re-armed by scheduleHide. */
    public void cancelHide() {
        main.removeCallbacks(hideControls);
    }

    public void scheduleHide() {
        cancelHide();
        if (canHide()) main.postDelayed(hideControls, 4200);
    }

    /** Reflects the player's play/pause icon and description. */
    public void updatePlayButton(boolean pauseShown) {
        if (pause == null) return;
        String icon = pauseShown ? "pause" : "play";
        if (!icon.equals(pauseIcon)) {
            pauseIcon = icon;
            pause.setImageDrawable(Ui.iconDrawable(icon, 0xFF142026));
            pause.setContentDescription(
                    tr(icon.equals("pause") ? R.string.player_pause : R.string.player_play));
        }
    }

    public void setTitle(String value) {
        if (title != null) title.setText(value);
    }

    public void setHint(String value) {
        if (hint != null) textIfChanged(hint, value);
    }

    /** Updates the status line and the gyro button state together. */
    public void setStatus(String status, boolean gyro) {
        if (hint == null) return;
        textIfChanged(hint, status);
        hint.setContentDescription(status);
        if (gyroButton != null) {
            String description =
                    tr(gyro ? R.string.player_gyro_disable : R.string.player_gyro_enable);
            gyroButton.setSelected(gyro);
            gyroButton.setContentDescription(description);
            gyroButton.setTooltipText(description);
            gyroButton.setImageTintList(
                    ColorStateList.valueOf(gyro ? 0xFFFFD54F : Color.WHITE));
            gyroButton.setBackground(Ui.ripple(activity, gyro ? 0x33FFD54F : 0x22FFFFFF, 24));
        }
    }

    public void setTime(String value) {
        if (time != null) textIfChanged(time, value);
    }

    public void setProgress(int progress) {
        if (seek != null) seek.setProgress(progress);
    }

    /** Forwards Media3 cues to the subtitle surface. */
    public void setCues(java.util.List<androidx.media3.common.text.Cue> cues) {
        if (subtitle != null) subtitle.setCues(cues);
    }

    /** True while the user drags the seek bar; the ticker must not fight the drag. */
    public boolean isSeeking() {
        return seeking;
    }

    private static void textIfChanged(TextView view, String value) {
        if (!value.contentEquals(view.getText())) view.setText(value);
    }

    /** Registers, shows and tracks a dialog; dismissals re-arm the auto-hide. */
    public <T extends Dialog> T present(T dialog) {
        dialogs.add(dialog);
        cancelHide();
        dialog.setOnDismissListener(
                d -> {
                    dialogs.remove(dialog);
                    scheduleHide();
                });
        dialog.show();
        applyDialogPalette(dialog);
        return dialog;
    }

    public AlertDialog display(AlertDialog.Builder builder) {
        return present(builder.create());
    }

    /** Presents the settings sheet and re-applies its width for the current window size. */
    public void presentMore(Dialog dialog) {
        moreDialog = dialog;
        present(dialog);
        resizeMore();
    }

    public void error(Throwable failure) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        display(
                new AlertDialog.Builder(activity)
                        .setTitle(tr(R.string.player_error_title))
                        .setMessage(Ui.errorMessage(activity, failure))
                        .setPositiveButton(tr(R.string.player_dismiss), null));
    }

    public void resizeMore() {
        if (moreDialog != null && moreDialog.isShowing()) {
            Window w = moreDialog.getWindow();
            if (w != null)
                w.setLayout(
                        activity.getResources().getConfiguration().screenWidthDp >= 600
                                ? Ui.dp(activity, 480)
                                : -1,
                        -2);
        }
    }

    public void applyDialogPalette(Dialog dialog) {
        Window w = dialog.getWindow();
        if (w == null) return;
        Ui.Palette c = Ui.colors(activity);
        dialog.getContext().getTheme().rebase();
        if (dialog instanceof AlertDialog) Ui.decorateDialog(activity, dialog);
        recolorDialog(w.getDecorView(), c);
    }

    private void recolorDialog(View v, Ui.Palette c) {
        if ("dialog-surface".equals(v.getTag())) v.setBackground(Ui.rounded(c.surface, 24, activity));
        else if ("handle".equals(v.getTag())) v.setBackground(Ui.rounded(c.border, 3, activity));
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
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) recolorDialog(group.getChildAt(i), c);
        }
    }

    /** Re-applies insets, compact layout, menu width and the dialog palette after a rotation. */
    public void onConfigurationChanged(Configuration configuration) {
        if (overlay != null) overlay.requestApplyInsets();
        adaptControls();
        resizeMore();
        int mode = configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (mode != nightMode) {
            nightMode = mode;
            activity.getTheme().rebase();
            for (Dialog d : new ArrayList<>(dialogs)) applyDialogPalette(d);
        }
    }

    /** Dismisses every open dialog; used when the page is destroyed. */
    public void dismissDialogs() {
        for (Dialog d : new ArrayList<>(dialogs)) d.dismiss();
    }
}
