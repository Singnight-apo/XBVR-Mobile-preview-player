package top.liuwei.xbvr.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import okhttp3.OkHttpClient;
import top.liuwei.xbvr.domain.CoverRepository;

/**
 * Decoded cover cache. Keeps the previous budget and sampling rules: an LruCache measured by
 * Bitmap.getByteCount with a 16 MiB ceiling, and a 640x960 sampling threshold.
 */
public final class BitmapCoverRepository implements CoverRepository<Bitmap> {
    private static final int CACHE_BYTES = 16 * 1024 * 1024;
    private static final int MAX_SAMPLE_WIDTH = 640;
    private static final int MAX_SAMPLE_HEIGHT = 960;

    private final LruCache<String, Bitmap> images =
            new LruCache<String, Bitmap>(CACHE_BYTES) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };
    private final Map<String, Boolean> imageProblems = new ConcurrentHashMap<>();
    private final CoverRequests requests;
    private final Executor main;

    public BitmapCoverRepository(OkHttpClient client, Executor io, Executor main) {
        this.requests = new CoverRequests(client, io);
        this.main = main;
    }

    @Override
    public Bitmap cached(String key) {
        return images.get(key);
    }

    @Override
    public boolean failed(String key) {
        return imageProblems.containsKey(key);
    }

    @Override
    public void retry(String key) {
        imageProblems.remove(key);
    }

    /** Previous imageProblems.clear(): lets every failed cover be requested again. */
    @Override
    public void retryAll() {
        imageProblems.clear();
    }

    @Override
    public void invalidate(long epoch) {
        requests.invalidate(epoch);
    }

    @Override
    public void clear() {
        requests.clear();
        images.evictAll();
        imageProblems.clear();
    }

    @Override
    public void request(
            long epoch, String key, List<String> candidates, Observer<Bitmap> observer) {
        Bitmap known = images.get(key);
        if (known != null) {
            main.execute(() -> observer.complete(key, known, known.getWidth(), known.getHeight()));
            return;
        }
        if (imageProblems.containsKey(key)) return;
        requests.request(
                epoch,
                key,
                candidates,
                new CoverRequests.Completion() {
                    @Override
                    public void bytes(byte[] data) {
                        deliver(epoch, key, decode(data), observer);
                    }

                    @Override
                    public void failed() {
                        deliver(epoch, key, null, observer);
                    }
                });
    }

    /** Cache writes and observer callbacks happen on the supplied main executor. */
    private void deliver(long epoch, String key, Bitmap decoded, Observer<Bitmap> observer) {
        main.execute(
                () -> {
                    if (!requests.live(epoch)) return;
                    if (decoded == null) {
                        imageProblems.put(key, Boolean.TRUE);
                        observer.complete(key, null, 0, 0);
                        return;
                    }
                    images.put(key, decoded);
                    imageProblems.remove(key);
                    observer.complete(key, decoded, decoded.getWidth(), decoded.getHeight());
                });
    }

    /** Bounds check plus the original 640x960 sampling threshold. Returns null for unusable bytes. */
    public static Bitmap decode(byte[] data) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null;
        int sample = 1;
        while (opts.outWidth / sample > MAX_SAMPLE_WIDTH
                || opts.outHeight / sample > MAX_SAMPLE_HEIGHT) sample *= 2;
        opts.inJustDecodeBounds = false;
        opts.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
    }
}
