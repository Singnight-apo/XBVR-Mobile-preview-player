package top.liuwei.xbvr.ui.library;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.UUID;
import top.liuwei.xbvr.Protocol;
import top.liuwei.xbvr.R;
import top.liuwei.xbvr.ui.common.Ui;
import top.liuwei.xbvr.domain.ServerProfile;

/**
 * Server menu, connection form and cover-ratio chooser as pure Android presentation. Persistence
 * goes through the typed {@link ServerProfile} model and the {@link MainView.Actions} callbacks; the
 * dialogs never read or write prefs or raw JSON themselves.
 */
public final class ServerDialogs {
    private final MainView view;
    private final Activity activity;

    ServerDialogs(MainView view) {
        this.view = view;
        this.activity = view.activity();
    }

    private View serverMenuAction(String title, String icon, Runnable action) {
        LinearLayout row = Ui.row(activity);
        row.setPadding(Ui.dp(activity, 14), 0, Ui.dp(activity, 14), 0);
        row.setMinimumHeight(Ui.dp(activity, 48));
        row.setBackground(Ui.ripple(activity, view.colors().surface, 14));
        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(Ui.dp(activity, 20), Ui.dp(activity, 20));
        iconParams.rightMargin = Ui.dp(activity, 12);
        if (icon != null) row.addView(view.glyph(icon, view.colors().muted, 20), iconParams);
        else row.addView(new View(activity), iconParams);
        TextView text = view.label(title, 14, view.colors().text);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        row.setContentDescription(title);
        row.setOnClickListener(v -> action.run());
        return row;
    }

    void servers() {
        try {
            List<ServerProfile> profiles = view.actions().profiles();
            LinearLayout list = Ui.column(activity);
            list.setPadding(
                    Ui.dp(activity, 16),
                    Ui.dp(activity, 6),
                    Ui.dp(activity, 16),
                    Ui.dp(activity, 12));
            ScrollView serverScroll = new ScrollView(activity);
            serverScroll.addView(list);
            AlertDialog dialog =
                    new AlertDialog.Builder(activity)
                            .setTitle(view.tr(R.string.main_your_library))
                            .setView(serverScroll)
                            .setNegativeButton(view.tr(R.string.main_close), null)
                            .create();
            String activeId = view.actions().activeProfileId();
            for (ServerProfile p : profiles) {
                boolean selected = activeId != null && activeId.equals(p.id);
                LinearLayout row = Ui.row(activity);
                row.setPadding(
                        Ui.dp(activity, 12),
                        Ui.dp(activity, 14),
                        Ui.dp(activity, 12),
                        Ui.dp(activity, 14));
                row.setMinimumHeight(Ui.dp(activity, 48));
                row.setBackground(
                        Ui.ripple(
                                activity,
                                selected ? view.colors().soft : view.colors().surface,
                                14));
                row.addView(
                        view.glyph(
                                "server",
                                selected ? view.colors().accent : view.colors().muted,
                                21));
                TextView name = view.label(p.base, 14, view.colors().text);
                name.setMaxLines(2);
                name.setPadding(Ui.dp(activity, 10), 0, Ui.dp(activity, 8), 0);
                row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
                if (selected) row.addView(view.glyph("check", view.colors().accent, 20));
                row.setOnClickListener(
                        v -> {
                            dialog.dismiss();
                            view.actions().openProfile(p, true);
                        });
                list.addView(row, view.spacing(-1, -2, 0, 0, 0, 8));
            }
            list.addView(
                    serverMenuAction(
                            view.tr(R.string.main_add_server),
                            "server",
                            () -> {
                                dialog.dismiss();
                                view.connection(null, null);
                            }),
                    view.spacing(-1, -2, 0, 0, 0, 8));
            if (activeId != null)
                list.addView(
                        serverMenuAction(
                                view.tr(R.string.main_cover_ratio),
                                "library",
                                () -> {
                                    dialog.dismiss();
                                    view.coverRatios();
                                }),
                        view.spacing(-1, -2, 0, 0, 0, 8));
            list.addView(
                    serverMenuAction(
                            view.tr(R.string.main_diagnostics),
                            "eye",
                            () -> {
                                dialog.dismiss();
                                view.actions().showDiagnostics();
                            }),
                    view.spacing(-1, -2, 0, 0, 0, 8));
            list.addView(
                    serverMenuAction(
                            view.tr(R.string.licenses_title),
                            null,
                            () -> {
                                dialog.dismiss();
                                view.actions().openLicenses();
                            }),
                    view.spacing(-1, -2, 0, 0, 0, 8));
            if (activeId != null)
                list.addView(
                        serverMenuAction(
                                view.tr(R.string.main_edit_current),
                                "settings",
                                () -> {
                                    dialog.dismiss();
                                    try {
                                        view.connection(view.actions().currentProfile(), null);
                                    } catch (Exception e) {
                                        Ui.error(activity, e);
                                    }
                                }),
                        view.spacing(-1, -2, 0, 0, 0, 8));
            view.track(dialog, 2);
            dialog.show();
            view.tintDialog(dialog);
        } catch (Exception e) {
            Ui.error(activity, e);
        }
    }

    private String draftValue(String oldValue, String[] draft, int index) {
        return draft != null ? draft[index] : oldValue;
    }

    void connection(ServerProfile old, String[] draft) {
        LinearLayout form = Ui.column(activity);
        form.setPadding(
                Ui.dp(activity, 20), Ui.dp(activity, 8), Ui.dp(activity, 20), Ui.dp(activity, 20));
        form.addView(view.label(view.tr(R.string.main_connection_help), 13, view.colors().muted));
        EditText address =
                view.field(
                        form,
                        view.tr(R.string.main_server_address),
                        view.tr(R.string.main_server_example),
                        draftValue(old == null ? "" : old.base, draft, 0),
                        false);
        address.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        EditText
                user =
                        view.field(
                                form,
                                view.tr(R.string.main_player_user),
                                view.tr(R.string.main_auth_optional),
                                draftValue(old == null ? "" : old.user, draft, 1),
                                false),
                pass =
                        view.field(
                                form,
                                view.tr(R.string.main_player_password),
                                view.tr(R.string.main_password),
                                draftValue(old == null ? "" : old.password, draft, 2),
                                true);
        TextView advanced = view.label(view.tr(R.string.main_proxy_expand), 13, view.colors().accent);
        advanced.setMinimumHeight(Ui.dp(activity, 48));
        advanced.setPadding(0, Ui.dp(activity, 18), 0, Ui.dp(activity, 4));
        advanced.setBackground(Ui.ripple(activity, view.colors().surface, 10));
        form.addView(advanced);
        LinearLayout extra = Ui.column(activity);
        EditText
                bu =
                        view.field(
                                extra,
                                view.tr(R.string.main_proxy_user),
                                view.tr(R.string.main_proxy_optional),
                                draftValue(old == null ? "" : old.basicUser, draft, 3),
                                false),
                bp =
                        view.field(
                                extra,
                                view.tr(R.string.main_proxy_password),
                                view.tr(R.string.main_password),
                                draftValue(old == null ? "" : old.basicPassword, draft, 4),
                                true);
        extra.setVisibility(
                !bu.getText().toString().isBlank() || !bp.getText().toString().isBlank()
                        ? View.VISIBLE
                        : View.GONE);
        advanced.setOnClickListener(
                v -> {
                    boolean show = extra.getVisibility() != View.VISIBLE;
                    extra.setVisibility(show ? View.VISIBLE : View.GONE);
                    advanced.setText(
                            show
                                    ? view.tr(R.string.main_proxy_collapse)
                                    : view.tr(R.string.main_proxy_expand));
                });
        form.addView(extra);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.addView(form);
        AlertDialog dialog =
                new AlertDialog.Builder(activity)
                        .setTitle(
                                old == null
                                        ? view.tr(R.string.main_connect_library)
                                        : view.tr(R.string.main_edit_server))
                        .setView(scroll)
                        .setNegativeButton(view.tr(R.string.main_cancel), null)
                        .setPositiveButton(view.tr(R.string.main_save_connect), null)
                        .create();
        dialog.setOnShowListener(
                v -> {
                    view.tintDialog(dialog);
                    dialog.getButton(-1)
                            .setOnClickListener(
                                    b -> {
                                        try {
                                            String base =
                                                    Protocol.base(address.getText().toString());
                                            ServerProfile p =
                                                    new ServerProfile(
                                                            old == null
                                                                    ? UUID.randomUUID().toString()
                                                                    : old.id,
                                                            base,
                                                            user.getText().toString(),
                                                            pass.getText().toString(),
                                                            bu.getText().toString(),
                                                            bp.getText().toString());
                                            view.actions().saveProfile(p);
                                            dialog.dismiss();
                                            view.actions().openProfile(p, true);
                                        } catch (Exception e) {
                                            address.setError(Ui.errorMessage(activity, e));
                                        }
                                    });
                });
        view.setConnectionDraft(old, new EditText[] {address, user, pass, bu, bp});
        view.track(dialog, 1);
        dialog.show();
    }

    void coverRatios() {
        if (view.actions().activeProfileId() == null) return;
        LibraryUiState state = view.library().state();
        String[] choices = {
            view.tr(R.string.main_cover_auto),
            view.tr(R.string.main_cover_square),
            view.tr(R.string.main_cover_three_two),
            view.tr(R.string.main_cover_wide)
        };
        AlertDialog dialog =
                new AlertDialog.Builder(activity)
                        .setTitle(view.tr(R.string.main_cover_ratio))
                        .setSingleChoiceItems(
                                choices,
                                state.coverMode,
                                (d, index) -> {
                                    view.actions().coverModeSelected(index);
                                    d.dismiss();
                                })
                        .setNegativeButton(view.tr(R.string.main_cancel), null)
                        .create();
        view.track(dialog, 4);
        dialog.show();
        view.tintDialog(dialog);
    }
}
