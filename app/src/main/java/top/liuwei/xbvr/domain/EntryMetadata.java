package top.liuwei.xbvr.domain;

import java.util.List;

/** Metadata for one library entry. Order is preserved as the server returned it. */
public final class EntryMetadata {
    public final String studio;
    public final List<String> actors;
    public final List<String> tags;
    public final List<String> posterCandidates;
    public final boolean metadataLoaded;

    public EntryMetadata(
            String studio,
            List<String> actors,
            List<String> tags,
            List<String> posterCandidates,
            boolean metadataLoaded) {
        this.studio = studio;
        this.actors = List.copyOf(actors);
        this.tags = List.copyOf(tags);
        this.posterCandidates = List.copyOf(posterCandidates);
        this.metadataLoaded = metadataLoaded;
    }
}
