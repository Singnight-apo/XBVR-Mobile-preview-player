package top.liuwei.xbvr.domain;

import org.junit.Test;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

/** Characterisation of the selected filter state and its interaction with LibraryQuery. */
public class LibraryFilterStateTest {
    private static Models.Entry entry(String title, String studio, String actor, String... tags) {
        Models.Entry e = new Models.Entry();
        e.title = title;
        e.studio = studio;
        e.actors.add(actor);
        e.tags.addAll(List.of(tags));
        e.groups.add("Default");
        return e;
    }

    @Test public void defaultsMatchTheLibraryStartState() {
        LibraryFilterState s = new LibraryFilterState();
        assertEquals("全部", s.category);
        assertEquals("", s.query);
        assertEquals("", s.studio);
        assertEquals("", s.actor);
        assertEquals(0, s.tab);
        assertTrue(s.tags.isEmpty());
        assertFalse(s.hasFacets());
    }

    @Test public void clearFacetsKeepsCategoryQueryAndTab() {
        LibraryFilterState s = new LibraryFilterState();
        s.category = "Added date";
        s.query = "north";
        s.tab = 2;
        s.studio = "North Labs";
        s.actor = "Ada";
        s.tags.add("Outdoor");
        assertTrue(s.hasFacets());
        s.clearFacets();
        assertFalse(s.hasFacets());
        assertEquals("Added date", s.category);
        assertEquals("north", s.query);
        assertEquals(2, s.tab);
        assertTrue(s.tags.isEmpty());
    }

    @Test public void hasFacetsIsTrueForAnySingleFacet() {
        LibraryFilterState s = new LibraryFilterState();
        s.studio = "North Labs";
        assertTrue(s.hasFacets());
        s.clearFacets();
        s.actor = "Ada";
        assertTrue(s.hasFacets());
        s.clearFacets();
        s.tags.add("Outdoor");
        assertTrue(s.hasFacets());
    }

    @Test public void tagsKeepInsertionOrder() {
        LibraryFilterState s = new LibraryFilterState();
        s.tags.add("Outdoor");
        s.tags.add("Indoor");
        s.tags.add("Outdoor");
        assertEquals(List.of("Outdoor", "Indoor"), List.copyOf(s.tags));
    }

    @Test public void stateDrivesLibraryQueryWithoutNewAlgorithms() {
        Models.Entry a = entry("One", "North Labs", "Ada", "Outdoor");
        Models.Entry b = entry("Two", "North Labs", "Bea", "Indoor");
        Models.Entry c = entry("Three", "South Labs", "Ada", "Indoor");
        List<Models.Entry> all = List.of(a, b, c);

        LibraryFilterState s = new LibraryFilterState();
        assertEquals(all, LibraryQuery.select(all, s.category, s.query, s.studio, s.actor, s.tags));

        s.studio = "North Labs";
        s.tags.add("Indoor");
        s.tags.add("Outdoor");
        // Tags are OR-matched inside the facet and AND-ed against the other facets.
        assertEquals(List.of(a, b), LibraryQuery.select(all, s.category, s.query, s.studio, s.actor, s.tags));
        s.tags.clear();
        s.tags.add("Indoor");
        assertEquals(List.of(b), LibraryQuery.select(all, s.category, s.query, s.studio, s.actor, s.tags));

        s.clearFacets();
        s.query = "south";
        assertEquals(List.of(c), LibraryQuery.select(all, s.category, s.query, s.studio, s.actor, s.tags));
    }

    @Test public void clearedFacetsRestoreEveryEntry() {
        Models.Entry a = entry("One", "North Labs", "Ada", "Outdoor");
        Models.Entry b = entry("Two", "South Labs", "Bea", "Indoor");
        List<Models.Entry> all = List.of(a, b);
        LibraryFilterState s = new LibraryFilterState();
        s.studio = "North Labs";
        s.actor = "Ada";
        s.tags.add("Outdoor");
        assertEquals(List.of(a), LibraryQuery.select(all, s.category, s.query, s.studio, s.actor, s.tags));
        s.clearFacets();
        assertEquals(all, LibraryQuery.select(all, s.category, s.query, s.studio, s.actor, s.tags));
        assertEquals(Set.of(), Set.copyOf(s.tags));
    }
}
