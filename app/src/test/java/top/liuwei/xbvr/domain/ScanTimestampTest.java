package top.liuwei.xbvr.domain;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScanTimestampTest {
    @Test public void parsesServerFractionAndOffset() {
        assertEquals(ScanTimestamp.parse("2026-10-10T17:06:33.0234971Z"),
                ScanTimestamp.parse("2026-10-11T01:06:33.0234971+08:00"));
        assertTrue(ScanTimestamp.parse("2026-10-11T01:06:33.0234971+08:00") > 0);
    }
    @Test public void absentInvalidAndGoZeroAreUnknown() {
        for (String value : new String[]{null, "", "invalid", "0001-01-01T00:00:00Z"})
            assertEquals(-1, ScanTimestamp.parse(value));
    }
    @Test public void ageHandlesMinuteBoundaryAndFutureClock() {
        assertEquals(0, ScanTimestamp.ageMinutes(60000, 119999));
        assertEquals(1, ScanTimestamp.ageMinutes(60000, 120000));
        assertEquals(0, ScanTimestamp.ageMinutes(120000, 60000));
    }
}
