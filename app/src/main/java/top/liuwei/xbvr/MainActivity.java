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
import top.liuwei.xbvr.data.BitmapCoverRepository;
import top.liuwei.xbvr.data.DefaultLibraryRepository;
import top.liuwei.xbvr.data.ProfileJsonMapper;
import top.liuwei.xbvr.domain.CoverRatioPolicy;
import top.liuwei.xbvr.domain.LibraryFilterState;
import top.liuwei.xbvr.domain.LibraryRepository;
import top.liuwei.xbvr.domain.ProfileRepository;
import top.liuwei.xbvr.domain.ResourceIdentity;
import top.liuwei.xbvr.domain.ServerProfile;
import top.liuwei.xbvr.ui.library.GridScrollRestorer;
import top.liuwei.xbvr.ui.library.LibraryController;
import top.liuwei.xbvr.ui.library.LibraryUiState;
import top.liuwei.xbvr.ui.library.MainView;
import top.liuwei.xbvr.ui.library.PosterAdapter;
import static top.liuwei.xbvr.domain.Models.*;

public final class MainActivity extends Activity
        implements LibraryController.Listener, MainView.Actions {
    private Store store;
    private Api api;
    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private LibraryController library;
    private MainView view;
    private final ProfileRepository profileRepository =
            new ProfileRepository() {
                public List<ServerProfile> all() throws Exception {
                    return store.serverProfiles();
                }

                public ServerProfile current() throws Exception {
                    return store.currentProfile();
                }

                public ServerProfile find(String id) throws Exception {
                    return store.serverProfile(id);
                }

                public void save(ServerProfile value) throws Exception {
                    store.saveProfile(value);
                }

                public void remove(String id) throws Exception {
                    store.removeProfile(id);
                }

                public void select(String id) {
                    store.current(id);
                }
            };
    private final LibraryRepository libraryLoader =
            new LibraryRepository() {
                public LibraryRepository.Request load(boolean useCache, LibraryRepository.Observer observer) {
                    return new DefaultLibraryRepository(api, store, io, MainActivity.this::runOnUiThread)
                            .load(useCache, observer);
                }
            };
    private BitmapCoverRepository covers;
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
                    return store.position(store.playbackKey(api.id, e.url));
                }

                public boolean favorite(Entry e) {
                    return store.favorite(store.playbackKey(api.id, e.url));
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
                    inferCoverRatio(api, bitmap);
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
        store = new Store(this);
        library = new LibraryController(profileRepository, libraryLoader, store, store, store, this);
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
            ServerProfile p = store.currentProfile();
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

    private void open(ServerProfile profile, boolean reset) {
        api = new Api(ProfileJsonMapper.toJson(profile));
        // Covers are per server: a fresh repository drops the previous server's cache and problems.
        covers = new BitmapCoverRepository(api.client, io, this::runOnUiThread);
        view.setCovers(covers);
        coverInferenceQueued = false;
        view.setServerLabel(serverLabel());
        library.open(profile, reset);
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

    private void inferCoverRatio(Api requestApi, Bitmap bitmap) {
        LibraryUiState state = library.state();
        if (requestApi != api
                || state.coverMode != 0
                || state.coverInferred
                || bitmap == null
                || bitmap.getWidth() <= 0
                || bitmap.getHeight() <= 0) return;
        float ratio = (float) bitmap.getWidth() / bitmap.getHeight();
        state.coverInferred = true;
        if (state.profile != null) store.inferredRatio(state.profile.id, ratio);
        applyCoverRatio(ratio);
    }

    private void inferCachedCover(Bitmap bitmap) {
        LibraryUiState state = library.state();
        if (bitmap == null || state.coverMode != 0 || state.coverInferred || coverInferenceQueued)
            return;
        // Adapter binding runs inside layout; defer the one-time size change until it finishes.
        final Api requestApi = api;
        coverInferenceQueued = true;
        view.postToGrid(
                () -> {
                    coverInferenceQueued = false;
                    inferCoverRatio(requestApi, bitmap);
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
        return (api == null ? "" : api.id)
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
        i.putExtra("profile", api.id);
        startActivity(i);
    }

    @Override
    public boolean toggleFavorite(Entry e) {
        if (api == null) return false;
        String key = store.playbackKey(api.id, e.url);
        boolean value = !store.favorite(key);
        store.favorite(key, value);
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
        if (api == null) return tr(R.string.main_private_space);
        try {
            java.net.URI u = java.net.URI.create(api.base);
            return u.getHost()
                    + (u.getPort() < 0 ? "" : ":" + u.getPort())
                    + (u.getPath() == null ? "" : u.getPath());
        } catch (Exception e) {
            return api.base;
        }
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
        return api == null ? null : api.id;
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
        if (state.profile != null) store.mode(state.profile.id, index);
        if (index == 0) {
            state.coverInferred = false;
            if (state.profile != null) store.clearInferredRatio(state.profile.id);
            Bitmap first = null;
            for (Entry entry : state.visible) {
                first = covers == null ? null : covers.cached(posterKey(entry));
                if (first != null) break;
            }
            if (first == null) applyCoverRatio(CoverRatioPolicy.DEFAULT);
            else inferCoverRatio(api, first);
        } else applyCoverRatio(manualCoverRatio(index));
    }

    @Override
    public void showDiagnostics() {
        PlaybackDiagnostics.show(this);
    }

    @Override
    public void openLicenses() {
        startActivity(new Intent(this, LicensesActivity.class));
    }

    @Override
    public void refresh() {
        LibraryUiState state = library.state();
        if (api == null || state.busy || state.metadataBusy) return;
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
