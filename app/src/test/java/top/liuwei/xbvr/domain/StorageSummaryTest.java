package top.liuwei.xbvr.domain;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Fixtures mirror the real XBVR 0.4.40 payload shape (field names and types) but use synthetic
 * paths and numbers; the user's server is never contacted from unit tests.
 */
public class StorageSummaryTest {
    private static final String VOLUME_SEVEN = "{\"id\":7,\"type\":\"local\",\"path\":\"/media/vr\","
            + "\"metadata\":\"\",\"last_scan\":\"2026-10-16T12:17:36.7715609+08:00\","
            + "\"is_available\":true,\"file_count\":12,\"unmatched_count\":1,\"total_size\":4200000000000}";
    private static final String VOLUME_NINE = "{\"id\":9,\"type\":\"local\",\"path\":\"/media/other\","
            + "\"metadata\":\"\",\"last_scan\":\"2026-10-16T12:16:22.8603512+08:00\","
            + "\"is_available\":false,\"file_count\":5,\"unmatched_count\":0,\"total_size\":800}";

    private static JSONObject snapshot(String volumesJson) throws Exception {
        return new JSONObject("{\"volumes\":" + volumesJson + "}");
    }

    @Test public void realShapeFixtureIsAggregatedOverVolumes() throws Exception {
        StorageSummary summary = StorageSummary.fromJson(snapshot("[" + VOLUME_SEVEN + "," + VOLUME_NINE + "]"));
        assertEquals(2, summary.volumes.size());
        assertEquals(17L, (long) summary.totalFiles);
        assertEquals(1L, (long) summary.unmatchedFiles);
        assertEquals(4200000000800L, (long) summary.totalBytes);
        StorageSummary.Volume first = summary.volumes.get(0);
        assertEquals(7, first.id);
        assertEquals("/media/vr", first.path);
        assertEquals("2026-10-16T12:17:36.7715609+08:00", first.lastScan);
        assertEquals(Boolean.TRUE, first.available);
        assertEquals(12L, (long) first.totalFiles);
        assertEquals(1L, (long) first.unmatchedFiles);
        assertEquals(4200000000000L, (long) first.totalBytes);
        assertEquals(Boolean.FALSE, summary.volumes.get(1).available);
    }

    @Test public void unknownCountMakesOnlyThatTotalUnknown() throws Exception {
        String noCounts = "{\"id\":1,\"path\":\"/media/a\",\"is_available\":true}";
        StorageSummary summary = StorageSummary.fromJson(snapshot("[" + noCounts + "," + VOLUME_SEVEN + "]"));
        assertEquals(2, summary.volumes.size());
        assertNull(summary.volumes.get(0).totalFiles);
        assertNull(summary.volumes.get(0).unmatchedFiles);
        assertNull(summary.volumes.get(0).totalBytes);
        assertNull("a single unknown volume count must not be reported as a partial sum", summary.totalFiles);
        assertNull(summary.unmatchedFiles);
        assertNull(summary.totalBytes);
        assertEquals(12L, (long) summary.volumes.get(1).totalFiles);
    }

    @Test public void missingAvailabilityStaysUnknownInsteadOfAvailable() throws Exception {
        StorageSummary summary = StorageSummary.fromJson(
                snapshot("[{\"id\":1,\"path\":\"/media/a\",\"file_count\":3,\"unmatched_count\":0,\"total_size\":1}]"));
        assertNull(summary.volumes.get(0).available);
        assertNotEquals(Boolean.TRUE, summary.volumes.get(0).available);
    }

    @Test public void emptyVolumeListAggregatesToZero() throws Exception {
        StorageSummary summary = StorageSummary.fromJson(snapshot("[]"));
        assertTrue(summary.volumes.isEmpty());
        assertEquals(0L, (long) summary.totalFiles);
        assertEquals(0L, (long) summary.unmatchedFiles);
        assertEquals(0L, (long) summary.totalBytes);
    }

    @Test public void missingVolumesArrayIsUnknownNotZero() throws Exception {
        StorageSummary summary = StorageSummary.fromJson(new JSONObject("{\"match_ohash\":true}"));
        assertTrue(summary.volumes.isEmpty());
        assertNull("an absent volume list must not look like an empty library", summary.totalFiles);
        assertNull(summary.unmatchedFiles);
        assertNull(summary.totalBytes);
        assertNull(StorageSummary.fromJson(null).totalFiles);
        assertNull(StorageSummary.fromJson(new JSONObject("{\"volumes\":\"nope\"}")).totalFiles);
    }

    @Test public void negativeFractionalAndStringCountsAreUnknown() throws Exception {
        StorageSummary summary = StorageSummary.fromJson(snapshot(
                "[{\"id\":1,\"path\":\"/a\",\"file_count\":-5,\"unmatched_count\":1.5,\"total_size\":\"12\"}]"));
        StorageSummary.Volume volume = summary.volumes.get(0);
        assertNull(volume.totalFiles);
        assertNull(volume.unmatchedFiles);
        assertNull(volume.totalBytes);
        assertNull(summary.totalFiles);
    }

    @Test public void outOfRangeAndOverflowingValuesAreUnknown() throws Exception {
        StorageSummary huge = StorageSummary.fromJson(snapshot(
                "[{\"id\":1,\"path\":\"/a\",\"file_count\":3,\"unmatched_count\":0,"
                        + "\"total_size\":99999999999999999999999}]"));
        assertNull("a value beyond long range is unknown", huge.volumes.get(0).totalBytes);
        assertNull(huge.totalBytes);

        String max = "{\"id\":1,\"path\":\"/a\",\"total_size\":9223372036854775807}";
        String maxAgain = "{\"id\":2,\"path\":\"/b\",\"total_size\":9223372036854775807}";
        StorageSummary overflow = StorageSummary.fromJson(snapshot("[" + max + "," + maxAgain + "]"));
        assertNull("an overflowing sum must be null, never a wrapped number", overflow.totalBytes);
    }

    @Test public void legalZeroIsPreserved() throws Exception {
        StorageSummary summary = StorageSummary.fromJson(
                snapshot("[{\"id\":4,\"path\":\"/empty\",\"is_available\":true,"
                        + "\"file_count\":0,\"unmatched_count\":0,\"total_size\":0}]"));
        assertEquals(0L, (long) summary.volumes.get(0).totalFiles);
        assertEquals(0L, (long) summary.totalFiles);
        assertEquals(0L, (long) summary.totalBytes);
    }

    @Test public void volumeWithoutUsableIdIsSkippedAndAggregatesStayUnknown() throws Exception {
        StorageSummary noId = StorageSummary.fromJson(snapshot("[{\"path\":\"/a\",\"file_count\":1}]"));
        assertTrue(noId.volumes.isEmpty());
        assertNull(noId.totalFiles);

        StorageSummary fractionalId = StorageSummary.fromJson(snapshot("[{\"id\":1.5,\"path\":\"/a\",\"file_count\":1}]"));
        assertTrue(fractionalId.volumes.isEmpty());
        assertNull(fractionalId.totalFiles);

        StorageSummary negativeId = StorageSummary.fromJson(snapshot("[{\"id\":-1,\"path\":\"/a\",\"file_count\":1}]"));
        assertTrue(negativeId.volumes.isEmpty());

        StorageSummary mixed = StorageSummary.fromJson(snapshot("[1,\"x\",{\"id\":2,\"path\":\"/b\",\"file_count\":4,"
                + "\"unmatched_count\":0,\"total_size\":8}]"));
        assertEquals("the readable volume is still exposed", 1, mixed.volumes.size());
        assertEquals(2, mixed.volumes.get(0).id);
        assertNull("but the aggregate cannot claim to be complete", mixed.totalFiles);
        assertNull(mixed.totalBytes);
    }

    @Test public void malformedInputNeverThrows() throws Exception {
        String[] payloads = {
                "{\"volumes\":{}}",
                "{\"volumes\":[null]}",
                "{\"volumes\":[[\"nested\"]]}",
                "{\"volumes\":[{\"id\":1,\"path\":7,\"last_scan\":[],\"is_available\":\"true\"}]}",
                "{\"volumes\":[{\"id\":1,\"path\":\"/a\",\"file_count\":{},\"total_size\":[]}]}"
        };
        for (String payload : payloads) {
            StorageSummary summary = StorageSummary.fromJson(new JSONObject(payload));
            assertNotNull(summary.volumes);
        }
        StorageSummary typed = StorageSummary.fromJson(
                new JSONObject("{\"volumes\":[{\"id\":1,\"path\":7,\"last_scan\":[],\"is_available\":\"true\"}]}"));
        assertNull("a non-string path is unknown, not coerced", typed.volumes.get(0).path);
        assertNull(typed.volumes.get(0).lastScan);
        assertNull("the string \"true\" is not a boolean availability", typed.volumes.get(0).available);
    }
}
