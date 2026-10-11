package top.liuwei.xbvr.domain;

import java.time.OffsetDateTime;

/** Server scan timestamps, including Go's zero-time sentinel. */
public final class ScanTimestamp {
    private ScanTimestamp() { }
    public static long parse(String value) {
        try {
            OffsetDateTime time = OffsetDateTime.parse(value);
            return time.getYear() <= 1 ? -1 : time.toInstant().toEpochMilli();
        } catch (Exception ignored) { return -1; }
    }
    public static long ageMinutes(long timestamp, long now) {
        return Math.max(0, (now - timestamp) / 60000);
    }
}
