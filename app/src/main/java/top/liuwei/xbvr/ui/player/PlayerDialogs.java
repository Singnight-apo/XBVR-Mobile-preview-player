package top.liuwei.xbvr.ui.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import top.liuwei.xbvr.R;
import top.liuwei.xbvr.Ui;
import top.liuwei.xbvr.domain.Projection;

/**
 * The player's menus as pure Android presentation: the settings sheet, the format/eye/packing,
 * file, chapter, speed, track, lens and favourite choosers. Every business decision (which
 * projection to build, which file to select, what to persist) stays with the page and arrives here
 * as a callback; the dialogs never read prefs, JSON or the player themselves.
 */
public final class PlayerDialogs {
    /** Result of a single-choice menu. */
    public interface Pick {
        void pick(int index);
    }

    /** Applies validated lens values. */
    public interface Lens {
        void apply(float x, float y, float radius, float rotation, boolean mirror);
    }

    /** Business actions of the settings sheet. */
    public interface Menu {
        void chapters();

        void speed();

        void tracks();

        void lens();

        void favorites();

        void restart();
    }

    private final Activity activity;
    private final PlayerView view;

    public PlayerDialogs(Activity activity, PlayerView view) {
        this.activity = activity;
        this.view = view;
    }

    private String tr(int id, Object... args) {
        return activity.getString(id, args);
    }

    /** Builds and presents the settings sheet in its original group and item order. */
    public void more(Menu menu) {
        Ui.Palette c = Ui.colors(activity);
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = Ui.column(activity);
        panel.setTag("dialog-surface");
        panel.setPadding(
                Ui.dp(activity, 20), Ui.dp(activity, 14), Ui.dp(activity, 20), Ui.dp(activity, 20));
        panel.setBackground(Ui.rounded(c.surface, 24, activity));
        View handle = new View(activity);
        handle.setTag("handle");
        handle.setBackground(Ui.rounded(c.border, 3, activity));
        LinearLayout.LayoutParams hp =
                new LinearLayout.LayoutParams(Ui.dp(activity, 36), Ui.dp(activity, 4));
        hp.gravity = Gravity.CENTER;
        hp.bottomMargin = Ui.dp(activity, 16);
        panel.addView(handle, hp);
        LinearLayout heading = Ui.row(activity);
        TextView headingText = Ui.text(activity, tr(R.string.player_settings), 20, c.text);
        headingText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.addView(headingText, new LinearLayout.LayoutParams(0, -2, 1));
        heading.addView(
                Ui.icon(activity, "close", tr(R.string.player_close_settings), dialog::dismiss));
        panel.addView(heading);
        menuGroup(panel, tr(R.string.player_play), c);
        menuAction(panel, dialog, "clock", tr(R.string.player_chapters), menu::chapters, c);
        menuAction(panel, dialog, "play", tr(R.string.player_speed), menu::speed, c);
        menuAction(panel, dialog, "film", tr(R.string.player_tracks), menu::tracks, c);
        menuGroup(panel, tr(R.string.player_view_group), c);
        menuAction(panel, dialog, "settings", tr(R.string.player_lens), menu::lens, c);
        menuGroup(panel, tr(R.string.player_media_group), c);
        menuAction(panel, dialog, "heart", tr(R.string.player_favorites), menu::favorites, c);
        menuAction(panel, dialog, "previous", tr(R.string.player_restart), menu::restart, c);
        ScrollView scroll = new ScrollView(activity);
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
        view.presentMore(dialog);
    }

    private void menuGroup(LinearLayout panel, String label, Ui.Palette c) {
        TextView t = Ui.text(activity, label, 11, c.muted);
        t.setTag("muted");
        t.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 14), 0, Ui.dp(activity, 4));
        panel.addView(t);
    }

    private void menuAction(
            LinearLayout panel,
            Dialog dialog,
            String icon,
            String label,
            Runnable action,
            Ui.Palette c) {
        LinearLayout row = Ui.row(activity);
        row.setPadding(Ui.dp(activity, 12), 0, Ui.dp(activity, 12), 0);
        row.setMinimumHeight(Ui.dp(activity, 48));
        row.setBackground(Ui.ripple(activity, Color.TRANSPARENT, 14));
        ImageView image = new ImageView(activity);
        image.setImageDrawable(Ui.iconDrawable(icon, c.text));
        row.addView(image, new LinearLayout.LayoutParams(Ui.dp(activity, 22), Ui.dp(activity, 22)));
        TextView text = Ui.text(activity, label, 15, c.text);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.leftMargin = Ui.dp(activity, 14);
        row.addView(text, p);
        ImageView arrow = new ImageView(activity);
        arrow.setTag("muted");
        arrow.setImageDrawable(Ui.iconDrawable("chevron", c.muted));
        row.addView(arrow, new LinearLayout.LayoutParams(Ui.dp(activity, 18), Ui.dp(activity, 18)));
        row.setContentDescription(label);
        row.setOnClickListener(
                v -> {
                    dialog.dismiss();
                    action.run();
                    view.scheduleHide();
                });
        panel.addView(row);
    }

    /** Single-choice menu; the dialog closes before the choice is applied. */
    private void choose(String title, String[] labels, int current, Pick pick, boolean cancel) {
        AlertDialog.Builder builder =
                new AlertDialog.Builder(activity)
                        .setTitle(title)
                        .setSingleChoiceItems(
                                labels,
                                current,
                                (d, index) -> {
                                    d.dismiss();
                                    pick.pick(index);
                                });
        if (cancel) builder.setNegativeButton(tr(R.string.player_cancel), null);
        view.present(builder.create());
    }

    /** Plain list menu; Android dismisses it on the first click, as before. */
    private void list(String title, String[] labels, Pick pick, boolean cancel) {
        AlertDialog.Builder builder =
                new AlertDialog.Builder(activity)
                        .setTitle(title)
                        .setItems(labels, (d, index) -> pick.pick(index));
        if (cancel) builder.setNegativeButton(tr(R.string.player_cancel), null);
        view.present(builder.create());
    }

    public void formats(String title, String[] items, Pick pick) {
        list(title, items, pick, true);
    }

    public void projectionFormats(String title, String[] modes, Pick pick) {
        list(title, modes, pick, false);
    }

    public void layout(String title, String[] labels, int current, Pick pick) {
        choose(title, labels, current, pick, false);
    }

    public void packing(String title, String[] labels, int current, Pick pick) {
        choose(title, labels, current, pick, false);
    }

    public void viewingEye(String title, String[] choices, int current, Pick pick) {
        choose(title, choices, current, pick, true);
    }

    public void files(String title, String[] labels, int current, Pick pick) {
        choose(title, labels, current, pick, false);
    }

    public void chapters(String title, String[] labels, Pick pick) {
        list(title, labels, pick, false);
    }

    public void speed(String title, String[] labels, Pick pick) {
        list(title, labels, pick, false);
    }

    public void tracks(String title, String[] labels, List<Runnable> actions) {
        list(title, labels, index -> actions.get(index).run(), false);
    }

    public void favorites(String title, String[] items, Pick pick) {
        list(title, items, pick, false);
    }

    /**
     * Lens form with its original numeric parsing, finiteness checks, cx/cy 0..1, radius .1..2 and
     * the same invalid-value error path; the caller validates nothing itself.
     */
    public void lens(String title, Projection projection, Lens lens) {
        LinearLayout form = Ui.column(activity);
        form.setPadding(Ui.dp(activity, 16), 0, Ui.dp(activity, 16), 0);
        form.addView(
                Ui.text(activity, tr(R.string.player_lens_help), 13, Ui.colors(activity).muted));
        EditText cx = numeric(form, tr(R.string.player_center_x), projection.centerX),
                cy = numeric(form, tr(R.string.player_center_y), projection.centerY),
                r = numeric(form, tr(R.string.player_radius), projection.radius),
                rot = numeric(form, tr(R.string.player_rotation), projection.rotation);
        CheckBox mirror = new CheckBox(activity);
        mirror.setText(tr(R.string.player_mirror));
        mirror.setChecked(projection.mirror);
        form.addView(mirror);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);
        AlertDialog dialog =
                new AlertDialog.Builder(activity)
                        .setTitle(title)
                        .setView(scroll)
                        .setNegativeButton(tr(R.string.player_cancel), null)
                        .setNeutralButton(
                                tr(R.string.player_defaults),
                                (d, w) -> lens.apply(.5f, .5f, 1, 0, false))
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
                                                lens.apply(
                                                        x,
                                                        y,
                                                        radius,
                                                        rotation,
                                                        mirror.isChecked());
                                                dialog.dismiss();
                                            } catch (Exception e) {
                                                view.error(e);
                                            }
                                        }));
        view.present(dialog);
    }

    private EditText numeric(LinearLayout form, String label, float value) {
        form.addView(Ui.text(activity, label, 12, Ui.colors(activity).muted));
        EditText e = new EditText(activity);
        e.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER
                        | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setText(Float.toString(value));
        form.addView(e);
        return e;
    }
}
