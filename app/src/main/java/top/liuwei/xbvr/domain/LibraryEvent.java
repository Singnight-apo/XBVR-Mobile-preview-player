package top.liuwei.xbvr.domain;

import java.util.List;
import java.util.Map;
import top.liuwei.xbvr.domain.Models.Entry;

/** One step of the cached-directory-metadata sequence. Factories fill only what the kind needs. */
public final class LibraryEvent {
    public enum Kind {
        CACHE,
        DIRECTORY,
        METADATA,
        DIRECTORY_ERROR,
        METADATA_ERROR
    }

    public final Kind kind;
    public final List<Entry> entries;
    public final Map<String, EntryMetadata> metadata;
    public final Throwable failure;

    private LibraryEvent(
            Kind kind, List<Entry> entries, Map<String, EntryMetadata> metadata, Throwable failure) {
        this.kind = kind;
        this.entries = entries;
        this.metadata = metadata;
        this.failure = failure;
    }

    /** Cached directory shown before the server answers. */
    public static LibraryEvent cache(List<Entry> entries) {
        return new LibraryEvent(Kind.CACHE, List.copyOf(entries), Map.of(), null);
    }

    /** Fresh directory from the server, already merged with cached metadata. */
    public static LibraryEvent directory(List<Entry> entries) {
        return new LibraryEvent(Kind.DIRECTORY, List.copyOf(entries), Map.of(), null);
    }

    /** Metadata enrichment keyed by stable identity. */
    public static LibraryEvent metadata(Map<String, EntryMetadata> metadata) {
        return new LibraryEvent(Kind.METADATA, List.of(), Map.copyOf(metadata), null);
    }

    public static LibraryEvent directoryError(Throwable failure) {
        return new LibraryEvent(Kind.DIRECTORY_ERROR, List.of(), Map.of(), failure);
    }

    /** Enrichment failed after the directory was already displayed. */
    public static LibraryEvent metadataError(Throwable failure) {
        return new LibraryEvent(Kind.METADATA_ERROR, List.of(), Map.of(), failure);
    }
}
