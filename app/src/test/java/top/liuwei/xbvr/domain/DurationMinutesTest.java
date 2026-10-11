package top.liuwei.xbvr.domain;
import org.junit.Test;
import static org.junit.Assert.*;
public class DurationMinutesTest {
    @Test public void fileSecondsAndSceneMinutesUseDifferentUnits() {
        assertEquals("60", DurationMinutes.format(3600, true));
        assertEquals("60", DurationMinutes.format(60, false));
        assertEquals("2.1", DurationMinutes.format(125, true));
        assertEquals("0.3", DurationMinutes.format(20, true));
    }
    @Test public void invalidOrAbsentDurationStaysUnknown() {
        for(double value:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})
            assertNull(DurationMinutes.format(value,true));
        assertEquals("0.1",DurationMinutes.format(1,true));
    }
}
