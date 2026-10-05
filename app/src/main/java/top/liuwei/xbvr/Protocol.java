package top.liuwei.xbvr;

import java.util.List;
import org.json.JSONObject;
import top.liuwei.xbvr.data.XbvrProtocol;
import top.liuwei.xbvr.domain.Models.Detail;
import top.liuwei.xbvr.domain.Models.Entry;
import top.liuwei.xbvr.domain.Models.Source;

/**
 * Temporary root facade over the data-layer protocol helpers so existing call sites and the
 * instrumentation reflection keep working until T22. New code must use {@link XbvrProtocol}.
 */
@Deprecated
public final class Protocol {
    private Protocol() {}

    public static String base(String input) {
        return XbvrProtocol.base(input);
    }

    public static String resolve(String base, String value) {
        return XbvrProtocol.resolve(base, value);
    }

    public static boolean sameOrigin(String a, String b) {
        return XbvrProtocol.sameOrigin(a, b);
    }

    public static void authorized(JSONObject j) {
        XbvrProtocol.authorized(j);
    }

    public static List<Entry> library(JSONObject j, String base) {
        return XbvrProtocol.library(j, base);
    }

    public static void metadata(Entry e, JSONObject j, String base) {
        XbvrProtocol.metadata(e, j, base);
    }

    public static void merge(List<Entry> entries, JSONObject normalized) {
        XbvrProtocol.merge(entries, normalized);
    }

    public static JSONObject normalized(Entry e) throws org.json.JSONException {
        return XbvrProtocol.normalized(e);
    }

    public static Detail detail(JSONObject j, String base, boolean hs) {
        return XbvrProtocol.detail(j, base, hs);
    }

    public static String hereUrl(String url) {
        return XbvrProtocol.hereUrl(url);
    }

    public static String fileId(String url) {
        return XbvrProtocol.fileId(url);
    }

    public static String identity(String url) {
        return XbvrProtocol.identity(url);
    }

    public static void fileMetadata(Source source, JSONObject file) {
        XbvrProtocol.fileMetadata(source, file);
    }
}
