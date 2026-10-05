package top.liuwei.xbvr.data;

import top.liuwei.xbvr.domain.PlaybackRepository;
import top.liuwei.xbvr.domain.Projection;

/** Resume positions, saved view state and selected sources on top of the local preferences. */
public final class PlaybackStore implements PlaybackRepository {
    private final LocalSettings settings;

    public PlaybackStore(LocalSettings settings) {
        this.settings = settings;
    }

    @Override
    public long position(String key) {
        return settings.position(key);
    }

    @Override
    public void save(String key, long position, Projection view, boolean manual) {
        settings.save(key, position, view, manual);
    }

    @Override
    public boolean restore(String key, Projection target) {
        return settings.restore(key, target);
    }

    @Override
    public String selectedSource(String entryKey) {
        return settings.selectedSource(entryKey);
    }

    @Override
    public void selectedSource(String entryKey, String url) {
        settings.selectedSource(entryKey, url);
    }

    @Override
    public void entryPosition(String entryKey, long position) {
        settings.entryPosition(entryKey, position);
    }
}
