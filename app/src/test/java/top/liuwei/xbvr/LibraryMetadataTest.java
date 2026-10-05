package top.liuwei.xbvr;
import top.liuwei.xbvr.domain.Models;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class LibraryMetadataTest {
    @Test public void inlineAndCachedMetadataAreRestoredByStableIdentity() throws Exception {
        String base="https://fixture/proxy";
        JSONObject j=new JSONObject("{scenes:[{name:'All',list:[{video_url:'deovr/1?dnt=true',thumbnailUrl:'https://images.test/a.jpg',paysite:{name:'Site'},actors:[{name:'Actor'}],categories:[{tag:{name:'Tag'}}]}]}]}");
        Models.Entry e=Protocol.library(j,base).get(0);
        assertEquals("Site",e.studio);assertEquals(Set.of("Actor"),e.actors);assertEquals(Set.of("Tag"),e.tags);
        assertEquals(base+"/img/700x/https://images.test/a.jpg",e.posterCandidates.get(0));
        j.put("_metadata",new JSONObject().put(Protocol.identity(base+"/heresphere/1"),new JSONObject("{studio:'Cached',actors:['A2'],tags:['T2'],metadataLoaded:true}")));
        e=Protocol.library(j,base).get(0);assertEquals("Cached",e.studio);assertEquals(Set.of("A2"),e.actors);assertEquals(Set.of("T2"),e.tags);assertTrue(e.metadataLoaded);
    }
    @Test public void incompleteInlineStudioStillNeedsEnrichment() throws Exception {
        Models.Entry e=Protocol.library(new JSONObject("{scenes:[{name:'All',list:[{video_url:'deovr/1',studio:'Studio'}]}]}"),"https://fixture/proxy").get(0);
        assertEquals("Studio",e.studio);assertFalse("Studio alone cannot establish that cast/tags are empty",e.metadataLoaded);
    }
    @Test public void restSitePreferredAndInvalidPostersCannotBecomeRequests() throws Exception {
        Models.Entry e=new Models.Entry();Protocol.metadata(e,new JSONObject("{site:'Site',studio:'Parent Studio',cast:[{name:'A'},{name:'A'}],tags:[{name:'T'},'Extra'],cover_url:'javascript:alert(1)',thumbnailUrl:'https://u:p@images.test/a'}"),"https://fixture/proxy");
        assertEquals("Site",e.studio);assertEquals(Set.of("A"),e.actors);assertEquals(Set.of("T","Extra"),e.tags);assertTrue(e.posterCandidates.isEmpty());
    }
    @Test public void jsonEncodedRestImagesUseCoverAlternatesOnly() throws Exception {
        Models.Entry e=new Models.Entry();JSONObject j=new JSONObject().put("images",new JSONArray().put(new JSONObject().put("type","cover").put("url","https://images.test/alternate.jpg")).put(new JSONObject().put("type","gallery").put("url","https://images.test/gallery.jpg")).toString());
        Protocol.metadata(e,j,"https://fixture/proxy");assertEquals(List.of("https://fixture/proxy/img/700x/https://images.test/alternate.jpg","https://images.test/alternate.jpg"),e.posterCandidates);
    }
    @Test public void absoluteSameOriginCoverUsesWebProxyThenOrigin() throws Exception {
        Models.Entry e=new Models.Entry();Protocol.metadata(e,new JSONObject().put("thumbnailUrl","https://fixture/proxy/cover/a.png"),"https://fixture/proxy");
        assertEquals(List.of("https://fixture/proxy/img/700x/https://fixture/proxy/cover/a.png","https://fixture/proxy/cover/a.png"),e.posterCandidates);
    }
    @Test public void relativeCoverRemainsDirectDespiteResolvedEntryPoster() throws Exception {
        Models.Entry e=Protocol.library(new JSONObject("{scenes:[{name:'All',list:[{video_url:'deovr/1',thumbnailUrl:'cover/a.png'}]}]}"),"https://fixture/proxy").get(0);
        assertEquals("https://fixture/proxy/cover/a.png",e.poster);assertEquals(List.of(e.poster),e.posterCandidates);
    }
    @Test public void existingXbvrImageProxyCannotBecomeNestedProxy() throws Exception {
        String url="https://fixture/proxy/img/700x/https://images.test/a.png";Models.Entry e=new Models.Entry();Protocol.metadata(e,new JSONObject().put("thumbnailUrl",url),"https://fixture/proxy");assertEquals(List.of(url),e.posterCandidates);
    }
    @Test public void overlappingGroupsRetainTheirIndependentOrder() throws Exception {
        JSONObject j=new JSONObject("{scenes:[{name:'All',list:[{video_url:'deovr/1'},{video_url:'deovr/2'}]},{name:'Added',list:[{video_url:'deovr/2'},{video_url:'deovr/1'}]}]}");
        List<Models.Entry> entries=Protocol.library(j,"https://fixture/proxy");
        assertEquals(2,entries.size());
        assertEquals(Integer.valueOf(1),entries.get(0).groupOrder.get("Added"));
        assertEquals(Integer.valueOf(0),entries.get(1).groupOrder.get("Added"));
    }
}
