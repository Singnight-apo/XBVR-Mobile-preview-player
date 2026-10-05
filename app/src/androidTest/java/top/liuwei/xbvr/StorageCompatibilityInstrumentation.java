package top.liuwei.xbvr;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import java.io.File;
import java.nio.file.Files;
import top.liuwei.xbvr.domain.Projection;
import top.liuwei.xbvr.domain.ServerProfile;

/**
 * Test APK only: real Android storage assertions for the split local stores. Runs against a dedicated
 * synthetic profile, snapshots the stored blob and active id first and restores them before
 * reporting. Never clears application data and never writes to a real profile.
 */
public final class StorageCompatibilityInstrumentation extends Instrumentation {
    private static final String PROBE_ID = "storage-compatibility-probe";
    private static final String PROBE_BASE = "http://10.0.2.2:18766";

    private String phase = "launch";
    private int checks;
    private SharedPreferences prefs;
    private String savedProfiles = "";
    private String savedActive = "";
    private File probe;
    private File probeTemp;
    private String sceneKey;
    private String fileKey;

    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    @Override
    public void onStart() {
        Bundle results = new Bundle();
        Context ctx = getTargetContext();
        prefs = ctx.getSharedPreferences("local", Context.MODE_PRIVATE);
        savedProfiles = prefs.getString("profiles", "");
        savedActive = prefs.getString("active", "");
        probe = new File(ctx.getFilesDir(), "library-" + PROBE_ID + ".json");
        probeTemp = new File(ctx.getFilesDir(), "library-" + PROBE_ID + ".json.tmp");
        Projection view = new Projection();
        try {
            Store store = new Store(ctx);

            phase = "storage.cache.missing";
            check("".equals(store.cache("storage-compatibility-absent")),
                    "A missing library cache did not read back as an empty string");

            phase = "storage.cache.propagation";
            deleteRecursively(probe);
            probe.mkdirs();
            new File(probe, "occupant").createNewFile();
            boolean propagated = false;
            try {
                store.cache(PROBE_ID, "{\"probe\":true}");
            } catch (Exception expected) {
                propagated = true;
            }
            check(propagated, "A failing library cache write was swallowed instead of propagating");

            phase = "storage.profile.keystore";
            store.saveProfile(new ServerProfile(PROBE_ID, PROBE_BASE, "", "", "", ""));
            ServerProfile stored = store.serverProfile(PROBE_ID);
            check(stored != null && PROBE_ID.equals(stored.id) && PROBE_BASE.equals(stored.base),
                    "AndroidKeyStore did not return the profile written to the encrypted store");

            phase = "storage.profile.fallback";
            store.current("storage-compatibility-missing-active");
            ServerProfile current = store.currentProfile();
            check(current != null && current.id.equals(store.serverProfiles().get(0).id),
                    "An invalid active id did not fall back to the first stored profile");
            store.current(PROBE_ID);
            check(PROBE_ID.equals(store.currentProfile().id),
                    "Selecting the synthetic profile did not make it current");

            phase = "storage.identity.isolation";
            sceneKey = store.playbackKey(PROBE_ID, PROBE_BASE + "/deovr/1");
            fileKey = store.playbackKey(PROBE_ID, PROBE_BASE + "/deovr/file/1");
            check(!sceneKey.equals(fileKey), "Scene and file identities collided inside the same profile");
            store.save(sceneKey, 1000, view, false);
            store.save(fileKey, 2000, view, false);
            check(store.position(sceneKey) == 1000 && store.position(fileKey) == 2000,
                    "Scene and file watch records were not stored independently");
            store.favorite(sceneKey, true);
            check(store.favorite(sceneKey) && !store.favorite(fileKey),
                    "Favourites were not isolated between the scene and its file");

            phase = "storage.position.clamp";
            store.save(sceneKey, -5, view, false);
            check(store.position(sceneKey) == 0, "The file save did not clamp a negative position");
            store.entryPosition(sceneKey, -5);
            check(store.position(sceneKey) == -5,
                    "The raw scene position was clamped although it must be written unchanged");

            phase = "storage.profile.remove";
            store.removeProfile(PROBE_ID);
            check(store.serverProfile(PROBE_ID) == null, "Removing the synthetic profile had no effect");

            restoreDeviceState();
            results.putBoolean("passed", true);
            results.putInt("checks", checks);
            results.putString("stream", "\nOK (" + checks + " storage checks).\n");
            finish(Activity.RESULT_OK, results);
        } catch (Exception | AssertionError failure) {
            restoreDeviceState();
            results.putBoolean("passed", false);
            results.putString("phase", phase);
            results.putString("exceptionClass", failure.getClass().getName());
            results.putInt("checks", checks);
            results.putString("stream", "\nFAIL at " + phase + ": " + failure.getClass().getName() + "\n");
            finish(Activity.RESULT_CANCELED, results);
        }
    }

    /**
     * Puts the device back exactly as it was found. Runs before finish() because finishing can end
     * the process before any finally block executes.
     */
    private void restoreDeviceState() {
        if (prefs == null) return;
        prefs.edit().putString("profiles", savedProfiles).putString("active", savedActive).apply();
        SharedPreferences.Editor cleanup = prefs.edit();
        for (String key : new String[] {sceneKey, fileKey}) {
            if (key == null) continue;
            cleanup.remove("pos:" + key).remove("view:" + key).remove("fav:" + key);
        }
        cleanup.apply();
        deleteRecursively(probe);
        deleteRecursively(probeTemp);
        File[] leftovers = probe == null ? null : probe.getParentFile().listFiles();
        if (leftovers != null) {
            for (File leftover : leftovers) {
                if (leftover.getName().startsWith("library-" + PROBE_ID)) deleteRecursively(leftover);
            }
        }
    }

    private void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        try {
            Files.deleteIfExists(file.toPath());
        } catch (Exception ignored) {
        }
    }
}
