package top.liuwei.xbvr.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** XBVR files use seconds; scraped scenes already use minutes. */
public final class DurationMinutes {
    private DurationMinutes() { }
    public static String format(double value, boolean seconds) {
        if (!Double.isFinite(value) || value <= 0) return null;
        double minutes = seconds ? value / 60.0 : value;
        return BigDecimal.valueOf(Math.max(0.1, minutes)).setScale(1, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }
}
