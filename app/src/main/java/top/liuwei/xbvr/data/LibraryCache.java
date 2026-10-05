package top.liuwei.xbvr.data;

import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Directory cache on the filesystem: library-&lt;id&gt;.json written through a .tmp sibling. */
public final class LibraryCache {
    private final Context ctx;

    public LibraryCache(Context context) {
        ctx = context.getApplicationContext();
    }

    public void write(String id, String json) throws Exception {
        Path p = ctx.getFilesDir().toPath().resolve("library-" + id + ".json");
        Path temp = p.resolveSibling(p.getFileName() + ".tmp");
        Files.write(temp, json.getBytes(StandardCharsets.UTF_8));
        Files.move(temp, p, StandardCopyOption.REPLACE_EXISTING);
    }

    public String read(String id) {
        try {
            return new String(
                    Files.readAllBytes(ctx.getFilesDir().toPath().resolve("library-" + id + ".json")),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
