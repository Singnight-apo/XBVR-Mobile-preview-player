package top.liuwei.xbvr.data;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import top.liuwei.xbvr.domain.ServerProfile;
import static org.junit.Assert.*;

/** Guards for the profile JSON mapping and the previous connection save behaviour. */
public class ProfileJsonMapperTest {
    private static final String ID_A = "11111111-1111-1111-1111-111111111111";
    private static final String ID_B = "22222222-2222-2222-2222-222222222222";

    private static JSONObject entry(String id, String base) throws Exception {
        return new JSONObject().put("id", id).put("base", base);
    }

    @Test public void mapsTheSixKnownFieldsBothWays() throws Exception {
        JSONObject json = new JSONObject()
                .put("id", ID_A)
                .put("base", "https://host/proxy")
                .put("user", "player")
                .put("password", "secret")
                .put("basicUser", "proxy")
                .put("basicPassword", "proxysecret");
        ServerProfile p = ProfileJsonMapper.from(json);
        assertEquals(ID_A, p.id);
        assertEquals("https://host/proxy", p.base);
        assertEquals("player", p.user);
        assertEquals("secret", p.password);
        assertEquals("proxy", p.basicUser);
        assertEquals("proxysecret", p.basicPassword);

        JSONObject back = ProfileJsonMapper.toJson(p);
        assertEquals(6, back.length());
        assertEquals("https://host/proxy", back.getString("base"));
        assertEquals("proxysecret", back.getString("basicPassword"));
    }

    @Test public void missingFieldsBecomeEmptyStrings() throws Exception {
        ServerProfile p = ProfileJsonMapper.from(entry(ID_A, "https://host"));
        assertEquals(ID_A, p.id);
        assertEquals("https://host", p.base);
        assertEquals("", p.user);
        assertEquals("", p.password);
        assertEquals("", p.basicUser);
        assertEquals("", p.basicPassword);
        assertEquals(6, ProfileJsonMapper.toJson(p).length());
    }

    @Test public void editedProfileKeepsOnlyTheSixKnownFields() throws Exception {
        ServerProfile p = new ServerProfile(ID_A, "https://host", "u", "p", "", "");
        JSONObject json = ProfileJsonMapper.toJson(p);
        assertFalse(json.has("unknown"));
        assertEquals(6, json.length());
    }

    @Test public void savingOneEntryKeepsOtherEntriesRawJson() throws Exception {
        JSONArray all = new JSONArray();
        all.put(entry(ID_A, "https://a.test").put("unknown", "keep-me"));
        all.put(entry(ID_B, "https://b.test"));

        ProfileJsonMapper.put(all, new ServerProfile(ID_B, "https://b.test/edited", "u", "", "", ""));

        assertEquals(2, all.length());
        assertEquals("keep-me", all.getJSONObject(0).optString("unknown"));
        assertEquals("https://b.test/edited", all.getJSONObject(1).optString("base"));
        assertEquals(ID_A, all.getJSONObject(0).optString("id"));
    }

    @Test public void putReplacesByIdAtTheSameIndex() throws Exception {
        JSONArray all = new JSONArray();
        all.put(entry(ID_A, "https://a.test").put("unknown", "edited-away"));
        all.put(entry(ID_B, "https://b.test"));

        ProfileJsonMapper.put(all, new ServerProfile(ID_A, "https://a.test", "player", "", "", ""));

        assertEquals(2, all.length());
        assertEquals("player", all.getJSONObject(0).optString("user"));
        assertEquals("", all.getJSONObject(0).optString("unknown"));
        assertEquals(ID_B, all.getJSONObject(1).optString("id"));
    }

    @Test public void putAppendsWhenTheIdIsNew() throws Exception {
        JSONArray all = new JSONArray();
        all.put(entry(ID_A, "https://a.test"));
        ProfileJsonMapper.put(all, new ServerProfile(ID_B, "https://a.test", "", "", "", ""));
        assertEquals(2, all.length());
        assertEquals(ID_B, all.getJSONObject(1).optString("id"));
    }

    @Test public void sameAddressIsNotDeduplicated() throws Exception {
        JSONArray all = new JSONArray();
        all.put(entry(ID_A, "https://same.test"));
        ProfileJsonMapper.put(all, new ServerProfile(ID_B, "https://same.test", "", "", "", ""));
        assertEquals(2, all.length());
        assertEquals(2, ProfileJsonMapper.listFrom(all).size());
    }

    @Test public void findAndListMatchTheStoredEntries() throws Exception {
        JSONArray all = new JSONArray();
        all.put(entry(ID_A, "https://a.test"));
        all.put(JSONObject.NULL);
        all.put(entry(ID_B, "https://b.test"));

        List<ServerProfile> list = ProfileJsonMapper.listFrom(all);
        assertEquals(2, list.size());
        assertEquals(ID_A, list.get(0).id);
        assertEquals("https://b.test", list.get(1).base);
        assertEquals(ID_B, ProfileJsonMapper.find(all, ID_B).id);
        assertNull(ProfileJsonMapper.find(all, "missing"));
        assertNull(ProfileJsonMapper.find(all, null));
        assertTrue(ProfileJsonMapper.listFrom(null).isEmpty());
    }

    @Test public void profileTextNeverExposesCredentials() {
        ServerProfile p = new ServerProfile(ID_A, "https://host", "player", "secret", "proxy", "proxysecret");
        String text = String.valueOf(p);
        assertFalse(text.contains("secret"));
        assertFalse(text.contains("player"));
        assertFalse(text.contains("proxy"));
        assertFalse(text.contains("https://host"));
    }
}
