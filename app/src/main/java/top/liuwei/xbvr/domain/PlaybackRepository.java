package top.liuwei.xbvr.domain;

/** Persisted playback keys: resume positions, saved view state and the selected source per scene. */
public interface PlaybackRepository {
    long position(String key);

    void save(String key, long position, Projection view, boolean manual);

    boolean restore(String key, Projection target);

    String selectedSource(String entryKey);

    void selectedSource(String entryKey, String url);

    void entryPosition(String entryKey, long position);
}
