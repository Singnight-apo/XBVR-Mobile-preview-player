package top.liuwei.xbvr.ui.library;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.List;
import top.liuwei.xbvr.R;
import top.liuwei.xbvr.ui.common.Ui;
import top.liuwei.xbvr.domain.CoverRepository;
import top.liuwei.xbvr.domain.LibraryFilterState;
import top.liuwei.xbvr.domain.Models.Entry;
import top.liuwei.xbvr.domain.ServerProfile;

/**
 * Owns the media-library layout and its dialogs. It reads {@link LibraryUiState} for the values and
 * the {@link Actions} interface for everything that is not presentation; it never touches Store,
 * prefs or JSON. The modal bookkeeping (kind, edited profile, five-field draft, facet kind) lives
 * here so a rotation can reopen the same dialog with the typed values.
 */
public final class MainView {
    /** Business actions the layout asks the page to perform. */
    public interface Actions {
        void play(Entry entry);

        /** @return whether the long press was consumed (no active server means it is not). */
        boolean toggleFavorite(Entry entry);

        void filter(boolean preserveScroll);

        void refresh();

        String serverLabel();

        /** Normalises a typed server address before it becomes a profile. */
        String normalizeBase(String value);

        List<ServerProfile> profiles() throws Exception;

        ServerProfile currentProfile() throws Exception;

        /** Selected server id, or {@code null} when no profile is open. */
        String activeProfileId();

        void saveProfile(ServerProfile profile) throws Exception;

        void openProfile(ServerProfile profile, boolean reset);

        /** Applies and persists a cover-ratio choice; index 0 is the automatic mode. */
        void coverModeSelected(int index);

        void showDiagnostics();

        void openLicenses();
    }

    public static final String CATEGORY_ALL = "全部";

    private final Activity activity;
    private final LibraryController library;
    private final PosterAdapter.EntryStatus statuses;
    private final PosterAdapter.Listener posterListener;
    private final Actions actions;
    private final ServerDialogs serverDialogs;
    private final FacetDialogs facetDialogs;

    private Ui.Palette colors;
    private boolean compact, binding;
    private int renderedTab = -1;
    private CoverRepository<Bitmap> covers;

    private GridView grid;
    private TextView status,
            serverName,
            sectionTitle,
            categoryLabel,
            resultCount,
            emptyTitle,
            emptyMessage;
    private EditText search;
    private ImageButton clearSearch, refresh;
    private PosterAdapter adapter;
    private LinearLayout empty, navigation, categoryPill;
    private ProgressBar loading;
    private LinearLayout facetRow, facetChips, chipsHost;
    private View chipsRule;
    private HorizontalScrollView chipsScroll;
    private LinearLayout stateRow, rail, railTitle;

    private AlertDialog activeDialog;
    private int modal;
    private ServerProfile editedProfile;
    private EditText[] connectionFields;
    private int facetKind;

    public MainView(
            Activity activity,
            LibraryController library,
            PosterAdapter.EntryStatus statuses,
            PosterAdapter.Listener posterListener,
            Actions actions) {
        this.activity = activity;
        this.library = library;
        this.statuses = statuses;
        this.posterListener = posterListener;
        this.actions = actions;
        this.serverDialogs = new ServerDialogs(this);
        this.facetDialogs = new FacetDialogs(this);
    }

    Activity activity() {
        return activity;
    }

    LibraryController library() {
        return library;
    }

    Actions actions() {
        return actions;
    }

    Ui.Palette colors() {
        return colors;
    }

    String tr(int id, Object... args) {
        return activity.getString(id, args);
    }

    String categoryText(String value) {
        return CATEGORY_ALL.equals(value) ? tr(R.string.main_all) : value;
    }

    /** True once the layout has been built and can be rendered into. */
    public boolean ready() {
        return status != null;
    }

    public GridView grid() {
        return grid;
    }

    PosterAdapter adapter() {
        return adapter;
    }

    /** Covers are per server: the page swaps the repository when it opens another profile. */
    public void setCovers(CoverRepository<Bitmap> value) {
        covers = value;
        if (adapter != null) adapter.setCovers(value);
    }

    public void setServerLabel(String value) {
        if (serverName != null) serverName.setText(value);
    }

    public void notifyAdapter() {
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    public void resetGridSelection() {
        if (grid != null) grid.setSelection(0);
    }

    public void requestGridLayout() {
        if (grid != null) grid.requestLayout();
    }

    public void postToGrid(Runnable action) {
        if (grid != null) grid.post(action);
    }

    // ---------------------------------------------------------------- modal bookkeeping

    public int modal() {
        return modal;
    }

    public ServerProfile editedProfile() {
        return editedProfile;
    }

    public int facetKind() {
        return facetKind;
    }

    public String[] connectionDraft() {
        if (connectionFields == null) return null;
        String[] draft = new String[connectionFields.length];
        for (int i = 0; i < draft.length; i++) draft[i] = connectionFields[i].getText().toString();
        return draft;
    }

    public void dismissDialog() {
        if (activeDialog != null) activeDialog.dismiss();
    }

    void setConnectionDraft(ServerProfile old, EditText[] fields) {
        editedProfile = old;
        connectionFields = fields;
    }

    void setFacetKind(int kind) {
        facetKind = kind;
    }

    void track(AlertDialog d, int kind) {
        activeDialog = d;
        modal = kind;
        d.setOnDismissListener(
                v -> {
                    if (activeDialog == d) {
                        activeDialog = null;
                        modal = 0;
                        connectionFields = null;
                    }
                });
    }

    // ---------------------------------------------------------------- layout helpers

    View divider() {
        View v = new View(activity);
        v.setBackgroundColor(colors.border);
        return v;
    }

    LinearLayout.LayoutParams spacing(
            int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.setMargins(
                Ui.dp(activity, left),
                Ui.dp(activity, top),
                Ui.dp(activity, right),
                Ui.dp(activity, bottom));
        return p;
    }

    TextView label(String value, int size, int color) {
        TextView t = Ui.text(activity, value, size, color);
        t.setFontFeatureSettings("kern");
        return t;
    }

    ImageView glyph(String name, int tint, int size) {
        ImageView v = new ImageView(activity);
        v.setImageDrawable(Ui.iconDrawable(name, tint));
        v.setLayoutParams(
                new LinearLayout.LayoutParams(Ui.dp(activity, size), Ui.dp(activity, size)));
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    FrameLayout filterCapsule(
            String caption, String description, int textSize, Runnable action) {
        FrameLayout target = new FrameLayout(activity);
        target.setFocusable(true);
        target.setContentDescription(description);
        target.setBackground(Ui.ripple(activity, Color.TRANSPARENT, 16));
        target.setOnClickListener(v -> action.run());
        Button face = Ui.button(activity, caption, action);
        face.setTextSize(textSize);
        face.setPadding(
                Ui.dp(activity, 7), Ui.dp(activity, 2), Ui.dp(activity, 7), Ui.dp(activity, 2));
        face.setMinHeight(0);
        face.setMinimumHeight(0);
        face.setBackground(Ui.ripple(activity, colors.soft, 16));
        face.setFocusable(false);
        face.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        target.addView(face, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        return target;
    }

    EditText field(
            LinearLayout form, String name, String hint, String value, boolean secret) {
        TextView title = label(name, 12, colors.muted);
        form.addView(title, spacing(-1, -2, 0, 13, 0, 7));
        EditText e = new EditText(activity);
        e.setTextColor(colors.text);
        e.setHintTextColor(colors.muted);
        e.setTextSize(14);
        e.setSingleLine();
        e.setBackground(Ui.rounded(colors.raised, 12, activity));
        e.setPadding(Ui.dp(activity, 12), 0, Ui.dp(activity, 12), 0);
        e.setHint(hint);
        e.setText(value);
        e.setContentDescription(name);
        e.setInputType(
                secret
                        ? android.text.InputType.TYPE_CLASS_TEXT
                                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                        : android.text.InputType.TYPE_CLASS_TEXT
                                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        form.addView(e, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 48)));
        return e;
    }

    void tintDialog(AlertDialog d) {
        for (int which : new int[] {-1, -2, -3}) {
            Button button = d.getButton(which);
            if (button != null) {
                button.setTextColor(colors.accent);
                button.setAllCaps(false);
            }
        }
        if (d.getWindow() != null)
            d.getWindow().setBackgroundDrawable(Ui.rounded(colors.surface, 24, activity));
    }

    // ---------------------------------------------------------------- build

    /** Rebuilds the whole page. The scroll position is captured and restored by the page. */
    public void build(CoverRepository<Bitmap> currentCovers) {
        covers = currentCovers;
        colors = Ui.colors(activity);
        Ui.edgeToEdge(activity, false);
        compact =
                activity.getResources().getConfiguration().orientation
                                == Configuration.ORIENTATION_LANDSCAPE
                        && activity.getResources().getConfiguration().screenHeightDp < 500;
        LinearLayout root = Ui.column(activity);
        root.setBackgroundColor(colors.bg);
        Ui.insets(root);
        activity.setContentView(root);
        LinearLayout header = Ui.row(activity);
        header.setPadding(
                Ui.dp(activity, 20), Ui.dp(activity, 8), Ui.dp(activity, 12), Ui.dp(activity, 6));
        FrameLayout mark = new FrameLayout(activity);
        mark.setBackground(Ui.rounded(colors.soft, 13, activity));
        ImageView film = glyph("film", colors.accent, 22);
        FrameLayout.LayoutParams fp =
                new FrameLayout.LayoutParams(
                        Ui.dp(activity, 22), Ui.dp(activity, 22), Gravity.CENTER);
        mark.addView(film, fp);
        header.addView(
                mark,
                new LinearLayout.LayoutParams(Ui.dp(activity, 36), Ui.dp(activity, 36)));
        LinearLayout titles = Ui.column(activity);
        titles.setPadding(Ui.dp(activity, 10), 0, 0, 0);
        TextView brand = label(tr(R.string.main_brand), 15, colors.text);
        brand.setSingleLine();
        brand.setEllipsize(TextUtils.TruncateAt.END);
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titles.addView(brand);
        serverName = label(actions.serverLabel(), 10, colors.muted);
        serverName.setSingleLine();
        serverName.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        titles.addView(serverName);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(
                Ui.icon(activity, "server", tr(R.string.main_choose_server), this::servers));
        if (!compact) root.addView(header);
        LinearLayout searchBar = Ui.row(activity);
        searchBar.setBackground(Ui.rounded(colors.surface, 16, activity));
        searchBar.setPadding(Ui.dp(activity, 14), 0, Ui.dp(activity, 4), 0);
        searchBar.addView(glyph("search", colors.muted, 21));
        search = new EditText(activity);
        search.setBackground(null);
        search.setSingleLine();
        search.setTextSize(15);
        search.setTextColor(colors.text);
        search.setHintTextColor(colors.muted);
        search.setHint(tr(R.string.main_search_media));
        search.setPadding(Ui.dp(activity, 10), 0, 0, 0);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setText(library.state().filter.query);
        search.setContentDescription(tr(R.string.main_search_media));
        searchBar.addView(search, new LinearLayout.LayoutParams(0, Ui.dp(activity, 50), 1));
        clearSearch =
                Ui.icon(
                        activity,
                        "close",
                        tr(R.string.main_clear_search),
                        () -> search.setText(""));
        clearSearch.setVisibility(
                library.state().filter.query.isEmpty() ? View.GONE : View.VISIBLE);
        searchBar.addView(clearSearch);
        if (compact) {
            header.setPadding(
                    Ui.dp(activity, 16),
                    Ui.dp(activity, 8),
                    Ui.dp(activity, 12),
                    Ui.dp(activity, 6));
            brand.setTextSize(15);
            serverName.setTextSize(9);
            rail = Ui.column(activity);
            rail.addView(header, new LinearLayout.LayoutParams(-1, -2));
            rail.addView(divider(), new LinearLayout.LayoutParams(-1, Ui.dp(activity, 1)));
            searchBar.removeAllViews();
            searchBar.setBackground(Ui.rounded(colors.surface, 14, activity));
            searchBar.setPadding(Ui.dp(activity, 10), 0, Ui.dp(activity, 2), 0);
            search.setBackground(null);
            search.setSingleLine();
            search.setTextSize(13);
            search.setTextColor(colors.text);
            search.setHintTextColor(colors.muted);
            search.setHint(tr(R.string.main_search_media));
            search.setPadding(Ui.dp(activity, 8), 0, 0, 0);
            search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
            search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
            search.setText(library.state().filter.query);
            search.setContentDescription(tr(R.string.main_search_media));
            searchBar.addView(glyph("search", colors.muted, 18));
            searchBar.addView(search, new LinearLayout.LayoutParams(0, Ui.dp(activity, 42), 1));
            clearSearch =
                    Ui.icon(
                            activity,
                            "close",
                            tr(R.string.main_clear_search),
                            () -> search.setText(""));
            clearSearch.setVisibility(
                    library.state().filter.query.isEmpty() ? View.GONE : View.VISIBLE);
            searchBar.addView(clearSearch);
            sectionTitle = label(tabTitle(), 17, colors.text);
            sectionTitle.setSingleLine();
            sectionTitle.setEllipsize(TextUtils.TruncateAt.END);
            sectionTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            railTitle = Ui.row(activity);
            railTitle.addView(sectionTitle, new LinearLayout.LayoutParams(0, -2, 1));
            rail.addView(railTitle, spacing(-1, Ui.dp(activity, 34), 14, 2, 14, 0));
            LinearLayout searchLine = Ui.row(activity);
            searchLine.addView(searchBar, new LinearLayout.LayoutParams(0, Ui.dp(activity, 42), 1));
            refresh =
                    Ui.icon(
                            activity,
                            "refresh",
                            tr(R.string.main_refresh),
                            () -> actions.refresh());
            searchLine.addView(
                    refresh,
                    spacing(Ui.dp(activity, 42), Ui.dp(activity, 42), 6, 0, 0, 0));
            rail.addView(searchLine, spacing(-1, Ui.dp(activity, 42), 14, 0, 14, 0));
            rail.addView(divider(), spacing(-1, Ui.dp(activity, 1), 14, 8, 14, 8));
        } else {
            LinearLayout top = Ui.row(activity);
            top.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 2), Ui.dp(activity, 12), 0);
            sectionTitle = label(tabTitle(), 15, colors.text);
            sectionTitle.setSingleLine();
            sectionTitle.setEllipsize(TextUtils.TruncateAt.END);
            sectionTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            sectionTitle.setGravity(Gravity.CENTER_VERTICAL);
            sectionTitle.setPadding(0, 0, Ui.dp(activity, 11), 0);
            sectionTitle.setMinWidth(Ui.dp(activity, 58));
            top.addView(sectionTitle);
            top.addView(searchBar, new LinearLayout.LayoutParams(0, Ui.dp(activity, 46), 1));
            refresh =
                    Ui.icon(
                            activity,
                            "refresh",
                            tr(R.string.main_refresh),
                            () -> actions.refresh());
            top.addView(refresh);
            root.addView(top);
        }
        search.setOnEditorActionListener(
                (v, action, event) -> {
                    if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                        ((InputMethodManager)
                                        activity.getSystemService(
                                                Activity.INPUT_METHOD_SERVICE))
                                .hideSoftInputFromWindow(search.getWindowToken(), 0);
                        search.clearFocus();
                        return true;
                    }
                    return false;
                });
        LinearLayout tools = Ui.row(activity);
        tools.setPadding(
                Ui.dp(activity, 20), Ui.dp(activity, 2), Ui.dp(activity, 20), Ui.dp(activity, 6));
        LinearLayout pill = Ui.row(activity);
        categoryPill = pill;
        pill.setPadding(
                Ui.dp(activity, 12), Ui.dp(activity, 8), Ui.dp(activity, 10), Ui.dp(activity, 8));
        pill.setBackground(Ui.ripple(activity, colors.surface, 18));
        pill.addView(glyph("filter", colors.accent, 15));
        categoryLabel = label(categoryText(library.state().filter.category), 12, colors.text);
        categoryLabel.setSingleLine();
        categoryLabel.setEllipsize(TextUtils.TruncateAt.END);
        categoryLabel.setMaxWidth(Ui.dp(activity, compact ? 120 : 160));
        categoryLabel.setPadding(Ui.dp(activity, 7), 0, Ui.dp(activity, 7), 0);
        pill.addView(categoryLabel);
        pill.addView(glyph("chevron", colors.muted, 13));
        pill.setMinimumHeight(Ui.dp(activity, 48));
        pill.setContentDescription(
                tr(
                        R.string.main_category_current,
                        categoryText(library.state().filter.category)));
        pill.setOnClickListener(v -> categories());
        HorizontalScrollView filterScroll = new HorizontalScrollView(activity);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filterLine = Ui.row(activity);
        filterLine.addView(pill, spacing(-2, Ui.dp(activity, 44), 0, 0, 6, 0));
        facetRow = Ui.row(activity);
        filterLine.addView(facetRow);
        facetChips = Ui.row(activity);
        if (!compact) filterLine.addView(facetChips);
        filterScroll.addView(filterLine);
        tools.addView(filterScroll, new LinearLayout.LayoutParams(0, Ui.dp(activity, 44), 1));
        resultCount = label("", 12, colors.muted);
        resultCount.setGravity(Gravity.END);
        resultCount.setSingleLine();
        resultCount.setPadding(Ui.dp(activity, 8), 0, 0, 0);
        resultCount.setTextColor(colors.accent);
        (compact ? railTitle : tools).addView(resultCount, new LinearLayout.LayoutParams(-2, -2));
        if (compact) {
            tools.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 0), Ui.dp(activity, 14), 0);
            rail.addView(tools, spacing(-1, Ui.dp(activity, 44), 14, 0, 14, 0));
            chipsRule = divider();
            rail.addView(chipsRule, spacing(-1, Ui.dp(activity, 1), 14, 12, 14, 0));
            chipsHost = Ui.row(activity);
            chipsScroll = new HorizontalScrollView(activity);
            chipsScroll.setHorizontalScrollBarEnabled(false);
            chipsScroll.setClipToPadding(false);
            chipsScroll.setPadding(0, 0, 0, 0);
            chipsScroll.addView(
                    chipsHost, new HorizontalScrollView.LayoutParams(-2, Ui.dp(activity, 48)));
            rail.addView(chipsScroll, spacing(-1, Ui.dp(activity, 48), 14, 2, 14, 0));
            rail.addView(new View(activity), new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            root.addView(tools);
            chipsHost = null;
            chipsRule = null;
            chipsScroll = null;
        }
        stateRow = Ui.row(activity);
        stateRow.setPadding(Ui.dp(activity, 20), 0, Ui.dp(activity, 20), Ui.dp(activity, 8));
        loading = new ProgressBar(activity);
        loading.setIndeterminateTintList(
                android.content.res.ColorStateList.valueOf(colors.accent));
        stateRow.addView(
                loading,
                new LinearLayout.LayoutParams(Ui.dp(activity, 14), Ui.dp(activity, 14)));
        status = label("", 11, colors.muted);
        status.setPadding(Ui.dp(activity, 7), 0, 0, 0);
        stateRow.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        FrameLayout body = new FrameLayout(activity);
        grid = new GridView(activity);
        grid.setNumColumns(columns());
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(Ui.dp(activity, 6));
        grid.setVerticalSpacing(Ui.dp(activity, 6));
        grid.setPadding(
                Ui.dp(activity, 8),
                Ui.dp(activity, 2),
                Ui.dp(activity, 8),
                Ui.dp(activity, 10));
        grid.setClipToPadding(false);
        grid.setVerticalScrollBarEnabled(false);
        grid.setSelector(Ui.rounded(Color.TRANSPARENT, 8, activity));
        adapter =
                new PosterAdapter(
                        activity,
                        library.state(),
                        colors,
                        covers,
                        library::generation,
                        statuses,
                        posterListener);
        grid.setAdapter(adapter);
        body.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        empty = Ui.column(activity);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(
                Ui.dp(activity, 36),
                Ui.dp(activity, 12),
                Ui.dp(activity, 36),
                Ui.dp(activity, 12));
        ImageView emptyFilm = glyph("library", colors.accent, 42);
        empty.addView(emptyFilm);
        emptyTitle = label("", 20, colors.text);
        emptyTitle.setGravity(Gravity.CENTER);
        empty.addView(emptyTitle, spacing(-1, -2, 0, 18, 0, 8));
        emptyMessage = label("", 14, colors.muted);
        emptyMessage.setGravity(Gravity.CENTER);
        emptyMessage.setLineSpacing(Ui.dp(activity, 4), 1);
        empty.addView(emptyMessage);
        Button connect = Ui.button(activity, tr(R.string.main_connect_server), () -> connection(null, null));
        empty.addView(connect, spacing(-2, Ui.dp(activity, 48), 0, 20, 0, 0));
        connect.setTag("connect");
        body.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout posterColumn = Ui.column(activity);
        posterColumn.addView(stateRow, new LinearLayout.LayoutParams(-1, -2));
        posterColumn.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        navigation = Ui.row(activity);
        navigation.setBackgroundColor(colors.bg);
        if (compact) {
            View navDivider = new View(activity);
            navDivider.setBackgroundColor(colors.border);
            rail.addView(navDivider, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 1)));
            navigation.setPadding(
                    Ui.dp(activity, 6), Ui.dp(activity, 2), Ui.dp(activity, 6), Ui.dp(activity, 2));
            navigation.setLayoutParams(new LinearLayout.LayoutParams(-1, Ui.dp(activity, 58)));
            rail.addView(navigation);
            LinearLayout split = Ui.row(activity);
            split.addView(rail, new LinearLayout.LayoutParams(Ui.dp(activity, 300), -1));
            View railDivider = new View(activity);
            railDivider.setBackgroundColor(colors.border);
            split.addView(railDivider, new LinearLayout.LayoutParams(Ui.dp(activity, 1), -1));
            split.addView(posterColumn, new LinearLayout.LayoutParams(0, -1, 1));
            activity.setContentView(split);
            Ui.playerInsets(split);
        } else {
            navigation.setPadding(
                    Ui.dp(activity, 8), Ui.dp(activity, 6), Ui.dp(activity, 8), Ui.dp(activity, 6));
            root.addView(posterColumn, new LinearLayout.LayoutParams(-1, 0, 1));
            View divider = new View(activity);
            divider.setBackgroundColor(colors.border);
            root.addView(divider, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 1)));
            root.addView(navigation);
            activity.setContentView(root);
        }
        buildNavigation();
        grid.setOnItemClickListener(
                (a, v, pos, id) -> {
                    List<Entry> visible = library.state().visible;
                    if (pos < visible.size()) actions.play(visible.get(pos));
                });
        grid.setOnItemLongClickListener(
                (a, v, pos, id) -> {
                    List<Entry> visible = library.state().visible;
                    if (pos >= visible.size()) return false;
                    return actions.toggleFavorite(visible.get(pos));
                });
        search.addTextChangedListener(
                new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

                    public void onTextChanged(CharSequence s, int st, int before, int c) {
                        if (binding) return;
                        library.state().filter.query = s.toString();
                        clearSearch.setVisibility(
                                library.state().filter.query.isEmpty()
                                        ? View.GONE
                                        : View.VISIBLE);
                        actions.filter(false);
                    }

                    public void afterTextChanged(Editable e) {}
                });
        renderedTab = library.state().filter.tab;
        updateFacets();
        updateState();
    }

    // The rail consumes real pixels, so the poster width must be measured in pixels as well.
    // Wider posters, closer to a wall-style gallery: fewer columns and tighter gutters let the
    // covers grow.
    private int columns() {
        int width =
                activity.getResources().getDisplayMetrics().widthPixels
                        - (compact ? Ui.dp(activity, 300) : 0);
        return Math.max(2, width / Ui.dp(activity, 190));
    }

    private String tabTitle() {
        int tab = library.state().filter.tab;
        return tab == 1
                ? tr(R.string.main_continue_watching)
                : tab == 2 ? tr(R.string.main_my_favorites) : tr(R.string.main_library);
    }

    void updateCategory() {
        LibraryFilterState filter = library.state().filter;
        categoryLabel.setText(categoryText(filter.category));
        categoryPill.setContentDescription(
                tr(R.string.main_category_current, categoryText(filter.category)));
    }

    public void buildNavigation() {
        navigation.removeAllViews();
        int tab = library.state().filter.tab;
        String[] icons = {"library", "clock", "heart"},
                labels =
                        {
                            tr(R.string.main_library),
                            tr(R.string.main_continue_watching),
                            tr(R.string.main_favorites)
                        };
        for (int i = 0; i < 3; i++) {
            final int next = i;
            LinearLayout item = Ui.column(activity);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, Ui.dp(activity, 5), 0, Ui.dp(activity, 4));
            item.setBackground(Ui.ripple(activity, i == tab ? colors.soft : colors.bg, 18));
            ImageView icon = glyph(icons[i], i == tab ? colors.accent : colors.muted, 23);
            item.addView(icon);
            TextView title = label(labels[i], 11, i == tab ? colors.accent : colors.muted);
            title.setSingleLine();
            title.setEllipsize(TextUtils.TruncateAt.END);
            item.addView(title, spacing(-2, -2, 0, 3, 0, 0));
            item.setContentDescription(labels[i]);
            item.setSelected(i == tab);
            item.setOnClickListener(
                    v -> {
                        if (library.state().filter.tab == next) return;
                        library.state().filter.tab = next;
                        actions.filter(false);
                    });
            navigation.addView(
                    item,
                    new LinearLayout.LayoutParams(
                            0, Ui.dp(activity, compact ? 54 : 57), 1));
        }
    }

    private void chip(String caption, String description, Runnable action) {
        FrameLayout b = filterCapsule(caption, description, 14, action);
        LinearLayout host = compact ? chipsHost : facetChips;
        if (host != null) host.addView(b, spacing(-2, Ui.dp(activity, 48), 0, 0, 6, 0));
    }

    public void updateFacets() {
        if (facetRow == null) return;
        LibraryFilterState f = library.state().filter;
        facetRow.removeAllViews();
        String[] names = {
            tr(R.string.main_studio), tr(R.string.main_actor), tr(R.string.main_tags)
        };
        for (int i = 0; i < 3; i++) {
            final int kind = i;
            String suffix =
                    i == 0
                            ? (f.studio.isEmpty() ? "" : " · 1")
                            : i == 1
                                    ? (f.actor.isEmpty() ? "" : " · 1")
                                    : (f.tags.isEmpty() ? "" : " · " + f.tags.size());
            FrameLayout b =
                    filterCapsule(
                            names[i] + suffix,
                            tr(R.string.main_choose_facet, names[i]),
                            14,
                            () -> facetDialog(kind));
            facetRow.addView(b, spacing(-2, Ui.dp(activity, 48), 0, 0, 6, 0));
        }
        facetChips.removeAllViews();
        if (chipsHost != null) chipsHost.removeAllViews();
        if (!f.studio.isEmpty())
            chip(
                    f.studio + " ×",
                    tr(R.string.main_remove_studio, f.studio),
                    () -> {
                        f.studio = "";
                        actions.filter(false);
                    });
        if (!f.actor.isEmpty())
            chip(
                    f.actor + " ×",
                    tr(R.string.main_remove_actor, f.actor),
                    () -> {
                        f.actor = "";
                        actions.filter(false);
                    });
        for (String tag : new java.util.ArrayList<>(f.tags))
            chip(
                    tag + " ×",
                    tr(R.string.main_remove_tag, tag),
                    () -> {
                        f.tags.remove(tag);
                        actions.filter(false);
                    });
        if (f.hasFacets())
            chip(
                    tr(R.string.main_clear_all),
                    tr(R.string.main_clear_all_filters),
                    () -> {
                        f.studio = "";
                        f.actor = "";
                        f.tags.clear();
                        actions.filter(false);
                    });
        boolean on = f.hasFacets();
        facetChips.setVisibility(on ? View.VISIBLE : View.GONE);
        if (chipsScroll != null) chipsScroll.setVisibility(on ? View.VISIBLE : View.GONE);
        if (chipsRule != null) chipsRule.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    public void syncFilterViews() {
        LibraryFilterState f = library.state().filter;
        if (search != null && !search.getText().toString().equals(f.query)) {
            binding = true;
            search.setText(f.query);
            binding = false;
        }
        if (clearSearch != null)
            clearSearch.setVisibility(f.query.isEmpty() ? View.GONE : View.VISIBLE);
        if (renderedTab != f.tab) {
            renderedTab = f.tab;
            if (sectionTitle != null) sectionTitle.setText(tabTitle());
            buildNavigation();
        }
        if (categoryLabel != null) updateCategory();
    }

    private String statusText(LibraryUiState state) {
        if (state.metadataBusy) return tr(R.string.main_metadata_loading);
        switch (state.message) {
            case METADATA_PARTIAL:
                return tr(R.string.main_metadata_partial);
            case METADATA_FAILED:
                return tr(R.string.main_metadata_failed);
            case CONNECTING:
                return tr(R.string.main_connecting);
            case CACHE_UPDATING:
                return tr(R.string.main_cache_updating);
            case CONNECTION_FAILED:
                return tr(R.string.main_connection_failed);
            case CACHED_OFFLINE:
                return tr(R.string.main_cached_offline);
            default:
                return state.profile == null
                        ? tr(R.string.main_status_intro)
                        : tr(R.string.main_status_play);
        }
    }

    public void updateState() {
        if (status == null) return;
        LibraryUiState state = library.state();
        LibraryFilterState f = state.filter;
        boolean note =
                state.message == LibraryUiState.Message.METADATA_PARTIAL
                        || state.message == LibraryUiState.Message.METADATA_FAILED;
        stateRow.setVisibility(
                state.busy || state.failed || state.metadataBusy || note
                        ? View.VISIBLE
                        : View.GONE);
        status.setText(statusText(state));
        loading.setVisibility(state.busy || state.metadataBusy ? View.VISIBLE : View.GONE);
        refresh.setAlpha(state.busy ? .4f : 1);
        refresh.setEnabled(state.profile != null && !state.busy && !state.metadataBusy);
        resultCount.setText(tr(R.string.main_item_count, state.visible.size()));
        boolean no = state.visible.isEmpty();
        grid.setVisibility(no ? View.GONE : View.VISIBLE);
        empty.setVisibility(no ? View.VISIBLE : View.GONE);
        View connect = empty.findViewWithTag("connect");
        if (!no) return;
        if (state.profile == null) {
            emptyTitle.setText(tr(R.string.main_empty_intro_title));
            emptyMessage.setText(tr(R.string.main_empty_intro));
            connect.setVisibility(View.VISIBLE);
        } else {
            connect.setVisibility(View.GONE);
            if (state.busy || state.metadataBusy) {
                emptyTitle.setText(tr(R.string.main_empty_loading_title));
                emptyMessage.setText(tr(R.string.main_empty_loading));
            } else if (!f.query.isEmpty()) {
                emptyTitle.setText(tr(R.string.main_empty_search_title));
                emptyMessage.setText(tr(R.string.main_empty_search));
            } else if (f.hasFacets()) {
                emptyTitle.setText(tr(R.string.main_empty_filters_title));
                emptyMessage.setText(tr(R.string.main_empty_filters));
            } else if (state.failed) {
                emptyTitle.setText(tr(R.string.main_empty_error_title));
                emptyMessage.setText(tr(R.string.main_empty_error));
            } else if (f.tab == 1) {
                emptyTitle.setText(tr(R.string.main_empty_continue_title));
                emptyMessage.setText(tr(R.string.main_empty_continue));
            } else if (f.tab == 2) {
                emptyTitle.setText(tr(R.string.main_empty_favorites_title));
                emptyMessage.setText(tr(R.string.main_empty_favorites));
            } else {
                emptyTitle.setText(tr(R.string.main_empty_category_title));
                emptyMessage.setText(tr(R.string.main_empty_category));
            }
        }
    }

    // ---------------------------------------------------------------- dialogs

    public void servers() {
        serverDialogs.servers();
    }

    public void connection(ServerProfile old, String[] draft) {
        serverDialogs.connection(old, draft);
    }

    public void coverRatios() {
        serverDialogs.coverRatios();
    }

    public void categories() {
        facetDialogs.categories();
    }

    public void facetDialog(int kind) {
        facetDialogs.facetDialog(kind);
    }
}
