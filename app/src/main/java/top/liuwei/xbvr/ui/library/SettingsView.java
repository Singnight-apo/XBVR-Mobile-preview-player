package top.liuwei.xbvr.ui.library;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import top.liuwei.xbvr.R;
import top.liuwei.xbvr.domain.ServerProfile;
import top.liuwei.xbvr.ui.common.Ui;

/** Full-page settings, mounted above the existing library so its scroll/filter state survives. */
final class SettingsView {
    private final MainView view;
    private final Activity activity;
    private final SettingsNavigation navigation = new SettingsNavigation();
    private FrameLayout host;
    private View libraryPage;
    private TextView connectionStatus;
    private View retry;

    SettingsView(MainView view) { this.view = view; activity = view.activity(); }
    String savedPage() { return navigation.savedPage(); }
    boolean visible() { return navigation.page() != SettingsNavigation.Page.HOME; }
    void restore(String page) { navigation.restore(page); if (host != null) render(); }

    void attach(FrameLayout host, View libraryPage) {
        this.host = host;
        this.libraryPage = libraryPage;
        render();
    }

    void open(SettingsNavigation.Page page) {
        View focused = activity.getCurrentFocus();
        if (focused != null) {
            android.view.inputmethod.InputMethodManager keyboard = activity.getSystemService(
                    android.view.inputmethod.InputMethodManager.class);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(focused.getWindowToken(), 0);
            focused.clearFocus();
        }
        navigation.open(page);
        render();
    }

    boolean back() {
        if (!navigation.back()) return false;
        render();
        return true;
    }

    void refresh() { if (visible()) render(); }

    void updateStatus() {
        if (connectionStatus == null) return;
        LibraryUiState state = view.library().state();
        connectionStatus.setText(state.message == LibraryUiState.Message.NONE && !state.metadataBusy
                ? view.tr(R.string.settings_connected) : view.statusText(state));
        retry.setVisibility(state.failed && !state.busy ? View.VISIBLE : View.GONE);
    }

    private void render() {
        if (host == null) return;
        // Only the overlay is replaced; the library grid stays mounted and measured.
        while (host.getChildCount() > 1) host.removeViewAt(1);
        connectionStatus = null;
        retry = null;
        view.actions().settingsVisibilityChanged(visible());
        libraryPage.setImportantForAccessibility(visible()
                ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        libraryPage.setVisibility(visible() ? View.INVISIBLE : View.VISIBLE);
        if (!visible()) return;
        LinearLayout page = Ui.column(activity);
        page.setBackgroundColor(view.colors().bg);
        page.setClickable(true);
        page.setFocusable(true);
        Ui.insets(page);
        LinearLayout header = Ui.row(activity);
        header.setPadding(Ui.dp(activity, 8), Ui.dp(activity, 8), Ui.dp(activity, 16), Ui.dp(activity, 8));
        header.addView(Ui.icon(activity, "back", view.tr(R.string.settings_back), this::back));
        int title = navigation.page() == SettingsNavigation.Page.SETTINGS ? R.string.settings_title
                : navigation.page() == SettingsNavigation.Page.LIBRARY ? R.string.settings_library
                : R.string.settings_about;
        TextView heading = view.label(view.tr(title), 20, view.colors().text);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setAccessibilityHeading(true);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(header);
        page.addView(view.divider(), new LinearLayout.LayoutParams(-1, Ui.dp(activity, 1)));
        ScrollView scroll = new ScrollView(activity);
        LinearLayout body = Ui.column(activity);
        body.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 16), Ui.dp(activity, 20), Ui.dp(activity, 24));
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        host.addView(page, new FrameLayout.LayoutParams(-1, -1));
        // Newly mounted siblings do not always receive the already-dispatched window insets.
        if (host.getRootWindowInsets() != null)
            page.dispatchApplyWindowInsets(host.getRootWindowInsets());
        page.requestApplyInsets();
        try {
            switch (navigation.page()) {
                case SETTINGS: root(body); break;
                case LIBRARY: library(body); break;
                case ABOUT: about(body); break;
                default: break;
            }
        } catch (Exception e) {
            body.addView(view.label(Ui.errorMessage(activity, e), 14, view.colors().text));
            body.addView(Ui.button(activity, view.tr(R.string.main_refresh), this::refresh));
        }
    }

    private void root(LinearLayout body) throws Exception {
        int count = view.actions().profiles().size();
        String summary = view.actions().activeProfileId() == null ? view.tr(R.string.settings_no_servers)
                : view.actions().serverLabel();
        body.addView(action(view.tr(R.string.settings_library),
                summary + " · " + view.tr(R.string.settings_server_count, count), "server",
                () -> open(SettingsNavigation.Page.LIBRARY)), margin());
        body.addView(action(view.tr(R.string.main_diagnostics), view.tr(R.string.settings_diagnostics_hint),
                "eye", () -> view.actions().showDiagnostics()), margin());
        body.addView(action(view.tr(R.string.settings_about), view.tr(R.string.settings_about_hint),
                "film", () -> open(SettingsNavigation.Page.ABOUT)), margin());
    }

    private void library(LinearLayout body) throws Exception {
        section(body, R.string.settings_servers);
        List<ServerProfile> profiles = view.actions().profiles();
        String active = view.actions().activeProfileId();
        if (profiles.isEmpty()) body.addView(view.label(view.tr(R.string.settings_no_servers), 14, view.colors().muted), margin());
        for (ServerProfile profile : profiles) {
            boolean selected = profile.id.equals(active);
            LinearLayout card = Ui.row(activity);
            card.setPadding(Ui.dp(activity, 8), Ui.dp(activity, 6), Ui.dp(activity, 6), Ui.dp(activity, 6));
            card.setBackground(Ui.rounded(selected ? view.colors().soft : view.colors().surface, 16, activity));
            View select = action(profile.base,
                    view.tr(selected ? R.string.settings_current_server : R.string.settings_use_server),
                    selected ? "check" : "server", () -> {
                        if (!selected) { view.actions().openProfile(profile, true); refresh(); }
                    });
            select.setSelected(selected);
            if (selected) select.setBackground(Ui.ripple(activity, view.colors().soft, 14));
            card.addView(select, new LinearLayout.LayoutParams(0, -2, 1));
            View more = Ui.icon(activity, "more_vertical", view.tr(R.string.settings_server_actions, profile.base), () -> {});
            more.setOnClickListener(v -> {
                PopupMenu menu = new PopupMenu(activity, more);
                menu.getMenu().add(0, 1, 0, view.tr(R.string.main_edit_server));
                menu.getMenu().add(0, 2, 1, view.tr(R.string.main_remove_server));
                menu.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == 1) view.connection(profile, null);
                    else view.confirmRemoval(profile);
                    return true;
                });
                menu.show();
            });
            card.addView(more);
            body.addView(card, margin());
        }
        body.addView(Ui.button(activity, view.tr(R.string.main_add_server), () -> view.connection(null, null)), margin());
        if (active == null) return;
        section(body, R.string.settings_current_display);
        int[] modes = {R.string.main_cover_auto, R.string.main_cover_square, R.string.main_cover_three_two, R.string.main_cover_wide};
        int mode = Math.max(0, Math.min(3, view.library().state().coverMode));
        body.addView(action(view.tr(R.string.main_cover_ratio), view.tr(modes[mode]), "library", view::coverRatios), margin());
        body.addView(view.label(view.tr(R.string.settings_ratio_hint), 12, view.colors().muted), margin());
        section(body, R.string.settings_connection);
        connectionStatus = view.label("", 14, view.colors().muted);
        body.addView(connectionStatus, margin());
        retry = Ui.button(activity, view.tr(R.string.settings_retry), () -> view.actions().refresh());
        body.addView(retry, margin());
        body.addView(Ui.button(activity, view.tr(R.string.main_edit_current), () -> {
            try { view.connection(view.actions().currentProfile(), null); }
            catch (Exception e) { Ui.error(activity, e); }
        }), margin());
        updateStatus();
    }

    private void about(LinearLayout body) throws Exception {
        section(body, R.string.main_brand);
        String version = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
        body.addView(view.label(view.tr(R.string.settings_version, version), 14, view.colors().muted), margin());
        body.addView(action(view.tr(R.string.licenses_title), null, "library", () -> view.actions().openLicenses()), margin());
    }

    private void section(LinearLayout body, int title) {
        TextView label = view.label(view.tr(title), 13, view.colors().muted);
        label.setAccessibilityHeading(true);
        body.addView(label, view.spacing(-1, -2, 2, 12, 0, 12));
    }

    private LinearLayout.LayoutParams margin() { return view.spacing(-1, -2, 0, 0, 0, 10); }

    private View action(String title, String subtitle, String icon, Runnable action) {
        LinearLayout row = Ui.row(activity);
        row.setMinimumHeight(Ui.dp(activity, 64));
        row.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 12), Ui.dp(activity, 12), Ui.dp(activity, 12));
        row.setBackground(Ui.ripple(activity, view.colors().surface, 14));
        row.addView(view.glyph(icon, view.colors().accent, 22));
        LinearLayout text = Ui.column(activity);
        text.setPadding(Ui.dp(activity, 12), 0, Ui.dp(activity, 8), 0);
        TextView name = view.label(title, 15, view.colors().text);
        text.addView(name);
        if (subtitle != null) {
            TextView detail = view.label(subtitle, 12, view.colors().muted);
            detail.setPadding(0, Ui.dp(activity, 4), 0, 0);
            text.addView(detail);
        }
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(view.glyph("chevron", view.colors().muted, 16));
        row.setFocusable(true);
        row.setContentDescription(title + (subtitle == null ? "" : ", " + subtitle));
        row.setOnClickListener(v -> action.run());
        return row;
    }
}
