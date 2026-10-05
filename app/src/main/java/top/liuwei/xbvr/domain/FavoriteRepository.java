package top.liuwei.xbvr.domain;

/** Locally stored favourites, keyed by the stable scene or file identity. */
public interface FavoriteRepository {
    boolean favorite(String key);

    void favorite(String key, boolean value);
}
