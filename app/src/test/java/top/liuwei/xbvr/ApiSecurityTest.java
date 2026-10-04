package top.liuwei.xbvr;

import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONObject;
import org.junit.Test;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Loopback fixtures only: no user server, library data, or real credentials. */
public class ApiSecurityTest {
    private static Api api(String base)throws Exception{return new Api(new JSONObject().put("base",base).put("id","test").put("user","fixture-user").put("password","fixture-password").put("basicUser","proxy-fixture").put("basicPassword","proxy-password"));}
    private static void close(Api a){if(a!=null){a.client.dispatcher().executorService().shutdownNow();a.client.connectionPool().evictAll();}}

    @Test public void sameOriginRedirectRetainsProtocolFields()throws Exception {
        try(MockWebServer s=new MockWebServer()) {
            s.enqueue(new MockResponse().setResponseCode(307).setHeader("Location","/final"));
            s.enqueue(new MockResponse().setBody("{\"authorized\":1}"));s.start();Api a=api(s.url("/").toString());
            try{a.json(s.url("/deovr").toString(),false,null);s.takeRequest();RecordedRequest request=s.takeRequest(2,TimeUnit.SECONDS);assertNotNull(request);assertEquals("/final",request.getPath());JSONObject sent=new JSONObject(request.getBody().readUtf8());assertEquals("fixture-user",sent.getString("login"));assertEquals("fixture-password",sent.getString("password"));assertFalse(sent.has("username"));assertFalse(sent.has("isFavorite"));}finally{close(a);}
        }
    }
    @Test public void crossOrigin307NeverReceivesApplicationCredentials()throws Exception {
        try(MockWebServer origin=new MockWebServer();MockWebServer other=new MockWebServer()) {
            other.enqueue(new MockResponse().setBody("{\"authorized\":1}"));other.start();
            origin.enqueue(new MockResponse().setResponseCode(307).setHeader("Location",other.url("/leak")));origin.start();Api a=api(origin.url("/").toString());
            try{try{a.json(origin.url("/deovr").toString(),false,null);fail("Cross-origin credential POST must stop");}catch(java.io.IOException expected){}assertEquals(0,other.getRequestCount());assertEquals(1,origin.getRequestCount());}finally{close(a);}
        }
    }
    @Test public void mediaRedirectCannotForwardProxyAuthorization()throws Exception {
        try(MockWebServer origin=new MockWebServer();MockWebServer other=new MockWebServer()) {
            other.enqueue(new MockResponse().setBody("fixture"));other.start();
            origin.enqueue(new MockResponse().setResponseCode(302).setHeader("Location",other.url("/video")));origin.start();Api a=api(origin.url("/").toString());
            try{try(okhttp3.Response r=a.client.newCall(new okhttp3.Request.Builder().url(origin.url("/video")).build()).execute()){assertEquals(200,r.code());RecordedRequest first=origin.takeRequest(2,TimeUnit.SECONDS),second=other.takeRequest(2,TimeUnit.SECONDS);assertNotNull(first);assertNotNull(second);assertTrue(first.getHeader("Authorization").startsWith("Basic "));assertNull(second.getHeader("Authorization"));}}finally{close(a);}
        }
    }
}
