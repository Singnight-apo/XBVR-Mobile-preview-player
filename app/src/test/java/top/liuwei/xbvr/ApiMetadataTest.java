package top.liuwei.xbvr;

import okhttp3.mockwebserver.*;
import org.json.*;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class ApiMetadataTest {
    private static Api api(MockWebServer s)throws Exception{return new Api(new JSONObject().put("base",s.url("/proxy").toString()).put("user","fixture-user").put("password","fixture-secret"));}
    private static Models.Entry entry(Api a,int id){Models.Entry e=new Models.Entry();e.url=a.base+"/deovr/"+id+"?dnt=true";return e;}
    private static JSONObject load(Api a,List<Models.Entry> entries,JSONObject cache,boolean force)throws Exception {
        return a.libraryMetadata(entries,cache,force);
    }
    @Test public void restBatchDoesNotMutateEntriesOrSendPlayerCredentials()throws Exception {
        try(MockWebServer s=new MockWebServer()){s.enqueue(new MockResponse().setBody("{scenes:[{id:12,site:'Studio',cast:[{name:'Actor'}],tags:[{name:'Tag'}],cover_url:'https://images.test/a.jpg'},{id:99,site:'Outside'}]}"));s.start();Api a=api(s);
            try {Models.Entry e=entry(a,12);JSONObject result=load(a,List.of(e),null,false);assertEquals("",e.studio);JSONObject m=result.getJSONObject(Protocol.identity(e.url));assertEquals("Studio",m.getString("studio"));assertEquals(1,result.length());assertTrue(m.getBoolean("metadataLoaded"));RecordedRequest r=s.takeRequest(2,TimeUnit.SECONDS);assertEquals("/proxy/api/scene/list",r.getPath());JSONObject sent=new JSONObject(r.getBody().readUtf8());assertFalse(sent.has("login"));assertFalse(sent.has("password"));
                load(a,List.of(e),new JSONObject().put("_metadata",result),false);assertEquals(1,s.getRequestCount());
            }finally{a.client.dispatcher().executorService().shutdownNow();a.client.connectionPool().evictAll();}
        }
    }
    @Test public void blockedRestUsesOnlyOneDeovrMetadataRequestAndCachesIt()throws Exception {
        try(MockWebServer s=new MockWebServer()){s.enqueue(new MockResponse().setResponseCode(403));s.enqueue(new MockResponse().setBody("{authorized:1,paysite:{name:'Player Site'},actors:[{name:'A'}],categories:[{tag:{name:'T'}}]}"));s.start();Api a=api(s);
            try {Models.Entry e=entry(a,7);JSONObject result=load(a,List.of(e),null,false);assertEquals("Player Site",result.getJSONObject(Protocol.identity(e.url)).getString("studio"));assertEquals(2,s.getRequestCount());s.takeRequest();RecordedRequest r=s.takeRequest();assertEquals("/proxy/deovr/7?dnt=true",r.getPath());assertEquals("fixture-user",new JSONObject(r.getBody().readUtf8()).getString("login"));load(a,List.of(e),result,false);assertEquals(2,s.getRequestCount());
            }finally{a.client.dispatcher().executorService().shutdownNow();a.client.connectionPool().evictAll();}
        }
    }
    @Test public void batchPaginationPreservesQueryOffsetAndExplicitForceRefreshes()throws Exception {
        try(MockWebServer s=new MockWebServer()){
            JSONArray page=new JSONArray();for(int id=1;id<=200;id++)page.put(new JSONObject().put("id",id).put("site","Other"));
            s.enqueue(new MockResponse().setBody(new JSONObject().put("scenes",page).toString()));s.enqueue(new MockResponse().setBody("{scenes:[{id:201,site:'Page Two',cast:[],tags:[]}]}"));s.enqueue(new MockResponse().setBody("{scenes:[{id:201,site:'Updated',cast:[],tags:[]}]}"));s.start();Api a=api(s);
            try {Models.Entry e=entry(a,201);JSONObject result=load(a,List.of(e),null,false);assertEquals("Page Two",result.getJSONObject(Protocol.identity(e.url)).getString("studio"));assertEquals(0,new JSONObject(s.takeRequest().getBody().readUtf8()).getInt("offset"));assertEquals(200,new JSONObject(s.takeRequest().getBody().readUtf8()).getInt("offset"));JSONObject updated=load(a,List.of(e),result,true);assertEquals("Updated",updated.getJSONObject(Protocol.identity(e.url)).getString("studio"));assertEquals(3,s.getRequestCount());
            }finally{a.client.dispatcher().executorService().shutdownNow();a.client.connectionPool().evictAll();}
        }
    }
    @Test public void repeatedAuthFailuresStopFallbackAndDebounceCachedRetry()throws Exception {
        try(MockWebServer s=new MockWebServer()){
            s.enqueue(new MockResponse().setResponseCode(403));for(int i=0;i<3;i++)s.enqueue(new MockResponse().setBody("{authorized:-1}"));s.start();Api a=api(s);
            try {List<Models.Entry> entries=new ArrayList<>();for(int i=1;i<=9;i++)entries.add(entry(a,i));JSONObject result=load(a,entries,null,false);assertEquals(4,s.getRequestCount());assertEquals(9,result.length());for(Models.Entry e:entries){JSONObject cached=result.getJSONObject(Protocol.identity(e.url));assertFalse(cached.getBoolean("metadataLoaded"));assertTrue(cached.getLong("_retryAfter")>System.currentTimeMillis());}load(a,entries,result,false);assertEquals(4,s.getRequestCount());
            }finally{a.client.dispatcher().executorService().shutdownNow();a.client.connectionPool().evictAll();}
        }
    }
}
