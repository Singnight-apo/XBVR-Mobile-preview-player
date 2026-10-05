package top.liuwei.xbvr.domain;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/** Stable XBVR object identity: media URLs may gain dnt or session query parameters between visits. */
public final class ResourceIdentity {
    private ResourceIdentity() {}

    public static String of(String url) {
        try {
            URI u = URI.create(url);
            String path = u.getRawPath();
            var m = Pattern.compile("^(.*)/(?:deovr|heresphere)/(file/)?([0-9]+)/?$").matcher(path);
            String object = null, prefix = "";
            if (m.matches()) {
                prefix = m.group(1);
                object = (m.group(2) == null ? "scene:" : "file:") + m.group(3);
            } else {
                m = Pattern.compile("^(.*)/api/dms/file/([0-9]+)(?:/.*)?$").matcher(path);
                if (m.matches()) {
                    prefix = m.group(1);
                    object = "file:" + m.group(2);
                }
            }
            if (object != null)
                return u.getScheme().toLowerCase(Locale.ROOT)
                        + "://"
                        + u.getHost().toLowerCase(Locale.ROOT)
                        + ":"
                        + port(u)
                        + prefix
                        + "/"
                        + object;
        } catch (Exception ignored) {
        }
        return url;
    }

    /** Playback key shared by positions, favourites, selected sources and saved views. */
    public static String playbackKey(String profileId, String url) {
        return profileId + ":" + of(url);
    }

    private static int port(URI u) {
        return u.getPort() != -1 ? u.getPort() : "https".equalsIgnoreCase(u.getScheme()) ? 443 : 80;
    }
}
