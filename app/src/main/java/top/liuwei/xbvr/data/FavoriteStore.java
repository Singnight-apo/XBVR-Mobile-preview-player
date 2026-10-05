package top.liuwei.xbvr.data;

import top.liuwei.xbvr.domain.FavoriteRepository;

/** Local favourites keyed by the stable scene or file identity. */
public final class FavoriteStore implements FavoriteRepository {
    private final LocalSettings settings;

    public FavoriteStore(LocalSettings settings) {
        this.settings = settings;
    }

    @Override
    public boolean favorite(String key) {
        return settings.favorite(key);
    }

    @Override
    public void favorite(String key, boolean value) {
        settings.favorite(key, value);
    }
}
