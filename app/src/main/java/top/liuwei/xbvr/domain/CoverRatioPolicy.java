package top.liuwei.xbvr.domain;

/** Cover-ratio mode and cached-ratio rules for the poster wall. */
public final class CoverRatioPolicy {
    public static final float DEFAULT = 16f / 9f;

    private CoverRatioPolicy() {}

    public static int mode(int value) {
        return value >= 0 && value <= 3 ? value : 0;
    }

    public static float fixed(int value) {
        return value == 1 ? 1f : value == 2 ? 3f / 2f : DEFAULT;
    }

    public static boolean valid(float value) {
        return Float.isFinite(value) && value > 0;
    }

    public static float resolve(int value, float cached) {
        int mode = mode(value);
        return mode == 0 && valid(cached) ? cached : fixed(mode);
    }

    public static boolean changed(float current, float next) {
        return valid(next) && !(Math.abs(next - current) < .0001f);
    }

    public static boolean crop(int value) {
        return value != 0;
    }
}
