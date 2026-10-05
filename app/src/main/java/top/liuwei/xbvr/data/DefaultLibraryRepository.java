package top.liuwei.xbvr.data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import top.liuwei.xbvr.domain.EntryMetadata;
import top.liuwei.xbvr.domain.LibraryEvent;
import top.liuwei.xbvr.domain.LibraryRepository;
import top.liuwei.xbvr.domain.Models.Entry;

/**
 * Cached-directory-metadata loader. The sequencing, waiting and error handling are carried over from
 * the previous MainActivity.load: the cached directory is shown first, the server directory next,
 * and enrichment last; a failure before the directory becomes a directory error, a failure after it
 * only becomes a metadata error.
 */
public final class DefaultLibraryRepository implements LibraryRepository {
    private final XbvrApi api;
    private final LibraryCacheStore cache;
    private final Executor io;
    private final Executor main;

    public DefaultLibraryRepository(XbvrApi api, LibraryCacheStore cache, Executor io, Executor main) {
        this.api = api;
        this.cache = cache;
        this.io = io;
        this.main = main;
    }

    @Override
    public Request load(boolean useCache, Observer observer) {
        RequestImpl request = new RequestImpl();
        io.execute(() -> load(request, useCache, observer));
        return request;
    }

    private void load(RequestImpl request, boolean useCache, Observer observer) {
        JSONObject previous = new JSONObject();
        String saved = cache.read(api.id);
        if (!saved.isBlank())
            try {
                previous = new JSONObject(saved);
                if (useCache) {
                    List<Entry> old = XbvrProtocol.library(previous, api.base);
                    emit(request, observer, LibraryEvent.cache(old));
                }
            } catch (Exception ignored) {
            }
        boolean displayed = false;
        try {
            JSONObject response = api.library();
            List<Entry> list = XbvrProtocol.library(response, api.base);
            XbvrProtocol.merge(list, previous.optJSONObject("_metadata"));
            if (request.cancelled()) return;
            emit(request, observer, LibraryEvent.directory(list));
            displayed = true;
            JSONObject enriched = api.libraryMetadata(list, previous, !useCache);
            response.put("_metadata", enriched);
            if (request.cancelled()) return;
            cache.write(api.id, response.toString());
            emit(request, observer, LibraryEvent.metadata(metadata(enriched, api.base)));
        } catch (Exception failure) {
            if (displayed) emit(request, observer, LibraryEvent.metadataError(failure));
            else emit(request, observer, LibraryEvent.directoryError(failure));
        }
    }

    /** Reads each enriched snapshot through the shared metadata rules so the map matches the wire. */
    private static Map<String, EntryMetadata> metadata(JSONObject enriched, String base) {
        Map<String, EntryMetadata> out = new LinkedHashMap<>();
        for (Iterator<String> keys = enriched.keys(); keys.hasNext(); ) {
            String key = keys.next();
            JSONObject value = enriched.optJSONObject(key);
            if (value == null) continue;
            Entry scratch = new Entry();
            XbvrProtocol.metadata(scratch, value, base);
            out.put(
                    key,
                    new EntryMetadata(
                            scratch.studio,
                            new ArrayList<>(scratch.actors),
                            new ArrayList<>(scratch.tags),
                            new ArrayList<>(scratch.posterCandidates),
                            scratch.metadataLoaded));
        }
        return out;
    }

    private void emit(RequestImpl request, Observer observer, LibraryEvent event) {
        if (request.cancelled()) return;
        main.execute(
                () -> {
                    if (!request.cancelled()) observer.event(event);
                });
    }

    private static final class RequestImpl implements Request {
        private final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void cancel() {
            cancelled.set(true);
        }

        boolean cancelled() {
            return cancelled.get();
        }
    }
}
