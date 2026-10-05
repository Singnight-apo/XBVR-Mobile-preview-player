package top.liuwei.xbvr.ui.library;

import java.util.ArrayList;
import java.util.List;
import top.liuwei.xbvr.domain.CoverRatioPolicy;
import top.liuwei.xbvr.domain.LibraryFilterState;
import top.liuwei.xbvr.domain.Models.Entry;
import top.liuwei.xbvr.domain.ServerProfile;

/**
 * Mutable state of the library page. It holds no Android type: {@code R.string} mapping, views and
 * {@code Bundle} bridging stay in the activity. Message is the semantic status, not a resource id.
 */
public final class LibraryUiState {
    /** Semantic status line; the view maps it to the previous R.string values. */
    public enum Message {
        NONE,
        CONNECTING,
        CACHE_UPDATING,
        METADATA_PARTIAL,
        METADATA_FAILED,
        CONNECTION_FAILED,
        CACHED_OFFLINE
    }

    public ServerProfile profile;
    public final List<Entry> entries = new ArrayList<>();
    public final List<Entry> visible = new ArrayList<>();
    public final LibraryFilterState filter = new LibraryFilterState();
    public boolean busy, metadataBusy, failed;
    public Message message = Message.NONE;
    public int coverMode;
    public float coverRatio = CoverRatioPolicy.DEFAULT;
    public boolean coverInferred;
}
