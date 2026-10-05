package top.liuwei.xbvr.media;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import top.liuwei.xbvr.domain.DiagnosticsSink;

/**
 * Locks the production teardown order: every step of {@link PlaybackLifecycle#stop()} runs even
 * when an earlier one fails, and the position saved before a failed position read is kept.
 */
public final class PlaybackLifecycleTest {

    @Test
    public void releaseContinuesAfterDetachFails() {
        FakeBackend backend = FakeBackend.throwOn("detach");
        FakeDiagnostics diagnostics = new FakeDiagnostics();
        PlaybackLifecycle lifecycle = new PlaybackLifecycle(backend, diagnostics, 100L);
        lifecycle.stop();
        assertEquals(List.of("position", "detach", "release"), backend.calls());
        assertFalse(lifecycle.hasEngine());
        assertNull(lifecycle.backend());
        assertEquals(List.of("player.detach"), diagnostics.phases());
    }

    @Test
    public void failedPositionReadKeepsTheInitialPosition() {
        FakeBackend backend = FakeBackend.throwOn("position");
        FakeDiagnostics diagnostics = new FakeDiagnostics();
        PlaybackLifecycle lifecycle = new PlaybackLifecycle(backend, diagnostics, 100L);
        lifecycle.stop();
        assertEquals(100L, lifecycle.savedPosition());
        assertEquals(List.of("position", "detach", "release"), backend.calls());
        assertFalse(lifecycle.hasEngine());
        assertEquals(List.of("player.position"), diagnostics.phases());
    }

    @Test
    public void releaseFailureStillClearsTheEngine() {
        FakeBackend backend = FakeBackend.throwOn("release");
        FakeDiagnostics diagnostics = new FakeDiagnostics();
        PlaybackLifecycle lifecycle = new PlaybackLifecycle(backend, diagnostics, 0L);
        lifecycle.stop();
        assertFalse(lifecycle.hasEngine());
        assertEquals(5000L, lifecycle.savedPosition());
        assertEquals(List.of("position", "detach", "release"), backend.calls());
        assertEquals(List.of("player.release"), diagnostics.phases());
    }

    @Test
    public void stopTwiceReleasesOnce() {
        FakeBackend backend = FakeBackend.throwOn(null);
        FakeDiagnostics diagnostics = new FakeDiagnostics();
        PlaybackLifecycle lifecycle = new PlaybackLifecycle(backend, diagnostics, 0L);
        assertTrue(lifecycle.hasEngine());
        assertSame(backend, lifecycle.backend());
        lifecycle.stop();
        lifecycle.stop();
        assertEquals(List.of("position", "detach", "release"), backend.calls());
        assertFalse(lifecycle.hasEngine());
        assertTrue(diagnostics.phases().isEmpty());
    }

    @Test
    public void emptyLifecycleStopsWithoutTouchingAnything() {
        FakeDiagnostics diagnostics = new FakeDiagnostics();
        PlaybackLifecycle lifecycle = new PlaybackLifecycle(null, diagnostics, 42L);
        assertFalse(lifecycle.hasEngine());
        lifecycle.stop();
        assertEquals(42L, lifecycle.savedPosition());
        assertTrue(diagnostics.phases().isEmpty());
    }

    static final class FakeBackend implements PlaybackLifecycle.Backend {
        private final List<String> calls = new ArrayList<>();
        private final String failed;

        private FakeBackend(String failed) {
            this.failed = failed;
        }

        static FakeBackend throwOn(String failed) {
            return new FakeBackend(failed);
        }

        private void invoke(String name) {
            calls.add(name);
            if (name.equals(failed)) throw new IllegalStateException(name);
        }

        public long position() {
            invoke("position");
            return 5000L;
        }

        public void detach() {
            invoke("detach");
        }

        public void release() {
            invoke("release");
        }

        List<String> calls() {
            return calls;
        }
    }

    static final class FakeDiagnostics implements DiagnosticsSink {
        private final List<String> phases = new ArrayList<>();

        public void record(String phase, Throwable failure, String graphics) {
            phases.add(phase);
        }

        List<String> phases() {
            return phases;
        }
    }
}
