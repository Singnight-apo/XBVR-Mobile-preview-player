package top.liuwei.xbvr;
import java.util.*;
import top.liuwei.xbvr.Models.Entry;
/** Pure selection: preserve server category order; OR tags and AND independent facets. */
public final class LibraryQuery {
 private LibraryQuery(){}
 public static List<Entry> select(List<Entry> entries,String category,String query,String studio,String actor,Set<String> tags){
  String needle=query==null?"":query.trim().toLowerCase(Locale.ROOT);List<Entry> out=new ArrayList<>();
  for(Entry e:entries){
   if(!"全部".equals(category)&&!e.groups.contains(category))continue;
   if(studio!=null&&!studio.isBlank()&&!studio.equalsIgnoreCase(e.studio))continue;
   if(actor!=null&&!actor.isBlank()&&e.actors.stream().noneMatch(actor::equalsIgnoreCase))continue;
   if(tags!=null&&!tags.isEmpty()&&e.tags.stream().noneMatch(t->tags.stream().anyMatch(t::equalsIgnoreCase)))continue;
   if(!needle.isEmpty()&&!text(e.title,needle)&&!text(e.studio,needle)&&e.actors.stream().noneMatch(t->text(t,needle))&&e.tags.stream().noneMatch(t->text(t,needle)))continue;
   out.add(e);
  }
  if(!"全部".equals(category))out.sort(Comparator.comparingInt(e->e.groupOrder.getOrDefault(category,Integer.MAX_VALUE)));
  return out;
 }
 private static boolean text(String value,String needle){return value!=null&&value.toLowerCase(Locale.ROOT).contains(needle);}
}
