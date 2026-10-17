package top.liuwei.xbvr.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Read-only projection of XBVR's {@code GET /api/options/storage}.
 *
 * <p>XBVR returns one entry per storage volume with its own counts and no aggregate, so the
 * totals here are computed over {@link #volumes}. Unknown and malformed values stay {@code null}
 * rather than being reported as zero: a missing count must never look like an empty folder, and a
 * missing availability flag must never look available. Known zero is preserved.
 *
 * <p>Field names follow the real payload ({@code file_count}, {@code unmatched_count},
 * {@code total_size}, {@code is_available}, {@code last_scan}). Non-integral, negative or
 * out-of-range numbers are treated as unknown, and no input can make this class throw.
 */
public final class StorageSummary {
    /** Volumes in server order (newest scan first). Never null; unmodifiable. */
    public final List<Volume> volumes;
    /** Sum over volumes, or null when any volume's own value is unknown. */
    public final Long totalFiles, unmatchedFiles, totalBytes;

    private StorageSummary(List<Volume> volumes, Long totalFiles, Long unmatchedFiles, Long totalBytes) {
        this.volumes = Collections.unmodifiableList(volumes);
        this.totalFiles = totalFiles;
        this.unmatchedFiles = unmatchedFiles;
        this.totalBytes = totalBytes;
    }

    /** Parses a storage snapshot; never throws and never invents data. */
    public static StorageSummary fromJson(JSONObject snapshot) {
        List<Volume> parsed = new ArrayList<>();
        JSONArray array = snapshot == null ? null : snapshot.optJSONArray("volumes");
        if (array == null) {
            // The volume list itself is unreadable: totals are unknown, not zero.
            return new StorageSummary(parsed, null, null, null);
        }
        boolean unknownEntry = false;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            Volume volume = item == null ? null : Volume.from(item);
            if (volume == null) {
                // A volume that cannot be represented makes every aggregate incomplete.
                unknownEntry = true;
                continue;
            }
            parsed.add(volume);
        }
        if (unknownEntry) return new StorageSummary(parsed, null, null, null);
        // An empty but present list legitimately aggregates to zero.
        return new StorageSummary(parsed, sum(parsed, 0), sum(parsed, 1), sum(parsed, 2));
    }

    private static Long sum(List<Volume> volumes, int field) {
        long total = 0;
        for (Volume volume : volumes) {
            Long value = field == 0 ? volume.totalFiles : field == 1 ? volume.unmatchedFiles : volume.totalBytes;
            if (value == null) return null;
            try { total = Math.addExact(total, value); }
            catch (ArithmeticException overflow) { return null; }
        }
        return total;
    }

    /** One storage volume; every unknown field is null instead of a fabricated default. */
    public static final class Volume {
        public final int id;
        public final String path, lastScan;
        public final Boolean available;
        public final Long totalFiles, unmatchedFiles, totalBytes;

        private Volume(int id, String path, String lastScan, Boolean available,
                       Long totalFiles, Long unmatchedFiles, Long totalBytes) {
            this.id = id;
            this.path = path;
            this.lastScan = lastScan;
            this.available = available;
            this.totalFiles = totalFiles;
            this.unmatchedFiles = unmatchedFiles;
            this.totalBytes = totalBytes;
        }

        /** Returns null when the entry has no usable id, since an unknown id cannot be addressed. */
        private static Volume from(JSONObject json) {
            Long id = integral(json.opt("id"));
            if (id == null || id < 0 || id > Integer.MAX_VALUE) return null;
            return new Volume(id.intValue(), text(json.opt("path")), text(json.opt("last_scan")),
                    bool(json.opt("is_available")),
                    count(json.opt("file_count")), count(json.opt("unmatched_count")),
                    count(json.opt("total_size")));
        }
    }

    private static String text(Object value) { return value instanceof String ? (String) value : null; }

    private static Boolean bool(Object value) { return value instanceof Boolean ? (Boolean) value : null; }

    /** Only whole in-range numbers count; fractions, numeric strings and overflow stay unknown. */
    private static Long integral(Object value) {
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte)
            return ((Number) value).longValue();
        return null;
    }

    private static Long count(Object value) {
        Long number = integral(value);
        return number != null && number >= 0 ? number : null;
    }
}
