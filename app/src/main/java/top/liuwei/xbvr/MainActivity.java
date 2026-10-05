package top.liuwei.xbvr;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.text.*;
import java.util.*;
import java.util.concurrent.*;
import top.liuwei.xbvr.domain.CoverRatioPolicy;
import top.liuwei.xbvr.domain.CoverRepository;
import top.liuwei.xbvr.domain.CoverSettings;
import top.liuwei.xbvr.domain.FavoriteRepository;
import top.liuwei.xbvr.domain.LibraryFilterState;
import top.liuwei.xbvr.domain.LibraryRepository;
import top.liuwei.xbvr.domain.PlaybackRepository;
import top.liuwei.xbvr.domain.ProfileRepository;
import top.liuwei.xbvr.domain.ResourceIdentity;
import top.liuwei.xbvr.domain.ServerProfile;
import top.liuwei.xbvr.ui.library.GridScrollRestorer;
import top.liuwei.xbvr.ui.library.LibraryController;
import top.liuwei.xbvr.ui.library.LibraryUiState;
import top.liuwei.xbvr.ui.library.MainView;
import top.liuwei.xbvr.ui.library.PosterAdapter;
import top.liuwei.xbvr.ui.common.Ui;
import top.liuwei.xbvr.ui.diagnostics.DiagnosticsDialog;
import static top.liuwei.xbvr.domain.Models.*;

public final class MainActivity extends Activity
        implements LibraryController.Listener, MainView.Actions {
    private AppServices services;
    private ProfileRepository profileRepository;
    private PlaybackRepository playback;
    private FavoriteRepository favorites;
    private CoverSettings coverSettings;
    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private LibraryController library;
    private MainView view;
    private AppServices.ForProfile connection;
    private ServerProfile profile;
    private final LibraryRepository libraryLoader =
            (useCache, observer) ->
                    connection
                            .library(io, MainActivity.this::runOnUiThread)
                            .load(useCache, observer);
    private CoverRepository<Bitmap> covers;
    private boolean coverInferenceQueued;
    private GridScrollRestorer.ScrollTarget returnAnchor, restorationAnchor, pendingAnchor;
    private LibraryUiState.Message lastMessage;

    private final GridScrollRestorer scroll =
            new GridScrollRestorer(
                    new GridScrollRestorer.Page() {
                        public GridView grid() {
                            return view == null ? null : view.grid();
                        }

                        public List<Entry> visible() {
                            return library.state().visible;
                        }

                        public boolean isDestroyed() {
                            return MainActivity.this.isDestroyed();
                        }
                    });

    private final PosterAdapter.EntryStatus statuses =
            new PosterAdapter.EntryStatus() {
                public String posterKey(Entry e) {
                    return MainActivity.this.posterKey(e);
                }

                public long resume(Entry e) {
                    return profile == null
                            ? 0
                            : playback.position(
                                    ResourceIdentity.playbackKey(profile.id, e.url));
                }

                public boolean favorite(Entry e) {
                    return profile != null
                            && favorites.favorite(
                                    ResourceIdentity.playbackKey(profile.id, e.url));
                }
            };

    private final PosterAdapter.Listener posterActions =
            new PosterAdapter.Listener() {
                public void credit(String value, boolean studio) {
                    LibraryFilterState f = library.state().filter;
                    if (studio) f.studio = f.studio.equals(value) ? "" : value;
                    else f.actor = f.actor.equals(value) ? "" : value;
                    filter(false);
                }

                public void coverCached(Bitmap bitmap) {
                    inferCachedCover(bitmap);
                }

                public void coverDecoded(Bitmap bitmap) {
                    inferCoverRatio(connection, bitmap);
                }

                public void coverRetry() {
                    GridScrollRestorer.ScrollTarget anchor = scroll.capture();
                    view.notifyAdapter();
                    scroll.restore(anchor);
                }
            };

    private String tr(int id, Object... args) {
        return getString(id, args);
    }

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        services = new AppServices(this);
        profileRepository = services.profiles();
        playback = services.playback();
        favorites = services.favorites();
        coverSettings = services.coverSettings();
        library =
                new LibraryController(
                        profileRepository, libraryLoader, playback, favorites, coverSettings, this);
        view = new MainView(this, library, statuses, posterActions, this);
        if (saved != null) {
            LibraryFilterState filter = library.state().filter;
            filter.query = saved.getString("query", "");
            filter.category = saved.getString("category", MainView.CATEGORY_ALL);
            filter.tab = saved.getInt("tab", 0);
            filter.studio = saved.getString("studioFilter", "");
            filter.actor = saved.getString("actorFilter", "");
            ArrayList<String> savedTags = saved.getStringArrayList("tagFilters");
            if (savedTags != null) filter.tags.addAll(savedTags);
            restorationAnchor =
                    new GridScrollRestorer.ScrollTarget(
                            saved.getString("anchor", ""),
                            saved.getInt("first", 0),
                            saved.getInt("offset", 0),
                            saved.getParcelable("gridState"));
        }
        build();
        try {
            ServerProfile p = profileRepository.current();
            if (p == null) connection(null);
            else open(p, saved == null);
        } catch (Exception e) {
            Ui.error(this, e);
            connection(null);
        }
    }

    private void build() {
        scroll.cancel();
        view.build(covers);
    }

    void servers() {
        view.servers();
    }

    void connection(ServerProfile old) {
        connection(old, null);
    }

    void connection(ServerProfile old, String[] draft) {
        view.connection(old, draft);
    }

    void categories() {
        view.categories();
    }

    void coverRatios() {
        view.coverRatios();
    }

    void facetDialog(int kind) {
        view.facetDialog(kind);
    }

    private void open(ServerProfile value, boolean reset) {
        connection = services.forProfile(value);
        profile = value;
        // Covers are per server: a fresh repository drops the previous server's cache and problems.
        covers = connection.covers(io, this::runOnUiThread);
        view.setCovers(covers);
        coverInferenceQueued = false;
        view.setServerLabel(serverLabel());
        library.open(value, reset);
    }

    private float manualCoverRatio(int mode) {
        return CoverRatioPolicy.fixed(mode);
    }

    private void applyCoverRatio(float ratio) {
        LibraryUiState state = library.state();
        if (!CoverRatioPolicy.changed(state.coverRatio, ratio)) return;
        GridScrollRestorer.ScrollTarget anchor = scroll.capture();
        state.coverRatio = ratio;
        view.notifyAdapter();
        view.requestGridLayout();
        scroll.restore(anchor);
    }

    private void inferCoverRatio(AppServices.ForProfile request, Bitmap bitmap) {
        LibraryUiState state = library.state();
        if (request != connection
                || state.coverMode != 0
                || state.coverInferred
                || bitmap == null
                || bitmap.getWidth() <= 0
                || bitmap.getHeight() <= 0) return;
        float ratio = (float) bitmap.getWidth() / bitmap.getHeight();
        state.coverInferred = true;
        if (state.profile != null) coverSettings.inferredRatio(state.profile.id, ratio);
        applyCoverRatio(ratio);
    }

    private void inferCachedCover(Bitmap bitmap) {
        LibraryUiState state = library.state();
        if (bitmap == null || state.coverMode != 0 || state.coverInferred || coverInferenceQueued)
            return;
        // Adapter binding runs inside layout; defer the one-time size change until it finishes.
        final AppServices.ForProfile request = connection;
        coverInferenceQueued = true;
        view.postToGrid(
                () -> {
                    coverInferenceQueued = false;
                    inferCoverRatio(request, bitmap);
                });
    }

    @Override
    public void filter(boolean preserve) {
        pendingAnchor = preserve ? scroll.capture() : null;
        try {
            library.filterChanged(library.state().filter, preserve);
        } finally {
            pendingAnchor = null;
        }
    }

    private String posterKey(Entry e) {
        return (profile == null ? "" : profile.id)
                + ":"
                + ResourceIdentity.of(e.url)
                + ":"
                + e.poster
                + ":"
                + e.posterCandidates.hashCode();
    }

    @Override
    public void changed(LibraryUiState state, boolean keepScroll) {
        if (!view.ready()) return;
        retryFailedCovers(state);
        if (keepScroll) {
            GridScrollRestorer.ScrollTarget anchor =
                    pendingAnchor != null
                            ? pendingAnchor
                            : restorationAnchor != null ? restorationAnchor : scroll.capture();
            if (pendingAnchor == null
                    && restorationAnchor != null
                    && !state.entries.isEmpty()) restorationAnchor = null;
            view.notifyAdapter();
            view.resetGridSelection();
            scroll.restore(anchor);
        } else {
            scroll.cancel();
            view.notifyAdapter();
            view.resetGridSelection();
        }
        view.syncFilterViews();
        view.updateFacets();
        view.updateState();
    }

    @Override
    public void connectionFailed(Throwable failure) {
        Ui.error(this, failure);
    }

    private void retryFailedCovers(LibraryUiState state) {
        boolean loaded =
                state.message == LibraryUiState.Message.NONE
                        || state.message == LibraryUiState.Message.METADATA_PARTIAL;
        if (loaded && lastMessage != state.message && covers != null) covers.retryAll();
        lastMessage = state.message;
    }

    @Override
    public void play(Entry e) {
        returnAnchor = scroll.capture();
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("url", e.url);
        i.putExtra("title", e.title);
        i.putExtra("profile", profile.id);
        startActivity(i);
    }

    @Override
    public boolean toggleFavorite(Entry e) {
        if (profile == null) return false;
        String key = ResourceIdentity.playbackKey(profile.id, e.url);
        boolean value = !favorites.favorite(key);
        favorites.favorite(key, value);
        Toast.makeText(
                        this,
                        value ? tr(R.string.main_favorite_added) : tr(R.string.main_favorite_removed),
                        Toast.LENGTH_SHORT)
                .show();
        filter(true);
        return true;
    }

    @Override
    public String serverLabel() {
        if (profile == null) return tr(R.string.main_private_space);
        try {
            java.net.URI u = java.net.URI.create(profile.base);
            return u.getHost()
                    + (u.getPort() < 0 ? "" : ":" + u.getPort())
                    + (u.getPath() == null ? "" : u.getPath());
        } catch (Exception e) {
            return profile.base;
        }
    }

    @Override
    public String normalizeBase(String value) {
        return services.normalizeBase(value);
    }

    @Override
    public List<ServerProfile> profiles() throws Exception {
        return profileRepository.all();
    }

    @Override
    public ServerProfile currentProfile() throws Exception {
        return profileRepository.current();
    }

    @Override
    public String activeProfileId() {
        return profile == null ? null : profile.id;
    }

    @Override
    public void saveProfile(ServerProfile profile) throws Exception {
        profileRepository.save(profile);
    }

    @Override
    public void openProfile(ServerProfile profile, boolean reset) {
        open(profile, reset);
    }

    @Override
    public void coverModeSelected(int index) {
        LibraryUiState state = library.state();
        state.coverMode = index;
        if (state.profile != null) coverSettings.mode(state.profile.id, index);
        if (index == 0) {
            state.coverInferred = false;
            if (state.profile != null) coverSettings.clearInferredRatio(state.profile.id);
            Bitmap first = null;
            for (Entry entry : state.visible) {
                first = covers == null ? null : covers.cached(posterKey(entry));
                if (first != null) break;
            }
            if (first == null) applyCoverRatio(CoverRatioPolicy.DEFAULT);
            else inferCoverRatio(connection, first);
        } else applyCoverRatio(manualCoverRatio(index));
    }

    @Override
    public void showDiagnostics() {
        DiagnosticsDialog.show(this, services.diagnosticsReport());
    }

    @Override
    public void openLicenses() {
        startActivity(new Intent(this, LicensesActivity.class));
    }

    @Override
    public void refresh() {
        LibraryUiState state = library.state();
        if (profile == null || state.busy || state.metadataBusy) return;
        if (covers != null) covers.clear();
        library.refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (view.ready()) {
            GridScrollRestorer.ScrollTarget anchor =
                    returnAnchor == null ? scroll.capture() : returnAnchor;
            filter(false);
            scroll.restore(anchor);
            returnAnchor = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        LibraryFilterState filter = library.state().filter;
        out.putString("query", filter.query);
        out.putString("category", filter.category);
        out.putInt("tab", filter.tab);
        out.putString("studioFilter", filter.studio);
        out.putString("actorFilter", filter.actor);
        out.putStringArrayList("tagFilters", new ArrayList<>(filter.tags));
        GridScrollRestorer.ScrollTarget anchor = scroll.capture();
        out.putString("anchor", anchor.url);
        out.putInt("first", anchor.index);
        out.putInt("offset", anchor.top);
        out.putParcelable("gridState", anchor.nativeState);
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        int reopen = view.modal();
        ServerProfile old = view.editedProfile();
        String[] draft = null;
        if (reopen == 1) draft = view.connectionDraft();
        view.dismissDialog();
        getTheme().rebase();
        GridScrollRestorer.ScrollTarget anchor = scroll.capture();
        build();
        scroll.restore(anchor);
        if (reopen == 1) connection(old, draft);
        else if (reopen == 2) servers();
        else if (reopen == 3) categories();
        else if (reopen == 4) coverRatios();
        else if (reopen == 5) facetDialog(view.facetKind());
    }

    @Override
    public void onDestroy() {
        if (library != null) library.close();
        scroll.cancel();
        if (view != null) view.dismissDialog();
        if (covers != null) covers.clear();
        io.shutdownNow();
        super.onDestroy();
    }
}
