package top.liuwei.xbvr.data;

import static org.junit.Assert.*;
import java.util.*;
import org.json.JSONArray;
import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import top.liuwei.xbvr.domain.ServerProfile;

public class XbvrTaskMonitorTest {
    @Test public void reconnectsAndDropsOldSubscriptionsAfterServerDisconnect() throws Exception {
        MockWebServer server=new MockWebServer();
        CountDownLatch recovered=new CountDownLatch(1);
        List<String> events=new java.util.concurrent.CopyOnWriteArrayList<>();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener(){
            @Override public void onMessage(WebSocket socket,String text){
                if(text.startsWith("[1,"))socket.send("[2,42,{}]");
                else if(text.startsWith("[32,")){socket.send("[33,1,4]");socket.close(1001,"fixture restart");}
            }
        }));
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener(){
            @Override public void onMessage(WebSocket socket,String text){
                if(text.startsWith("[1,"))socket.send("[2,43,{}]");
                else if(text.startsWith("[32,")){
                    socket.send("[33,1,9]");
                    socket.send("[36,4,1,{},[],{\"name\":\"rescan\",\"locked\":false}]");
                    socket.send("[36,9,2,{},[],{\"name\":\"rescan\",\"locked\":true}]");
                }
            }
        }));server.start();
        XbvrTaskMonitor monitor=new XbvrTaskMonitor(new ServerProfile("qa",server.url("/").toString().replaceAll("/$",""),"","","",""),new XbvrTaskMonitor.Listener(){
            public void onConnection(boolean connected){}
            public void onTask(String task,boolean running,String message){events.add(task+":"+running);recovered.countDown();}
        });
        try {monitor.open();assertTrue("Task subscription must recover",recovered.await(8,TimeUnit.SECONDS));assertEquals(List.of("rescan:true"),events);assertEquals(2,server.getRequestCount());}
        finally {monitor.close();server.shutdown();}
    }
    @Test public void closingMonitorCancelsPendingReconnect() throws Exception {
        MockWebServer server=new MockWebServer();CountDownLatch disconnected=new CountDownLatch(1);
        server.enqueue(new MockResponse().setResponseCode(503));server.start();
        XbvrTaskMonitor monitor=new XbvrTaskMonitor(new ServerProfile("qa",server.url("/").toString().replaceAll("/$",""),"","","",""),new XbvrTaskMonitor.Listener(){
            public void onTask(String task,boolean running,String message){}
            public void onConnection(boolean connected){if(!connected)disconnected.countDown();}
        });
        try {monitor.open();assertTrue(disconnected.await(2,TimeUnit.SECONDS));monitor.close();assertNotNull(server.takeRequest(1,TimeUnit.SECONDS));assertNull(server.takeRequest(6,TimeUnit.SECONDS));}
        finally {monitor.close();server.shutdown();}
    }
    @Test public void gracefulServerCloseMarksDisconnected() throws Exception {
        MockWebServer server = new MockWebServer();
        CountDownLatch disconnected = new CountDownLatch(1);
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket socket,String message) {
                if(message.startsWith("[1,"))socket.close(1000,"fixture done");
            }
        }));
        server.start();
        XbvrTaskMonitor monitor = new XbvrTaskMonitor(new ServerProfile("qa",server.url("/").toString().replaceAll("/$",""),"","","",""),new XbvrTaskMonitor.Listener() {
            public void onTask(String task,boolean running,String message) {}
            public void onConnection(boolean connected) { if(!connected)disconnected.countDown(); }
        });
        try { monitor.open();assertTrue(disconnected.await(2,TimeUnit.SECONDS)); }
        finally {monitor.close();server.shutdown();}
    }
    @Test public void authenticatesWebSocketAndSubscribesBeforeReportingConnected() throws Exception {
        MockWebServer server = new MockWebServer();
        CountDownLatch subscribed = new CountDownLatch(1);
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket socket, String message) {
                if (message.startsWith("[1,")) socket.send("[2,42,{}]");
                else if (message.equals("[32,1,{},\"lock.change\"]")) socket.send("[33,1,4]");
            }
        }));
        server.start();
        XbvrTaskMonitor monitor = new XbvrTaskMonitor(new ServerProfile("qa",server.url("/").toString().replaceAll("/$",""),"player","playerpass","admin","secret"), new XbvrTaskMonitor.Listener() {
            public void onTask(String task,boolean running,String message) {}
            public void onConnection(boolean connected) { if (connected) subscribed.countDown(); }
        });
        try {
            monitor.open();
            assertTrue(subscribed.await(5,TimeUnit.SECONDS));
            RecordedRequest request=server.takeRequest(1,TimeUnit.SECONDS);
            assertEquals("/ws/",request.getPath());
            assertEquals(Credentials.basic("admin","secret"),request.getHeader("Authorization"));
            assertEquals("wamp.2.json",request.getHeader("Sec-WebSocket-Protocol"));
        } finally { monitor.close();server.shutdown(); }
    }
    @Test public void decodesOnlySubscribedLockEvents() throws Exception {
        List<String> observed = new ArrayList<>();
        XbvrTaskMonitor.Listener listener = new XbvrTaskMonitor.Listener() {
            public void onTask(String task, boolean running, String message) { observed.add(task + ":" + running); }
            public void onConnection(boolean connected) {}
        };
        Map<Long,String> subscriptions = Map.of(4L,"lock.change");
        XbvrTaskMonitor.decode(new JSONArray("[36,4,1,{},[],{\"name\":\"rescan\",\"locked\":true}]"), subscriptions, listener);
        XbvrTaskMonitor.decode(new JSONArray("[36,4,2,{},[],{\"name\":\"rescan\",\"locked\":false}]"), subscriptions, listener);
        XbvrTaskMonitor.decode(new JSONArray("[36,9,3,{},[],{\"name\":\"scrape\",\"locked\":true}]"), subscriptions, listener);
        assertEquals(List.of("rescan:true", "rescan:false"), observed);
    }
    @Test public void missingBooleanNeverMeansCompleted() throws Exception {
        List<String> observed = new ArrayList<>();
        XbvrTaskMonitor.Listener listener = new XbvrTaskMonitor.Listener() {
            public void onTask(String task, boolean running, String message) { observed.add(task); }
            public void onConnection(boolean connected) {}
        };
        Map<Long,String> subscriptions = Map.of(4L,"lock.change");
        for (String payload : List.of("[36,4,1,{},[],{\"name\":\"scrape\"}]", "[36,4,1,{},[],{\"name\":\"scrape\",\"locked\":\"false\"}]", "[36,4,1,{},[],{\"name\":\"other\",\"locked\":false}]", "[36]"))
            XbvrTaskMonitor.decode(new JSONArray(payload), subscriptions, listener);
        assertTrue(observed.isEmpty());
    }
}
