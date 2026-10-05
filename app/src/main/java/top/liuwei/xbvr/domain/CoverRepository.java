package top.liuwei.xbvr.domain;

import java.util.List;

/**
 * Poster/cover loading with a per-key cache. {@code I} keeps Domain free of Bitmap: the production
 * implementation is CoverRepository&lt;Bitmap&gt; and tests may use any placeholder type.
 */
public interface CoverRepository<I> {
    interface Observer<I> {
        void complete(String key, I image, int width, int height);
    }

    I cached(String key);

    boolean failed(String key);

    void retry(String key);

    void request(long epoch, String key, List<String> candidates, Observer<I> observer);

    void invalidate(long epoch);

    void clear();
}
