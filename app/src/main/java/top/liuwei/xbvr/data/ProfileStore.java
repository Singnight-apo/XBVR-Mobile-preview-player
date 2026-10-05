package top.liuwei.xbvr.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONArray;
import org.json.JSONObject;
import top.liuwei.xbvr.domain.ProfileRepository;
import top.liuwei.xbvr.domain.ServerProfile;

/**
 * Encrypted profile storage. Keeps the stored scheme byte-compatible: AndroidKeyStore alias
 * "xbvr-profiles", AES/GCM/NoPadding and Base64(IV) + ":" + Base64(ciphertext) under the "profiles"
 * key of the "local" preferences file.
 */
public final class ProfileStore implements ProfileRepository {
    private final SharedPreferences prefs;

    public ProfileStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("local", Context.MODE_PRIVATE);
    }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias("xbvr-profiles")) return (SecretKey) ks.getKey("xbvr-profiles", null);
        KeyGenerator gen = KeyGenerator.getInstance("AES", "AndroidKeyStore");
        gen.init(
                new KeyGenParameterSpec.Builder(
                                "xbvr-profiles",
                                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build());
        return gen.generateKey();
    }

    public void saveProfiles(JSONArray profiles) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key());
        prefs.edit()
                .putString(
                        "profiles",
                        Base64.encodeToString(c.getIV(), Base64.NO_WRAP)
                                + ":"
                                + Base64.encodeToString(
                                        c.doFinal(profiles.toString().getBytes(StandardCharsets.UTF_8)),
                                        Base64.NO_WRAP))
                .apply();
    }

    public JSONArray profiles() throws Exception {
        String s = prefs.getString("profiles", "");
        if (s.isBlank()) return new JSONArray();
        String[] a = s.split(":");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(a[0], 0)));
        return new JSONArray(
                new String(c.doFinal(Base64.decode(a[1], 0)), StandardCharsets.UTF_8));
    }

    public String activeId() {
        return prefs.getString("active", "");
    }

    public void activeId(String id) {
        prefs.edit().putString("active", id).apply();
    }

    /** Raw active profile with the previous first-entry fallback. */
    public JSONObject profile() throws Exception {
        JSONArray a = profiles();
        for (int i = 0; i < a.length(); i++)
            if (a.getJSONObject(i).optString("id").equals(activeId())) return a.getJSONObject(i);
        return a.length() > 0 ? a.getJSONObject(0) : null;
    }

    public ServerProfile currentProfile() throws Exception {
        JSONObject p = profile();
        return p == null ? null : ProfileJsonMapper.from(p);
    }

    public void saveProfile(ServerProfile value) throws Exception {
        JSONArray all = profiles();
        ProfileJsonMapper.put(all, value);
        saveProfiles(all);
    }

    @Override
    public List<ServerProfile> all() throws Exception {
        return ProfileJsonMapper.listFrom(profiles());
    }

    @Override
    public ServerProfile current() throws Exception {
        return currentProfile();
    }

    @Override
    public ServerProfile find(String id) throws Exception {
        return ProfileJsonMapper.find(profiles(), id);
    }

    @Override
    public void save(ServerProfile value) throws Exception {
        saveProfile(value);
    }

    @Override
    public void remove(String id) throws Exception {
        JSONArray all = profiles();
        JSONArray kept = new JSONArray();
        for (int i = 0; i < all.length(); i++) {
            JSONObject entry = all.optJSONObject(i);
            if (entry != null && !id.equals(entry.optString("id", ""))) kept.put(entry);
        }
        saveProfiles(kept);
    }

    @Override
    public void select(String id) {
        activeId(id);
    }
}
