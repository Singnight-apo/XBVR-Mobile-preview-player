package top.liuwei.xbvr.domain;

import java.util.LinkedHashSet;

/** Selected library filters: category, search text, one studio, one actor and OR-matched tags. */
public final class LibraryFilterState {
    public String category = "全部", query = "", studio = "", actor = "";
    public final LinkedHashSet<String> tags = new LinkedHashSet<>();
    public int tab;

    public void clearFacets() {
        studio = "";
        actor = "";
        tags.clear();
    }

    public boolean hasFacets() {
        return !studio.isEmpty() || !actor.isEmpty() || !tags.isEmpty();
    }
}
