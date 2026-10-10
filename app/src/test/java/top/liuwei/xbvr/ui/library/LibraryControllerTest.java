package top.liuwei.xbvr.ui.library;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import top.liuwei.xbvr.domain.CoverSettings;
import top.liuwei.xbvr.domain.EntryMetadata;
import top.liuwei.xbvr.domain.FavoriteRepository;
import top.liuwei.xbvr.domain.LibraryEvent;
import top.liuwei.xbvr.domain.LibraryFilterState;
import top.liuwei.xbvr.domain.LibraryRepository;
import top.liuwei.xbvr.domain.Models.Entry;
import top.liuwei.xbvr.domain.PlaybackRepository;
import top.liuwei.xbvr.domain.ProfileRepository;
import top.liuwei.xbvr.domain.Projection;
import top.liuwei.xbvr.domain.ResourceIdentity;
import top.liuwei.xbvr.domain.ServerProfile;
import static org.junit.Assert.*;

/** State coordination of the library page, driven through fakes; no Android and no network. */
public class LibraryControllerTest {
    @Test
    public void failedRemovalKeepsActiveProfileAndRequestAlive() throws Exception {
        FakeProfiles profiles = new FakeProfiles();
        profiles.values.add(P1);
        profiles.failRemove = true;
        FakeLibrary loader = new FakeLibrary();
        LibraryController c = new LibraryController(profiles, loader, new FakePlayback(),
                new FakeFavorites(), new FakeCovers(), new Recorder());
        c.open(P1, true);
        int generation = c.generation();
        assertThrows(IOException.class, () -> c.removeProfile(P1.id));
        assertSame(P1, c.state().profile);
        assertEquals(generation, c.generation());
        assertEquals(List.of(P1), profiles.values);
        loader.emit(0, LibraryEvent.directory(List.of(entry("Keep", "https://s.test/1"))));
        assertEquals(1, c.state().visible.size());
    }

    @Test
    public void removingInactiveProfilePreservesCurrentPage() throws Exception {
        FakeProfiles profiles = new FakeProfiles();
        profiles.values.addAll(List.of(P1, P2));
        FakeLibrary loader = new FakeLibrary();
        LibraryController c = new LibraryController(profiles, loader, new FakePlayback(),
                new FakeFavorites(), new FakeCovers(), new Recorder());
        c.open(P1, true);
        loader.emit(0, LibraryEvent.directory(List.of(entry("Keep", "https://s.test/1"))));
        int generation = c.generation();
        assertSame(P1, c.removeProfile(P2.id));
        assertSame(P1, c.state().profile);
        assertEquals(generation, c.generation());
        assertEquals(1, c.state().visible.size());
        assertEquals(0, loader.cancelled);
        assertEquals(List.of(P1), profiles.values);
    }

    @Test
    public void removingActiveProfileSelectsFirstRemainingAndRejectsLateCallbacks() throws Exception {
        FakeProfiles profiles = new FakeProfiles();
        profiles.values.addAll(List.of(P1, P2));
        FakeLibrary loader = new FakeLibrary();
        LibraryController c = new LibraryController(profiles, loader, new FakePlayback(),
                new FakeFavorites(), new FakeCovers(), new Recorder());
        c.open(P2, true);
        assertSame(P1, c.removeProfile(P2.id));
        assertEquals(P1.id, profiles.selected);
        assertNull(c.state().profile);
        assertEquals(1, loader.cancelled);
        loader.emit(0, LibraryEvent.directory(List.of(entry("Late", "https://t.test/1"))));
        assertTrue(c.state().entries.isEmpty());
        assertTrue(c.state().visible.isEmpty());
        assertFalse(c.state().busy);
    }

    @Test
    public void removingLastProfileClearsSelectionAndCanRefreshSafely() throws Exception {
        FakeProfiles profiles = new FakeProfiles();
        profiles.values.add(P1);
        FakeLibrary loader = new FakeLibrary();
        LibraryController c = new LibraryController(profiles, loader, new FakePlayback(),
                new FakeFavorites(), new FakeCovers(), new Recorder());
        c.open(P1, true);
        c.state().filter.query = "old";
        assertNull(c.removeProfile(P1.id));
        assertEquals("", profiles.selected);
        assertNull(c.state().profile);
        assertEquals("", c.state().filter.query);
        assertTrue(profiles.values.isEmpty());
        c.refresh();
        assertEquals(1, loader.observers.size());
    }

    private static final ServerProfile P1 =
            new ServerProfile("p1", "https://s.test", "", "", "", "");
    private static final ServerProfile P2 =
            new ServerProfile("p2", "https://t.test", "", "", "", "");

    private static final class Recorder implements LibraryController.Listener {
        LibraryUiState state;
        int changes;
        boolean keepScroll;
        int connectionFailures;
        Throwable lastFailure;

        public void changed(LibraryUiState value, boolean keep) {
            state = value;
            changes++;
            keepScroll = keep;
        }

        public void connectionFailed(Throwable failure) {
            connectionFailures++;
            lastFailure = failure;
        }
    }

    private static final class FakeProfiles implements ProfileRepository {
        final List<ServerProfile> values = new ArrayList<>();
        String selected;
        boolean failRemove;

        public List<ServerProfile> all() {
            return values;
        }

        public ServerProfile current() {
            return values.isEmpty() ? null : values.get(0);
        }

        public ServerProfile find(String id) {
            for (ServerProfile value : values) if (value.id.equals(id)) return value;
            return null;
        }

        public void save(ServerProfile value) {
            values.add(value);
        }

        public void remove(String id) throws IOException {
            if (failRemove) throw new IOException("Unable to persist removal");
            values.removeIf(value -> value.id.equals(id));
        }

        public void select(String id) {
            selected = id;
        }
    }

    private static final class FakeLibrary implements LibraryRepository {
        int cancelled;
        final List<Observer> observers = new ArrayList<>();
        final List<Boolean> cached = new ArrayList<>();

        public Request load(boolean useCache, Observer observer) {
            observers.add(observer);
            cached.add(useCache);
            return () -> cancelled++;
        }

        void emit(int index, LibraryEvent event) {
            observers.get(index).event(event);
        }
    }

    private static final class FakePlayback implements PlaybackRepository {
        final Map<String, Long> positions = new HashMap<>();
        final Map<String, Long> watched = new HashMap<>();
        final Map<String, String> sources = new HashMap<>();

        public long position(String key) {
            return positions.getOrDefault(key, 0L);
        }

        public long lastWatched(String key) {
            return watched.getOrDefault(key, 0L);
        }

        public void save(String key, long position, Projection view, boolean manual) {}

        public boolean restore(String key, Projection target) {
            return false;
        }

        public String selectedSource(String entryKey) {
            return sources.get(entryKey);
        }

        public void selectedSource(String entryKey, String url) {
            sources.put(entryKey, url);
        }

        public void entryPosition(String entryKey, long position) {
            positions.put(entryKey, position);
        }
    }

    private static final class FakeFavorites implements FavoriteRepository {
        final Set<String> keys = new HashSet<>();

        public boolean favorite(String key) {
            return keys.contains(key);
        }

        public void favorite(String key, boolean value) {
            if (value) keys.add(key);
            else keys.remove(key);
        }
    }

    private static final class FakeCovers implements CoverSettings {
        final Map<String, Integer> modes = new HashMap<>();
        final Map<String, Float> ratios = new HashMap<>();

        public int mode(String profileId) {
            return modes.getOrDefault(profileId, 0);
        }

        public void mode(String profileId, int value) {
            modes.put(profileId, value);
        }

        public float inferredRatio(String profileId) {
            return ratios.getOrDefault(profileId, Float.NaN);
        }

        public void inferredRatio(String profileId, float ratio) {
            ratios.put(profileId, ratio);
        }

        public void clearInferredRatio(String profileId) {
            ratios.remove(profileId);
        }
    }

    private static Entry entry(String title, String url) {
        Entry value = new Entry();
        value.title = title;
        value.url = url;
        return value;
    }

    private static Map<String, EntryMetadata> metadataFor(Entry value, boolean loaded) {
        Map<String, EntryMetadata> map = new HashMap<>();
        map.put(
                ResourceIdentity.of(value.url),
                new EntryMetadata(
                        "Studio", List.of("Actor"), List.of("Tag"), List.of(), loaded));
        return map;
    }

    private static LibraryController controller(
            FakeLibrary library, Recorder recorder, FakePlayback playback, FakeFavorites favorites) {
        return new LibraryController(
                new FakeProfiles(), library, playback, favorites, new FakeCovers(), recorder);
    }

    @Test
    public void cacheThenDirectoryThenMetadataArePublishedInOrder() {
        FakeLibrary library = new FakeLibrary();
        Recorder recorder = new Recorder();
        LibraryController controller =
                controller(library, recorder, new FakePlayback(), new FakeFavorites());
        controller.open(P1, true);
        assertEquals(1, library.observers.size());
        assertTrue(library.cached.get(0));

        Entry cached = entry("Cached", "https://s.test/deovr/1");
        Entry fresh = entry("Fresh", "https://s.test/deovr/2");

        library.emit(0, LibraryEvent.cache(List.of(cached)));
        assertEquals(LibraryUiState.Message.CACHE_UPDATING, recorder.state.message);
        assertEquals(1, recorder.state.entries.size());
        assertEquals("Cached", recorder.state.entries.get(0).title);

        library.emit(0, LibraryEvent.directory(List.of(fresh)));
        assertFalse(recorder.state.busy);
        assertTrue(recorder.state.metadataBusy);
        assertEquals(1, recorder.state.entries.size());
        assertEquals("Fresh", recorder.state.entries.get(0).title);
        assertEquals(1, recorder.state.visible.size());

        library.emit(0, LibraryEvent.metadata(metadataFor(fresh, true)));
        assertFalse(recorder.state.metadataBusy);
        assertEquals(LibraryUiState.Message.NONE, recorder.state.message);
        assertEquals("Studio", recorder.state.entries.get(0).studio);
        assertTrue(recorder.state.entries.get(0).metadataLoaded);
        assertSame(recorder.state, controller.state());
    }

    @Test
    public void metadataFailureAfterDirectoryKeepsTheDirectoryState() {
        FakeLibrary library = new FakeLibrary();
        Recorder recorder = new Recorder();
        LibraryController controller =
                controller(library, recorder, new FakePlayback(), new FakeFavorites());
        controller.open(P1, true);

        Entry fresh = entry("Fresh", "https://s.test/deovr/2");
        library.emit(0, LibraryEvent.directory(List.of(fresh)));
        library.emit(0, LibraryEvent.metadataError(new IOException("enrichment")));

        assertEquals(1, controller.state().entries.size());
        assertEquals(1, controller.state().visible.size());
        assertFalse(controller.state().failed);
        assertFalse(controller.state().metadataBusy);
        assertEquals(LibraryUiState.Message.METADATA_FAILED, controller.state().message);
        assertEquals(0, recorder.connectionFailures);
    }

    @Test
    public void anEventFromAnOldGenerationDoesNotUpdateTheState() {
        FakeLibrary library = new FakeLibrary();
        Recorder recorder = new Recorder();
        LibraryController controller =
                controller(library, recorder, new FakePlayback(), new FakeFavorites());
        controller.open(P1, true);
        library.emit(0, LibraryEvent.directory(List.of(entry("Old", "https://s.test/deovr/1"))));

        controller.open(P2, true);
        int changes = recorder.changes;
        library.emit(0, LibraryEvent.directory(List.of(entry("Stale", "https://s.test/deovr/9"))));

        assertEquals(changes, recorder.changes);
        assertTrue(controller.state().entries.isEmpty());
        assertEquals(P2.id, controller.state().profile.id);

        library.emit(1, LibraryEvent.directory(List.of(entry("New", "https://t.test/deovr/2"))));
        assertEquals(1, controller.state().entries.size());
        assertEquals("New", controller.state().entries.get(0).title);
    }

    @Test
    public void continueWatchingTabSelectsEntriesWithAPosition() {
        FakeLibrary library = new FakeLibrary();
        Recorder recorder = new Recorder();
        FakePlayback playback = new FakePlayback();
        LibraryController controller =
                controller(library, recorder, playback, new FakeFavorites());
        controller.open(P1, true);

        Entry unseen = entry("Unseen", "https://s.test/deovr/1");
        Entry started = entry("Started", "https://s.test/deovr/2");
        library.emit(0, LibraryEvent.directory(List.of(unseen, started)));
        playback.positions.put(ResourceIdentity.playbackKey(P1.id, started.url), 120L);

        LibraryFilterState filter = new LibraryFilterState();
        filter.tab = 1;
        controller.filterChanged(filter, false);

        assertEquals(1, controller.state().visible.size());
        assertEquals("Started", controller.state().visible.get(0).title);
    }

    @Test
    public void favouritesTabSelectsLocallyFavouritedEntries() {
        FakeLibrary library = new FakeLibrary();
        Recorder recorder = new Recorder();
        FakeFavorites favorites = new FakeFavorites();
        LibraryController controller =
                controller(library, recorder, new FakePlayback(), favorites);
        controller.open(P1, true);

        Entry plain = entry("Plain", "https://s.test/deovr/1");
        Entry loved = entry("Loved", "https://s.test/deovr/2");
        library.emit(0, LibraryEvent.directory(List.of(plain, loved)));
        favorites.favorite(ResourceIdentity.playbackKey(P1.id, loved.url), true);

        LibraryFilterState filter = new LibraryFilterState();
        filter.tab = 2;
        controller.filterChanged(filter, false);

        assertEquals(1, controller.state().visible.size());
        assertEquals("Loved", controller.state().visible.get(0).title);
    }

    @Test
    public void categorySortingIsPreservedThroughTheQuery() {
        FakeLibrary library = new FakeLibrary();
        Recorder recorder = new Recorder();
        LibraryController controller =
                controller(library, recorder, new FakePlayback(), new FakeFavorites());
        controller.open(P1, true);

        Entry later = entry("Later", "https://s.test/deovr/1");
        later.groups.add("Studio");
        later.groupOrder.put("Studio", 2);
        Entry earlier = entry("Earlier", "https://s.test/deovr/2");
        earlier.groups.add("Studio");
        earlier.groupOrder.put("Studio", 1);
        library.emit(0, LibraryEvent.directory(List.of(later, earlier)));

        LibraryFilterState filter = new LibraryFilterState();
        filter.category = "Studio";
        controller.filterChanged(filter, false);

        assertEquals(2, controller.state().visible.size());
        assertEquals("Earlier", controller.state().visible.get(0).title);
        assertEquals("Later", controller.state().visible.get(1).title);
    }

    @Test
    public void switchingServerClearsFacetsButKeepsTheOtherFilterState() {
        FakeLibrary library = new FakeLibrary();
        Recorder recorder = new Recorder();
        LibraryController controller =
                controller(library, recorder, new FakePlayback(), new FakeFavorites());
        controller.open(P1, true);

        LibraryFilterState filter = new LibraryFilterState();
        filter.studio = "A";
        filter.actor = "B";
        filter.tags.add("T");
        filter.query = "term";
        filter.tab = 2;
        controller.filterChanged(filter, false);
        assertTrue(controller.state().filter.hasFacets());

        controller.open(P2, false);

        assertFalse(controller.state().filter.hasFacets());
        assertEquals("term", controller.state().filter.query);
        assertEquals(2, controller.state().filter.tab);
    }

    @Test
    public void resetOpenClearsCategoryQueryAndTab() {
        FakeLibrary library = new FakeLibrary();
        Recorder recorder = new Recorder();
        LibraryController controller =
                controller(library, recorder, new FakePlayback(), new FakeFavorites());
        controller.open(P1, true);

        LibraryFilterState filter = new LibraryFilterState();
        filter.category = "Studio";
        filter.query = "term";
        filter.tab = 1;
        filter.studio = "A";
        controller.filterChanged(filter, false);

        controller.open(P1, true);

        assertEquals("全部", controller.state().filter.category);
        assertEquals("", controller.state().filter.query);
        assertEquals(0, controller.state().filter.tab);
        assertFalse(controller.state().filter.hasFacets());
        assertTrue(controller.state().message == LibraryUiState.Message.CONNECTING);
    }

    @Test
    public void closeDropsLaterEvents() {
        FakeLibrary library = new FakeLibrary();
        Recorder recorder = new Recorder();
        LibraryController controller =
                controller(library, recorder, new FakePlayback(), new FakeFavorites());
        controller.open(P1, true);
        int changes = recorder.changes;

        controller.close();
        library.emit(0, LibraryEvent.directory(List.of(entry("Late", "https://s.test/deovr/1"))));

        assertEquals(changes, recorder.changes);
        assertTrue(controller.state().entries.isEmpty());
    }

    private static String key(String url) {
        return ResourceIdentity.playbackKey(P1.id, url);
    }

    /** Publishes the directory then selects a tab, so ordering can be asserted on visible. */
    private static LibraryController withTab(
            FakePlayback playback, FakeFavorites favorites, List<Entry> entries, int tab) {
        FakeLibrary library = new FakeLibrary();
        Recorder recorder = new Recorder();
        LibraryController controller = controller(library, recorder, playback, favorites);
        controller.open(P1, true);
        library.emit(0, LibraryEvent.directory(entries));
        LibraryFilterState filter = new LibraryFilterState();
        filter.tab = tab;
        controller.filterChanged(filter, false);
        return controller;
    }

    private static List<String> titles(List<Entry> entries) {
        List<String> values = new ArrayList<>();
        for (Entry entry : entries) values.add(entry.title);
        return values;
    }

    @Test
    public void continueWatchingIsOrderedNewestFirst() {
        FakePlayback playback = new FakePlayback();
        Entry first = entry("First", "https://s.test/deovr/1");
        Entry second = entry("Second", "https://s.test/deovr/2");
        Entry third = entry("Third", "https://s.test/deovr/3");
        for (Entry value : List.of(first, second, third)) {
            playback.positions.put(key(value.url), 10L);
        }
        playback.watched.put(key(first.url), 100L);
        playback.watched.put(key(second.url), 300L);
        playback.watched.put(key(third.url), 200L);

        LibraryController controller =
                withTab(playback, new FakeFavorites(), List.of(first, second, third), 1);

        assertEquals(List.of("Second", "Third", "First"), titles(controller.state().visible));
    }

    @Test
    public void untimestampedEntriesFollowEveryTimestampedEntryInServerOrder() {
        FakePlayback playback = new FakePlayback();
        Entry a = entry("A", "https://s.test/deovr/1");
        Entry b = entry("B", "https://s.test/deovr/2");
        Entry c = entry("C", "https://s.test/deovr/3");
        Entry d = entry("D", "https://s.test/deovr/4");
        Entry e = entry("E", "https://s.test/deovr/5");
        for (Entry value : List.of(a, b, c, d, e)) {
            playback.positions.put(key(value.url), 5L);
        }
        playback.watched.put(key(a.url), 100L);
        playback.watched.put(key(c.url), 500L);
        playback.watched.put(key(e.url), 200L);

        LibraryController controller =
                withTab(playback, new FakeFavorites(), List.of(a, b, c, d, e), 1);

        assertEquals(List.of("C", "E", "A", "B", "D"), titles(controller.state().visible));
    }

    @Test
    public void equalTimestampsKeepTheirServerOrder() {
        FakePlayback playback = new FakePlayback();
        Entry a = entry("A", "https://s.test/deovr/1");
        Entry b = entry("B", "https://s.test/deovr/2");
        Entry c = entry("C", "https://s.test/deovr/3");
        for (Entry value : List.of(a, b, c)) {
            playback.positions.put(key(value.url), 5L);
            playback.watched.put(key(value.url), 777L);
        }

        LibraryController controller =
                withTab(playback, new FakeFavorites(), List.of(a, b, c), 1);

        assertEquals(List.of("A", "B", "C"), titles(controller.state().visible));
    }

    @Test
    public void timestampedRecordsDoNotReorderTheOtherTabs() {
        FakePlayback playback = new FakePlayback();
        Entry a = entry("A", "https://s.test/deovr/1");
        Entry b = entry("B", "https://s.test/deovr/2");
        Entry c = entry("C", "https://s.test/deovr/3");
        playback.watched.put(key(a.url), 100L);
        playback.watched.put(key(b.url), 900L);
        playback.watched.put(key(c.url), 400L);

        FakeFavorites favorites = new FakeFavorites();
        favorites.favorite(key(a.url), true);
        favorites.favorite(key(c.url), true);

        LibraryController all = withTab(playback, favorites, List.of(a, b, c), 0);
        assertEquals(List.of("A", "B", "C"), titles(all.state().visible));

        LibraryController loved = withTab(playback, favorites, List.of(a, b, c), 2);
        assertEquals(List.of("A", "C"), titles(loved.state().visible));
    }
}
