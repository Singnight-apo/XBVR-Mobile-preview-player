package top.liuwei.xbvr.data;

import android.content.Context;
import android.content.SharedPreferences;
import top.liuwei.xbvr.domain.CoverSettings;
import top.liuwei.xbvr.domain.Projection;

/**
 * Owns the plain "local" preferences. Cover settings are exposed through the domain interface; the
 * playback and favourite records are read and written here and surfaced by PlaybackStore and
 * FavoriteStore.
 */
public final class LocalSettings implements CoverSettings {
    private final SharedPreferences prefs;

    public LocalSettings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("local", Context.MODE_PRIVATE);
    }

    @Override
    public int mode(String profileId) {
        return prefs.getInt("coverMode:" + profileId, 0);
    }

    @Override
    public void mode(String profileId, int mode) {
        prefs.edit().putInt("coverMode:" + profileId, mode).apply();
    }

    @Override
    public float inferredRatio(String profileId) {
        return prefs.getFloat("coverAuto:" + profileId, 0);
    }

    @Override
    public void inferredRatio(String profileId, float ratio) {
        prefs.edit().putFloat("coverAuto:" + profileId, ratio).apply();
    }

    @Override
    public void clearInferredRatio(String profileId) {
        prefs.edit().remove("coverAuto:" + profileId).apply();
    }

    public long position(String key) {
        return prefs.getLong("pos:" + key, 0);
    }

    /**
     * Watch timestamp in epoch millis. Records written before the key existed read back as 0, which
     * the continue-watching sort treats as "older than everything".
     */
    public long lastWatched(String key) {
        return prefs.getLong("seen:" + key, 0);
    }

    /** File playback save: the position is clamped exactly as the previous Store.save did. */
    public void save(String key, long position, Projection view, boolean manual) {
        SharedPreferences.Editor e =
                prefs.edit()
                        .putLong("pos:" + key, Math.max(0, position))
                        .putLong("seen:" + key, System.currentTimeMillis());
        String json = PlaybackJsonCodec.encode(view, manual);
        if (json != null) e.putString("view:" + key, json);
        e.apply();
    }

    public boolean restore(String key, Projection target) {
        return PlaybackJsonCodec.apply(prefs.getString("view:" + key, "{}"), target);
    }

    public String selectedSource(String entryKey) {
        return prefs.getString("source:" + entryKey, "");
    }

    public void selectedSource(String entryKey, String url) {
        prefs.edit().putString("source:" + entryKey, url).apply();
    }

    /** Scene-level resume position: written raw, without the clamp used by {@link #save}. */
    public void entryPosition(String entryKey, long position) {
        prefs.edit()
                .putLong("pos:" + entryKey, position)
                .putLong("seen:" + entryKey, System.currentTimeMillis())
                .apply();
    }

    public boolean favorite(String key) {
        return prefs.getBoolean("fav:" + key, false);
    }

    public void favorite(String key, boolean value) {
        prefs.edit().putBoolean("fav:" + key, value).apply();
    }
}
