package top.liuwei.xbvr.ui.library;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import top.liuwei.xbvr.domain.CoverRatioPolicy;
import top.liuwei.xbvr.domain.CoverSettings;
import top.liuwei.xbvr.domain.EntryMetadata;
import top.liuwei.xbvr.domain.FavoriteRepository;
import top.liuwei.xbvr.domain.LibraryEvent;
import top.liuwei.xbvr.domain.LibraryFilterState;
import top.liuwei.xbvr.domain.LibraryQuery;
import top.liuwei.xbvr.domain.LibraryRepository;
import top.liuwei.xbvr.domain.Models.Entry;
import top.liuwei.xbvr.domain.PlaybackRepository;
import top.liuwei.xbvr.domain.ProfileRepository;
import top.liuwei.xbvr.domain.ResourceIdentity;
import top.liuwei.xbvr.domain.ServerProfile;

/**
 * Coordinates the library page state: it owns the load generation, the cached-directory-metadata
 * events, the filter/tab selection and the ready/error status. It observes no Android type and no
 * Bitmap; the page listener receives the state plus whether the scroll position should survive.
 */
public final class LibraryController {
    public interface Listener {
        void changed(LibraryUiState state, boolean keepScroll);

        void connectionFailed(Throwable failure);
    }

    private final ProfileRepository profiles;
    private final LibraryRepository library;
    private final PlaybackRepository playback;
    private final FavoriteRepository favorites;
    private final CoverSettings covers;
    private final Listener listener;
    private final LibraryUiState state = new LibraryUiState();
    private volatile int generation;
    private LibraryRepository.Request request;

    public LibraryController(
            ProfileRepository profiles,
            LibraryRepository library,
            PlaybackRepository playback,
            FavoriteRepository favorites,
            CoverSettings covers,
            Listener listener) {
        this.profiles = profiles;
        this.library = library;
        this.playback = playback;
        this.favorites = favorites;
        this.covers = covers;
        this.listener = listener;
    }

    public LibraryUiState state() {
        return state;
    }

    /** Cover observer epoch; the page uses it to drop late image callbacks. */
    public int generation() {
        return generation;
    }

    public void open(ServerProfile profile, boolean reset) {
        generation++;
        boolean changed = state.profile != null && !state.profile.id.equals(profile.id);
        if (reset || changed) state.filter.clearFacets();
        state.profile = profile;
        state.busy = false;
        state.metadataBusy = false;
        state.failed = false;
        state.message = LibraryUiState.Message.NONE;
        profiles.select(profile.id);
        readCoverSettings();
        state.entries.clear();
        if (reset) {
            state.filter.category = "全部";
            state.filter.query = "";
            state.filter.tab = 0;
        }
        filter(false);
        load(true);
    }

    public void refresh() {
        load(false);
    }

    /** Removes only the connection. Caller opens the returned fallback after rebinding services. */
    public ServerProfile removeProfile(String id) throws Exception {
        List<ServerProfile> remaining = new java.util.ArrayList<>(profiles.all());
        if (!remaining.removeIf(p -> p.id.equals(id))) return state.profile;
        // Persist first: a storage failure must leave the page and request untouched.
        profiles.remove(id);
        if (state.profile == null || !state.profile.id.equals(id)) return state.profile;
        ServerProfile next = remaining.isEmpty() ? null : remaining.get(0);
        profiles.select(next == null ? "" : next.id);
        close();
        state.profile = null;
        state.entries.clear();
        state.visible.clear();
        state.filter.clearFacets();
        state.filter.category = "全部";
        state.filter.query = "";
        state.filter.tab = 0;
        state.busy = state.metadataBusy = state.failed = false;
        state.message = LibraryUiState.Message.NONE;
        state.coverMode = 0;
        state.coverRatio = CoverRatioPolicy.DEFAULT;
        state.coverInferred = false;
        listener.changed(state, false);
        return next;
    }

    public void filterChanged(LibraryFilterState filter, boolean preserveScroll) {
        copyFilter(filter);
        filter(preserveScroll);
    }

    public void close() {
        generation++;
        if (request != null) {
            request.cancel();
            request = null;
        }
    }

    private void copyFilter(LibraryFilterState source) {
        if (source == state.filter) return;
        state.filter.category = source.category;
        state.filter.query = source.query;
        state.filter.studio = source.studio;
        state.filter.actor = source.actor;
        state.filter.tags.clear();
        state.filter.tags.addAll(source.tags);
        state.filter.tab = source.tab;
    }

    private void readCoverSettings() {
        state.coverMode = CoverRatioPolicy.mode(covers.mode(state.profile.id));
        float cached = covers.inferredRatio(state.profile.id);
        state.coverInferred = CoverRatioPolicy.valid(cached);
        state.coverRatio = CoverRatioPolicy.resolve(state.coverMode, cached);
    }

    private void load(boolean cache) {
        if (state.profile == null || state.busy || state.metadataBusy) return;
        final int gen = ++generation;
        state.busy = true;
        state.failed = false;
        state.message = LibraryUiState.Message.CONNECTING;
        if (!cache && state.coverMode == 0) {
            state.coverInferred = false;
            covers.clearInferredRatio(state.profile.id);
            state.coverRatio = CoverRatioPolicy.DEFAULT;
        }
        listener.changed(state, true);
        if (request != null) request.cancel();
        request = library.load(cache, event -> onEvent(gen, event));
    }

    private void onEvent(int gen, LibraryEvent event) {
        if (gen != generation) return;
        switch (event.kind) {
            case CACHE:
                state.message = LibraryUiState.Message.CACHE_UPDATING;
                apply(event.entries);
                break;
            case DIRECTORY:
                state.busy = false;
                state.metadataBusy = !event.entries.isEmpty();
                state.failed = false;
                apply(event.entries);
                break;
            case METADATA:
                mergeMetadata(event.metadata);
                state.metadataBusy = false;
                state.message =
                        partial()
                                ? LibraryUiState.Message.METADATA_PARTIAL
                                : LibraryUiState.Message.NONE;
                recompute();
                listener.changed(state, true);
                break;
            case METADATA_ERROR:
                state.busy = false;
                state.metadataBusy = false;
                state.message = LibraryUiState.Message.METADATA_FAILED;
                listener.changed(state, true);
                break;
            case DIRECTORY_ERROR:
            default:
                state.busy = false;
                state.metadataBusy = false;
                state.failed = true;
                state.message =
                        state.entries.isEmpty()
                                ? LibraryUiState.Message.CONNECTION_FAILED
                                : LibraryUiState.Message.CACHED_OFFLINE;
                listener.connectionFailed(event.failure);
                listener.changed(state, true);
                break;
        }
    }

    private void apply(List<Entry> list) {
        state.entries.clear();
        state.entries.addAll(list);
        recompute();
        listener.changed(state, true);
    }

    private void filter(boolean preserveScroll) {
        recompute();
        listener.changed(state, preserveScroll);
    }

    private void recompute() {
        List<Entry> selected =
                LibraryQuery.select(
                        state.entries,
                        state.filter.category,
                        state.filter.query,
                        state.filter.studio,
                        state.filter.actor,
                        state.filter.tags);
        state.visible.clear();
        for (Entry entry : selected) {
            String key = playbackKey(entry);
            if (state.filter.tab == 0
                    || state.filter.tab == 1 && playback.position(key) > 0
                    || state.filter.tab == 2 && favorites.favorite(key)) {
                state.visible.add(entry);
            }
        }
        if (state.filter.tab == 1) sortByLastWatched();
    }

    private String playbackKey(Entry entry) {
        return state.profile == null
                ? ""
                : ResourceIdentity.playbackKey(state.profile.id, entry.url);
    }

    /**
     * Continue watching is newest-first. The sort is stable, so equal timestamps keep the server
     * order and records without a {@code seen:} timestamp (0) fall below every timestamped entry
     * without being shuffled among themselves.
     */
    private void sortByLastWatched() {
        final Map<Entry, Long> watched = new IdentityHashMap<>();
        for (Entry entry : state.visible) watched.put(entry, playback.lastWatched(playbackKey(entry)));
        state.visible.sort((left, right) -> Long.compare(watched.get(right), watched.get(left)));
    }

    private boolean partial() {
        for (Entry entry : state.entries) if (!entry.metadataLoaded) return true;
        return false;
    }

    /** Applies enriched metadata by stable identity onto the live entries. */
    private void mergeMetadata(Map<String, EntryMetadata> metadata) {
        for (Entry entry : state.entries) {
            EntryMetadata md = metadata.get(ResourceIdentity.of(entry.url));
            if (md == null) continue;
            entry.studio = md.studio;
            entry.actors.clear();
            entry.actors.addAll(md.actors);
            entry.tags.clear();
            entry.tags.addAll(md.tags);
            entry.posterCandidates.clear();
            entry.posterCandidates.addAll(md.posterCandidates);
            entry.metadataLoaded = md.metadataLoaded;
        }
    }
}
