package top.liuwei.xbvr.media;

import top.liuwei.xbvr.domain.DiagnosticsSink;

/**
 * The single owner of the playback engine's teardown order. The Media3 session wraps its player in
 * a {@link Backend} and holds no other player field, so the position read, the surface detach and
 * the release always run in this order and one failing step never swallows the following ones.
 * Pure Java so the order is testable without an Android engine.
 */
public final class PlaybackLifecycle {
    /** The engine operations the teardown order depends on. */
    public interface Backend {
        long position();

        void detach();

        void release();
    }

    private Backend backend;
    private final DiagnosticsSink diagnostics;
    private long savedPosition;

    public PlaybackLifecycle(Backend backend, DiagnosticsSink diagnostics, long initialPosition) {
        this.backend = backend;
        this.diagnostics = diagnostics;
        this.savedPosition = initialPosition;
    }

    public boolean hasEngine() {
        return backend != null;
    }

    /** The backend while the policy still owns it, otherwise {@code null}. */
    public Backend backend() {
        return backend;
    }

    public long savedPosition() {
        return savedPosition;
    }

    public void stop() {
        Backend current = backend;
        if (current == null) return;
        backend = null;
        try {
            savedPosition = current.position();
        } catch (RuntimeException e) {
            diagnostics.record("player.position", e, "");
        }
        try {
            current.detach();
        } catch (RuntimeException e) {
            diagnostics.record("player.detach", e, "");
        }
        try {
            current.release();
        } catch (RuntimeException e) {
            diagnostics.record("player.release", e, "");
        }
    }
}
