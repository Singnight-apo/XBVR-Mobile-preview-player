package top.liuwei.xbvr.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Network half of cover loading: candidate fallback, the 12 MiB response cap and the per-key /
 * per-epoch request bookkeeping. Deliberately free of Android types so it is covered by JVM tests;
 * decoding and caching live in BitmapCoverRepository.
 *
 * <p>Completions run on the IO executor, never on the caller's thread.
 */
public final class CoverRequests {
    /** Largest accepted cover body, unchanged from the previous inline downloader. */
    public static final int MAX_BYTES = 12 * 1024 * 1024;

    public interface Completion {
        void bytes(byte[] data);

        void failed();
    }

    private final OkHttpClient client;
    private final Executor io;
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    private final Set<Long> epochs = ConcurrentHashMap.newKeySet();

    public CoverRequests(OkHttpClient client, Executor io) {
        this.client = client;
        this.io = io;
    }

    /** True while results for this epoch may still be delivered. */
    public boolean live(long epoch) {
        return epochs.contains(epoch);
    }

    public void request(long epoch, String key, List<String> candidates, Completion completion) {
        if (candidates == null || candidates.isEmpty()) return;
        epochs.add(epoch);
        String token = epoch + ":" + key;
        if (!pending.add(token)) return;
        io.execute(
                () -> {
                    byte[] loaded = null;
                    try {
                        for (String url : candidates) {
                            if (!epochs.contains(epoch) || Thread.currentThread().isInterrupted())
                                return;
                            try {
                                byte[] data = fetch(url);
                                if (data != null) {
                                    loaded = data;
                                    break;
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    } finally {
                        pending.remove(token);
                    }
                    if (!epochs.contains(epoch)) return;
                    if (loaded != null) completion.bytes(loaded);
                    else completion.failed();
                });
    }

    /** Fetches one candidate, rejecting bodies over the cap. Returns null for unusable responses. */
    public byte[] fetch(String url) throws IOException {
        try (Response response = client.newCall(new Request.Builder().url(url).build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream stream = response.body().byteStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = stream.read(buffer)) != -1) {
                    if (bytes.size() + n > MAX_BYTES) throw new IOException("Cover too large");
                    bytes.write(buffer, 0, n);
                }
            }
            return bytes.toByteArray();
        }
    }

    public void invalidate(long epoch) {
        epochs.remove(epoch);
    }

    /** Drops all live epochs, so results still in flight are discarded. */
    public void clear() {
        epochs.clear();
    }

    /** Test seam: only readable to assert that clear() drops old results. */
    Set<Long> liveEpochs() {
        return new HashSet<>(epochs);
    }
}
