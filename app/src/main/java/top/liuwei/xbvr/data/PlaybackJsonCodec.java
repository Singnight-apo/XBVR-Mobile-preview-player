package top.liuwei.xbvr.data;

import org.json.JSONObject;
import top.liuwei.xbvr.domain.Projection;

/**
 * Saved view-state JSON. Field names, defaults, exception handling and the partial-update order are
 * carried over from the previous Store.save/restore without reinterpretation.
 */
public final class PlaybackJsonCodec {
    private PlaybackJsonCodec() {}

    /** Returns null when the state cannot be encoded, matching the previous silent failure. */
    public static String encode(Projection p, boolean override) {
        try {
            JSONObject j = new JSONObject();
            j.put("kind", p.kind)
                    .put("layout", p.layout)
                    .put("capture", p.capture)
                    .put("eye", p.eye)
                    .put("yaw", p.yaw)
                    .put("pitch", p.pitch)
                    .put("fov", p.viewFov)
                    .put("cx", p.centerX)
                    .put("cy", p.centerY)
                    .put("radius", p.radius)
                    .put("rotation", p.rotation)
                    .put("mirror", p.mirror)
                    .put("half", p.halfPacked)
                    .put("override", override);
            return j.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Applies a stored value onto target and reports whether it was a manual format. Assignments are
     * made in the original order, so a failure part-way leaves the same partial update behind.
     */
    public static boolean apply(String stored, Projection target) {
        try {
            JSONObject j = new JSONObject(stored);
            boolean manual = j.optBoolean("override");
            if (manual) {
                target.kind = j.getInt("kind");
                target.layout = j.getInt("layout");
                target.capture = j.getInt("capture");
                target.known = true;
                target.reason = "使用已保存的手动格式";
                target.halfPacked = j.optBoolean("half");
            }
            target.eye = j.optInt("eye", 0);
            target.yaw = (float) j.optDouble("yaw", 0);
            target.pitch = (float) j.optDouble("pitch", 0);
            target.viewFov = (float) j.optDouble("fov", 75);
            target.centerX = (float) j.optDouble("cx", .5);
            target.centerY = (float) j.optDouble("cy", .5);
            target.radius = (float) j.optDouble("radius", 1);
            target.rotation = (float) j.optDouble("rotation", 0);
            target.mirror = j.optBoolean("mirror");
            return manual;
        } catch (Exception e) {
            return false;
        }
    }
}
