package top.liuwei.xbvr.ui.library;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import top.liuwei.xbvr.domain.Models.Entry;
import top.liuwei.xbvr.domain.ResourceIdentity;
import static org.junit.Assert.*;

/**
 * Pure guard for the poster grid scroll target: identity first, stored index only as a clamped
 * fallback. No Android and no GridView; the native behaviour is covered by the device cases.
 */
public class ScrollTargetTest {
    private static Entry entry(String url) {
        Entry e = new Entry();
        e.url = url;
        return e;
    }

    private static List<Entry> entries(String... urls) {
        List<Entry> list = new ArrayList<>();
        for (String url : urls) list.add(entry(url));
        return list;
    }

    @Test
    public void identityMatchWinsOverTheStoredIndex() {
        List<Entry> visible =
                entries(
                        "https://host/proxy/deovr/1",
                        "https://host/proxy/deovr/2",
                        "https://host/proxy/deovr/3");
        String stored = ResourceIdentity.of("https://host/proxy/deovr/2");
        assertEquals(1, GridScrollRestorer.resolveIndex(visible, stored, 2));
    }

    @Test
    public void identityMatchPointsPastTheStoredIndex() {
        List<Entry> visible =
                entries("https://host/proxy/deovr/1", "https://host/proxy/deovr/2");
        String stored = ResourceIdentity.of("https://host/proxy/deovr/2");
        assertEquals(1, GridScrollRestorer.resolveIndex(visible, stored, 0));
    }

    @Test
    public void identityIgnoresSessionQueryParametersAndPortal() {
        List<Entry> visible =
                entries(
                        "https://host/proxy/deovr/1?dnt=true",
                        "https://host/proxy/heresphere/7?session=abc");
        String stored = ResourceIdentity.of("https://host/proxy/deovr/7");
        assertEquals(1, GridScrollRestorer.resolveIndex(visible, stored, 0));
    }

    @Test
    public void missingIdentityFallsBackToTheStoredIndex() {
        List<Entry> visible =
                entries(
                        "https://host/proxy/deovr/1",
                        "https://host/proxy/deovr/2",
                        "https://host/proxy/deovr/3");
        assertEquals(2, GridScrollRestorer.resolveIndex(visible, "https://host:443/x/scene:99", 2));
    }

    @Test
    public void missingIdentityClampsAnIndexPastTheEnd() {
        List<Entry> visible =
                entries("https://host/proxy/deovr/1", "https://host/proxy/deovr/2");
        assertEquals(1, GridScrollRestorer.resolveIndex(visible, "", 50));
    }

    @Test
    public void missingIdentityClampsANegativeIndexToTheFirstItem() {
        List<Entry> visible =
                entries("https://host/proxy/deovr/1", "https://host/proxy/deovr/2");
        assertEquals(0, GridScrollRestorer.resolveIndex(visible, "", -4));
    }

    @Test
    public void emptyStoredUrlFallsBackInsteadOfMatching() {
        List<Entry> visible = entries("https://host/proxy/deovr/1");
        assertEquals(0, GridScrollRestorer.resolveIndex(visible, "", 0));
    }

    @Test
    public void scrollTargetKeepsTheCapturedFields() {
        GridScrollRestorer.ScrollTarget target =
                new GridScrollRestorer.ScrollTarget("https://host:443/proxy/scene:4", 6, 12, null);
        assertEquals("https://host:443/proxy/scene:4", target.url);
        assertEquals(6, target.index);
        assertEquals(12, target.top);
        assertNull(target.nativeState);
    }
}
