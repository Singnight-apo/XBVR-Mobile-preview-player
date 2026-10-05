package top.liuwei.xbvr.domain;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import top.liuwei.xbvr.domain.Models.Entry;
public class LibraryQueryTest {
 private Entry entry(String title,String studio,String actor,String...tags){Entry e=new Entry();e.title=title;e.studio=studio;e.actors.add(actor);e.tags.addAll(Arrays.asList(tags));e.groups.add("Default");return e;}
 private List<Entry> select(List<Entry> es,String q,String s,String a,Set<String> tags){return LibraryQuery.select(es,"全部",q,s,a,tags);}
 @Test public void searchFindsManufacturerEvenWhenAbsentFromTitle(){Entry e=entry("Scene One","North Labs","Ada","Outdoor");assertEquals(List.of(e),select(List.of(e),"nOrTh", "","",Set.of()));assertEquals(List.of(e),select(List.of(e),"Ada", "","",Set.of()));assertEquals(List.of(e),select(List.of(e),"Outdoor", "","",Set.of()));assertEquals(List.of(e),select(List.of(e),"  ", "","",Set.of()));}
 @Test public void sameFacetOrAcrossFacetsAndAndCancelRestores(){Entry a=entry("One","North Labs","Ada","Outdoor"),b=entry("Two","North Labs","Bea","Indoor"),c=entry("Three","South Labs","Ada","Indoor");List<Entry> all=List.of(a,b,c);assertEquals(List.of(a,b),select(all,"","North Labs","",Set.of("Indoor","Outdoor")));assertEquals(List.of(a),select(all,"","North Labs","Ada",Set.of("Indoor","Outdoor")));assertEquals(List.of(b),select(all,"","North Labs","",Set.of("Indoor")));assertEquals(all,select(all,"","","",Set.of()));assertTrue(select(all,"","South Labs","Bea",Set.of()).isEmpty());}
 @Test public void savedCategoryRetainsItsServerOrderWithoutChangingGlobalOrder(){Entry a=entry("A","S","X"),b=entry("B","S","X"),c=entry("C","S","X");for(Entry e:List.of(a,b,c))e.groups.add("Added date");a.groupOrder.put("Added date",2);b.groupOrder.put("Added date",0);c.groupOrder.put("Added date",1);a.tags.add("Keep");c.tags.add("Keep");assertEquals(List.of(b,c,a),LibraryQuery.select(List.of(a,b,c),"Added date","","","",Set.of()));assertEquals(List.of(a,b,c),select(List.of(a,b,c),"","","",Set.of()));assertEquals(List.of(c,a),LibraryQuery.select(List.of(a,b,c),"Added date","","","",Set.of("Keep")));}
 @Test public void activeFiltersDoNotMatchMissingMetadata(){Entry e=entry("One","North Labs","Ada","Red");Entry missing=new Entry();missing.title="Two";assertEquals(List.of(e),select(List.of(e,missing),"","North Labs","",Set.of()));assertEquals(List.of(e,missing),select(List.of(e,missing),"","","",Set.of()));}
}
