package top.liuwei.xbvr.data;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import top.liuwei.xbvr.domain.ServerProfile;

/** Typed mapping for the stored profile JSON. JSON stays in the data layer. */
public final class ProfileJsonMapper {
    private ProfileJsonMapper() {}

    public static ServerProfile from(JSONObject json) {
        return new ServerProfile(
                json.optString("id", ""),
                json.optString("base", ""),
                json.optString("user", ""),
                json.optString("password", ""),
                json.optString("basicUser", ""),
                json.optString("basicPassword", ""));
    }

    /**
     * The six known fields only. This is the object the previous implementation wrote when a profile
     * was created or edited, so unknown fields on an edited profile are dropped exactly as before.
     */
    public static JSONObject toJson(ServerProfile profile) {
        try {
            return new JSONObject()
                    .put("id", profile.id)
                    .put("base", profile.base)
                    .put("user", profile.user)
                    .put("password", profile.password)
                    .put("basicUser", profile.basicUser)
                    .put("basicPassword", profile.basicPassword);
        } catch (JSONException impossible) {
            // Six non-null strings cannot fail to serialise; keep the callers free of a checked path.
            throw new IllegalStateException("profile serialisation failed", impossible);
        }
    }

    public static List<ServerProfile> listFrom(JSONArray array) {
        List<ServerProfile> out = new ArrayList<>();
        if (array == null) return out;
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json != null) out.add(from(json));
        }
        return out;
    }

    public static ServerProfile find(JSONArray array, String id) {
        if (array == null || id == null) return null;
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json != null && id.equals(json.optString("id", ""))) return from(json);
        }
        return null;
    }

    /**
     * Previous connection-save behaviour: replace every entry carrying the same id, otherwise append.
     * Matching is by id only; two servers with the same address stay separate entries.
     */
    public static void put(JSONArray all, ServerProfile profile) {
        JSONObject json = toJson(profile);
        boolean replaced = false;
        for (int i = 0; i < all.length(); i++) {
            JSONObject existing = all.optJSONObject(i);
            if (existing != null && profile.id.equals(existing.optString("id", ""))) {
                replace(all, i, json);
                replaced = true;
            }
        }
        if (!replaced) all.put(json);
    }

    private static void replace(JSONArray all, int index, JSONObject json) {
        try {
            all.put(index, json);
        } catch (JSONException impossible) {
            throw new IllegalStateException("profile replacement failed", impossible);
        }
    }
}
