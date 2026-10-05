package top.liuwei.xbvr;

import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONObject;
import org.junit.Test;
import top.liuwei.xbvr.data.XbvrApi;
import top.liuwei.xbvr.data.XbvrProtocol;
import top.liuwei.xbvr.domain.ServerProfile;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Loopback fixtures only: no user server, library data, or real credentials. */
public class ApiSecurityTest {
    private static XbvrApi api(String base)throws Exception{return new XbvrApi(new ServerProfile("test",base,"fixture-user","fixture-password","proxy-fixture","proxy-password"));}
    private static void close(XbvrApi a){if(a!=null){a.client.dispatcher().executorService().shutdownNow();a.client.connectionPool().evictAll();}}

    @Test public void sameOriginRedirectRetainsProtocolFields()throws Exception {
        try(MockWebServer s=new MockWebServer()) {
            s.enqueue(new MockResponse().setResponseCode(307).setHeader("Location","/final"));
            s.enqueue(new MockResponse().setBody("{\"authorized\":1}"));s.start();XbvrApi a=api(s.url("/").toString());
            try{a.json(s.url("/deovr").toString(),false,null);s.takeRequest();RecordedRequest request=s.takeRequest(2,TimeUnit.SECONDS);assertNotNull(request);assertEquals("/final",request.getPath());JSONObject sent=new JSONObject(request.getBody().readUtf8());assertEquals("fixture-user",sent.getString("login"));assertEquals("fixture-password",sent.getString("password"));assertFalse(sent.has("username"));assertFalse(sent.has("isFavorite"));}finally{close(a);}
        }
    }
    @Test public void repeated307And308KeepThePostBodyAndCredentials()throws Exception {
        try(MockWebServer s=new MockWebServer()) {
            s.enqueue(new MockResponse().setResponseCode(307).setHeader("Location","/second"));
            s.enqueue(new MockResponse().setResponseCode(308).setHeader("Location","/final"));
            s.enqueue(new MockResponse().setBody("{\"authorized\":1}"));s.start();XbvrApi a=api(s.url("/").toString());
            try{
                a.json(s.url("/deovr").toString(),false,null);
                RecordedRequest first=s.takeRequest(2,TimeUnit.SECONDS),second=s.takeRequest(2,TimeUnit.SECONDS),third=s.takeRequest(2,TimeUnit.SECONDS);
                assertEquals("/deovr",first.getPath());assertEquals("/second",second.getPath());assertEquals("/final",third.getPath());
                for(RecordedRequest hop:new RecordedRequest[]{first,second,third}){
                    JSONObject sent=new JSONObject(hop.getBody().readUtf8());
                    assertEquals("fixture-user",sent.getString("login"));
                    assertEquals("fixture-password",sent.getString("password"));
                }
            }finally{close(a);}
        }
    }
    @Test public void crossOrigin307NeverReceivesApplicationCredentials()throws Exception {
        try(MockWebServer origin=new MockWebServer();MockWebServer other=new MockWebServer()) {
            other.enqueue(new MockResponse().setBody("{\"authorized\":1}"));other.start();
            origin.enqueue(new MockResponse().setResponseCode(307).setHeader("Location",other.url("/leak")));origin.start();XbvrApi a=api(origin.url("/").toString());
            try{try{a.json(origin.url("/deovr").toString(),false,null);fail("Cross-origin credential POST must stop");}catch(java.io.IOException expected){}assertEquals(0,other.getRequestCount());assertEquals(1,origin.getRequestCount());}finally{close(a);}
        }
    }
    @Test public void mediaRedirectCannotForwardProxyAuthorization()throws Exception {
        try(MockWebServer origin=new MockWebServer();MockWebServer other=new MockWebServer()) {
            other.enqueue(new MockResponse().setBody("fixture"));other.start();
            origin.enqueue(new MockResponse().setResponseCode(302).setHeader("Location",other.url("/video")));origin.start();XbvrApi a=api(origin.url("/").toString());
            try{try(okhttp3.Response r=a.client.newCall(new okhttp3.Request.Builder().url(origin.url("/video")).build()).execute()){assertEquals(200,r.code());RecordedRequest first=origin.takeRequest(2,TimeUnit.SECONDS),second=other.takeRequest(2,TimeUnit.SECONDS);assertNotNull(first);assertNotNull(second);assertTrue(first.getHeader("Authorization").startsWith("Basic "));assertNull(second.getHeader("Authorization"));}}finally{close(a);}
        }
    }
    @Test public void proxyBasicIsSentOnlyForTheConfiguredOrigin()throws Exception {
        try(MockWebServer origin=new MockWebServer();MockWebServer other=new MockWebServer()) {
            origin.enqueue(new MockResponse().setBody("{\"authorized\":1}"));other.enqueue(new MockResponse().setBody("fixture"));
            origin.start();other.start();XbvrApi a=api(origin.url("/").toString());
            try{
                a.client.newCall(new okhttp3.Request.Builder().url(origin.url("/same")).build()).execute().close();
                a.client.newCall(new okhttp3.Request.Builder().url(other.url("/other")).build()).execute().close();
                assertTrue(origin.takeRequest(2,TimeUnit.SECONDS).getHeader("Authorization").startsWith("Basic "));
                assertNull(other.takeRequest(2,TimeUnit.SECONDS).getHeader("Authorization"));
            }finally{close(a);}
        }
    }
    @Test public void mediaQueryParametersArePreserved()throws Exception {
        try(MockWebServer s=new MockWebServer()) {
            s.enqueue(new MockResponse().setBody("fixture"));s.start();XbvrApi a=api(s.url("/").toString());
            try(okhttp3.Response r=a.client.newCall(new okhttp3.Request.Builder().url(s.url("/video.mp4?token=a%2Fb&dnt=true")).build()).execute()){
                assertEquals(200,r.code());
                assertEquals("/video.mp4?token=a%2Fb&dnt=true",s.takeRequest(2,TimeUnit.SECONDS).getPath());
            }finally{close(a);}
        }
    }
    @Test public void sameOriginTreatsDefaultAndExplicitPortsAsEqual() {
        assertTrue(XbvrProtocol.sameOrigin("http://host.test/proxy","http://host.test:80/scene"));
        assertTrue(XbvrProtocol.sameOrigin("https://host.test","https://host.test:443/a"));
        assertFalse(XbvrProtocol.sameOrigin("http://host.test","http://host.test:8080/a"));
        assertFalse(XbvrProtocol.sameOrigin("http://host.test","https://host.test/a"));
        assertFalse(XbvrProtocol.sameOrigin("https://host.test","https://user:pass@host.test/a"));
    }
}
