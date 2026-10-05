package top.liuwei.xbvr.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.Test;
import top.liuwei.xbvr.domain.LibraryEvent;
import top.liuwei.xbvr.domain.LibraryRepository;
import top.liuwei.xbvr.domain.Models.Entry;
import top.liuwei.xbvr.domain.ServerProfile;
import static org.junit.Assert.*;

/** Ordering and error guards for the cached-directory-metadata loader. */
public class LibraryRepositoryTest {
    private static final String BASE = "https://fixture.test/proxy";

    private static final class FakeCache implements LibraryCacheStore {
        String stored = "";
        String written;
        int writes;

        public String read(String id) {
            return stored;
        }

        public void write(String id, String json) {
            written = json;
            writes++;
        }
    }

    private static final class FakeApi extends XbvrApi {
        JSONObject directory = new JSONObject();
        JSONObject metadata = new JSONObject();
        Exception directoryFailure;
        Exception metadataFailure;
        Runnable onDirectory = () -> {};

        FakeApi() {
            super(new ServerProfile("test", BASE, "", "", "", ""));
        }

        @Override
        public JSONObject library() throws Exception {
            onDirectory.run();
            if (directoryFailure != null) throw directoryFailure;
            return directory;
        }

        @Override
        public JSONObject libraryMetadata(List<Entry> entries, JSONObject cached, boolean force)
                throws Exception {
            if (metadataFailure != null) throw metadataFailure;
            return metadata;
        }
    }

    private static JSONObject scene(String id, String title) throws Exception {
        return new JSONObject()
                .put("name", "All")
                .put(
                        "list",
                        new org.json.JSONArray()
                                .put(new JSONObject().put("video_url", "deovr/" + id).put("title", title)));
    }

    private static JSONObject storedLibrary() throws Exception {
        return new JSONObject().put("scenes", new org.json.JSONArray().put(scene("1", "Cached")));
    }

    private static JSONObject servedLibrary() throws Exception {
        return new JSONObject().put("scenes", new org.json.JSONArray().put(scene("1", "Fresh")));
    }

    private static JSONObject enriched() throws Exception {
        return new JSONObject()
                .put(
                        XbvrProtocol.identity(BASE + "/deovr/1"),
                        new JSONObject()
                                .put("studio", "Studio")
                                .put("actors", new org.json.JSONArray().put("Actor"))
                                .put("tags", new org.json.JSONArray().put("Tag"))
                                .put("posterCandidates", new org.json.JSONArray())
                                .put("metadataLoaded", true));
    }

    private static final class Recorder implements LibraryRepository.Observer {
        final List<LibraryEvent.Kind> kinds = new ArrayList<>();
        final List<LibraryEvent> events = new ArrayList<>();

        public void event(LibraryEvent event) {
            kinds.add(event.kind);
            events.add(event);
        }
    }

    private static LibraryRepository repository(
            FakeApi api, FakeCache cache, ExecutorService io, ExecutorService main) {
        return new DefaultLibraryRepository(api, cache, io, main);
    }

    @Test public void emitsCacheDirectoryThenMetadataAndWritesTheCache() throws Exception {
        ExecutorService direct = Executors.newSingleThreadExecutor();
        try {
            FakeApi api = new FakeApi();
            api.directory = servedLibrary();
            api.metadata = enriched();
            FakeCache cache = new FakeCache();
            cache.stored = storedLibrary().toString();
            Recorder recorder = new Recorder();

            LibraryRepository.Request request =
                    repository(api, cache, direct, direct).load(true, recorder);
            waitFor(recorder, 3);
            assertNotNull(request);

            assertEquals(
                    List.of(
                            LibraryEvent.Kind.CACHE,
                            LibraryEvent.Kind.DIRECTORY,
                            LibraryEvent.Kind.METADATA),
                    recorder.kinds);
            assertEquals(1, recorder.events.get(1).entries.size());
            assertEquals("Fresh", recorder.events.get(1).entries.get(0).title);
            assertEquals(1, recorder.events.get(2).metadata.size());
            assertEquals("Studio", recorder.events.get(2).metadata.values().iterator().next().studio);
            assertEquals(1, cache.writes);
            assertTrue(cache.written.contains("_metadata"));
        } finally {
            direct.shutdownNow();
        }
    }

    @Test public void withoutCachedDirectoryTheFirstEventIsTheDirectory() throws Exception {
        ExecutorService direct = Executors.newSingleThreadExecutor();
        try {
            FakeApi api = new FakeApi();
            api.directory = servedLibrary();
            api.metadata = enriched();
            FakeCache cache = new FakeCache();
            Recorder recorder = new Recorder();

            repository(api, cache, direct, direct).load(false, recorder);
            waitFor(recorder, 2);
            assertEquals(List.of(LibraryEvent.Kind.DIRECTORY, LibraryEvent.Kind.METADATA), recorder.kinds);
        } finally {
            direct.shutdownNow();
        }
    }

    @Test public void directoryFailureWithoutCacheIsADirectoryError() throws Exception {
        ExecutorService direct = Executors.newSingleThreadExecutor();
        try {
            FakeApi api = new FakeApi();
            api.directoryFailure = new java.io.IOException("offline");
            FakeCache cache = new FakeCache();
            cache.stored = storedLibrary().toString();
            Recorder recorder = new Recorder();

            repository(api, cache, direct, direct).load(false, recorder);
            waitFor(recorder, 1);
            assertEquals(List.of(LibraryEvent.Kind.DIRECTORY_ERROR), recorder.kinds);
            assertNotNull(recorder.events.get(0).failure);
            assertEquals(0, cache.writes);
        } finally {
            direct.shutdownNow();
        }
    }

    @Test public void enrichmentFailureAfterTheDirectoryIsOnlyAMetadataError() throws Exception {
        ExecutorService direct = Executors.newSingleThreadExecutor();
        try {
            FakeApi api = new FakeApi();
            api.directory = servedLibrary();
            api.metadataFailure = new java.io.IOException("enrichment");
            FakeCache cache = new FakeCache();
            Recorder recorder = new Recorder();

            repository(api, cache, direct, direct).load(false, recorder);
            waitFor(recorder, 2);
            assertEquals(List.of(LibraryEvent.Kind.DIRECTORY, LibraryEvent.Kind.METADATA_ERROR), recorder.kinds);
            assertEquals(1, recorder.events.get(0).entries.size());
            assertEquals(0, cache.writes);
        } finally {
            direct.shutdownNow();
        }
    }

    @Test public void corruptCacheIsIgnoredAndTheDirectoryStillLoads() throws Exception {
        ExecutorService direct = Executors.newSingleThreadExecutor();
        try {
            FakeApi api = new FakeApi();
            api.directory = servedLibrary();
            api.metadata = enriched();
            FakeCache cache = new FakeCache();
            cache.stored = "not json";
            Recorder recorder = new Recorder();

            repository(api, cache, direct, direct).load(true, recorder);
            waitFor(recorder, 2);
            assertEquals(List.of(LibraryEvent.Kind.DIRECTORY, LibraryEvent.Kind.METADATA), recorder.kinds);
        } finally {
            direct.shutdownNow();
        }
    }

    @Test public void cancellationDuringTheFetchDropsEveryEventAndTheCacheWrite() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            FakeApi api = new FakeApi();
            api.directory = servedLibrary();
            api.metadata = enriched();
            api.onDirectory =
                    () -> {
                        entered.countDown();
                        try {
                            release.await(3, TimeUnit.SECONDS);
                        } catch (InterruptedException ignored) {
                        }
                    };
            FakeCache cache = new FakeCache();
            Recorder recorder = new Recorder();

            LibraryRepository.Request request =
                    repository(api, cache, worker, worker).load(false, recorder);
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            request.cancel();
            release.countDown();
            Thread.sleep(400);

            assertTrue(recorder.kinds.isEmpty());
            assertEquals(0, cache.writes);
        } finally {
            worker.shutdownNow();
        }
    }

    private static void waitFor(Recorder recorder, int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 3000;
        while (recorder.kinds.size() < expected && System.currentTimeMillis() < deadline) Thread.sleep(20);
        assertEquals("expected " + expected + " events", expected, recorder.kinds.size());
    }
}
