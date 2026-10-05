package top.liuwei.xbvr.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

/** JVM coverage for the Data-layer redaction and the bounded report size (T21). */
public final class DiagnosticsStoreTest {
    private static final int MAX_BYTES = 30 * 1024;

    @Test
    public void safeRemovesSensitiveUrlTokenAndAuthorizationSamples() {
        String url = "open https://synthetic.invalid/stream/1?token=QA_SECRET_URL_41 now";
        String token = "token=QA_SECRET_TOKEN_83";
        String authorization = "Authorization: Bearer QA_SECRET_TOKEN_83";
        String basic = "Authorization: Basic dXNlcjpwYXNz";

        String cleaned = DiagnosticsStore.safe(url + "\n" + token + "\n" + authorization + "\n" + basic, 4000);

        assertFalse(cleaned.contains("synthetic.invalid"));
        assertFalse(cleaned.contains("QA_SECRET_URL_41"));
        assertFalse(cleaned.contains("QA_SECRET_TOKEN_83"));
        assertFalse(cleaned.contains("dXNlcjpwYXNz"));
        assertTrue(cleaned.contains("[URL omitted]"));
        assertTrue(cleaned.contains("[credentials omitted]"));
        assertEquals(3, countOccurrences(cleaned, "[credentials omitted]"));
    }

    @Test
    public void safeKeepsOrdinaryDiagnosticText() {
        String ordinary = "shader.fragment\nGL_RENDERER: Synthetic GL\n  at top.liuwei.xbvr.media.VrRenderer.fail(VrRenderer.java:1)";

        assertEquals(ordinary, DiagnosticsStore.safe(ordinary, 4000));
    }

    @Test
    public void safeTruncatesToTheRequestedLength() {
        String value = DiagnosticsStore.safe("0123456789", 4);

        assertEquals("0123", value);
        assertEquals("", DiagnosticsStore.safe(null, 10));
    }

    @Test
    public void boundedCapsLargeReportsAtThirtyKilobytes() {
        StringBuilder value = new StringBuilder();
        while (value.length() < MAX_BYTES * 2) value.append("0123456789");

        byte[] bytes = DiagnosticsStore.bounded(value.toString());

        assertTrue(bytes.length <= MAX_BYTES);
        String text = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(text.endsWith("\n…report truncated at 30KB\n"));
    }

    @Test
    public void boundedKeepsSmallReportsUnchanged() {
        byte[] bytes = DiagnosticsStore.bounded("small report");

        assertArrayEquals("small report".getBytes(StandardCharsets.UTF_8), bytes);
    }

    @Test
    public void boundedTruncatesOnAUtf8Boundary() {
        StringBuilder value = new StringBuilder();
        while (value.length() < MAX_BYTES) value.append("测");

        byte[] bytes = DiagnosticsStore.bounded(value.toString());

        assertTrue(bytes.length <= MAX_BYTES);
        String text = new String(bytes, StandardCharsets.UTF_8);
        assertFalse(text.contains("\uFFFD"));
        assertTrue(text.endsWith("\n…report truncated at 30KB\n"));
    }

    private static int countOccurrences(String value, String needle) {
        int count = 0;
        int index = value.indexOf(needle);
        while (index >= 0) {
            count++;
            index = value.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
