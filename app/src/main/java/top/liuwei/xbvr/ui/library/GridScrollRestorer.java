package top.liuwei.xbvr.ui.library;

import android.os.Parcelable;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.GridView;
import java.util.List;
import top.liuwei.xbvr.domain.Models.Entry;
import top.liuwei.xbvr.domain.ResourceIdentity;

/**
 * Owns the poster grid scroll position: capture, cancel, and the two-pass pre-draw restore. Android
 * UI only — it holds the GridView, the saved Parcelable and the listener; the identity-first target
 * selection is a pure function so it can be locked by a JVM test.
 */
public final class GridScrollRestorer {
    /** Live page references; the GridView changes on every page rebuild and the list is mutated. */
    public interface Page {
        GridView grid();

        List<Entry> visible();

        boolean isDestroyed();
    }

    /** url/index/top/nativeState snapshot of the poster grid. */
    public static final class ScrollTarget {
        public final String url;
        public final int index, top;
        public final Parcelable nativeState;

        public ScrollTarget(String url, int index, int top, Parcelable nativeState) {
            this.url = url;
            this.index = index;
            this.top = top;
            this.nativeState = nativeState;
        }
    }

    /** Identity first; when the item is gone, the previous index clamped to the list bounds. */
    static int resolveIndex(List<Entry> visible, String url, int index) {
        int found = -1;
        for (int i = 0; i < visible.size(); i++)
            if (ResourceIdentity.of(visible.get(i).url).equals(url)) {
                found = i;
                break;
            }
        return Math.max(0, found >= 0 ? found : Math.min(index, visible.size() - 1));
    }

    private final Page page;
    private GridView restoreGrid;
    private ViewTreeObserver.OnPreDrawListener restoreListener;
    private ScrollTarget queued;

    public GridScrollRestorer(Page page) {
        this.page = page;
    }

    public ScrollTarget capture() {
        return queued == null ? snapshot() : queued;
    }

    private ScrollTarget snapshot() {
        GridView target = page.grid();
        int index = target == null ? 0 : target.getFirstVisiblePosition();
        View first = target == null ? null : target.getChildAt(0);
        int top = first == null ? 0 : first.getTop() - target.getPaddingTop();
        List<Entry> shown = page.visible();
        String url =
                index >= 0 && index < shown.size()
                        ? ResourceIdentity.of(shown.get(index).url)
                        : "";
        Parcelable nativeState =
                target == null || first == null ? null : target.onSaveInstanceState();
        return new ScrollTarget(url, index, top, nativeState);
    }

    public void cancel() {
        if (restoreGrid != null && restoreListener != null) {
            ViewTreeObserver observer = restoreGrid.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnPreDrawListener(restoreListener);
        }
        restoreGrid = null;
        restoreListener = null;
        queued = null;
    }

    public void restore(ScrollTarget anchor) {
        cancel();
        final List<Entry> visible = page.visible();
        final GridView target = page.grid();
        if (anchor == null || target == null || visible.isEmpty()) return;
        queued = anchor;
        restoreGrid = target;
        // AbsListView.setSelectionFromTop only changes resurrection in touch mode, whereas
        // GridView's LAYOUT_SPECIFIC uses selectedPosition. Native state establishes the
        // SYNC layout path, which uses syncPosition and preserves the actual first row.
        restoreListener =
                new ViewTreeObserver.OnPreDrawListener() {
                    private boolean selected;
                    private int targetIndex;

                    @Override
                    public boolean onPreDraw() {
                        if (page.grid() != target || page.isDestroyed()) {
                            cancel();
                            return true;
                        }
                        if (selected) {
                            if (anchor.nativeState == null) {
                                View child =
                                        target.getChildAt(
                                                targetIndex - target.getFirstVisiblePosition());
                                if (child != null)
                                    target.scrollListBy(
                                            child.getTop()
                                                    - target.getPaddingTop()
                                                    - anchor.top);
                            }
                            cancel();
                            return true;
                        }
                        if (target.getHeight() == 0 || target.getChildCount() == 0) return true;
                        targetIndex = resolveIndex(visible, anchor.url, anchor.index);
                        selected = true;
                        if (anchor.nativeState != null) {
                            target.onRestoreInstanceState(anchor.nativeState);
                            target.setSelectionFromTop(targetIndex, anchor.top);
                        } else target.setSelection(targetIndex);
                        return false;
                    }
                };
        target.getViewTreeObserver().addOnPreDrawListener(restoreListener);
        target.requestLayout();
    }
}
