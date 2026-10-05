package top.liuwei.xbvr.domain;

/**
 * Minimal diagnostics port for the playback session and the renderer. It carries only the old
 * {@code PlaybackDiagnostics.record} contract: a phase, an optional failure and the optional
 * graphics string. The Android file/dialog implementation stays outside Domain; T21 replaces the
 * temporary adapter with the Data-layer store.
 */
public interface DiagnosticsSink {
    void record(String phase, Throwable failure, String graphics);
}
