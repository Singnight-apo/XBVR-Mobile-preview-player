package top.liuwei.xbvr.data;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.Socket;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.SocketFactory;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;
import top.liuwei.xbvr.domain.ServerProfile;
import static org.junit.Assert.*;

/** Socket close can perform TLS I/O; lifecycle cleanup must leave the caller thread. */
public class ManagementCloseTest {
    @Test public void apiClosesPooledSocketOffLifecycleThread() throws Exception {
        verifyClose(false);
    }

    @Test public void monitorClosesPooledSocketOffLifecycleThread() throws Exception {
        verifyClose(true);
    }

    private void verifyClose(boolean monitor) throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("{}"));
            CountDownLatch closed = new CountDownLatch(1);
            AtomicReference<Thread> closeThread = new AtomicReference<>();
            SocketFactory sockets = new SocketFactory() {
                @Override public Socket createSocket() {
                    return new Socket() {
                        @Override public synchronized void close() throws IOException {
                            try { super.close(); }
                            finally { closeThread.set(Thread.currentThread()); closed.countDown(); }
                        }
                    };
                }
                @Override public Socket createSocket(String h,int p) { throw new UnsupportedOperationException(); }
                @Override public Socket createSocket(String h,int p,InetAddress l,int lp) { throw new UnsupportedOperationException(); }
                @Override public Socket createSocket(InetAddress h,int p) { throw new UnsupportedOperationException(); }
                @Override public Socket createSocket(InetAddress h,int p,InetAddress l,int lp) { throw new UnsupportedOperationException(); }
            };
            OkHttpClient client = new OkHttpClient.Builder().socketFactory(sockets).build();
            try (Response response = client.newCall(new Request.Builder().url(server.url("/")).build()).execute()) {
                response.body().string();
            }
            assertEquals(1, client.connectionPool().idleConnectionCount());
            ServerProfile profile = new ServerProfile("fixture",server.url("/").toString(),"","","","");
            Object owner = monitor ? new XbvrTaskMonitor(profile, new XbvrTaskMonitor.Listener() {
                public void onTask(String task,boolean running,String message) {}
                public void onConnection(boolean connected) {}
            }) : new XbvrManagementApi(profile);
            Field field = owner.getClass().getDeclaredField(monitor ? "client" : "readClient");
            field.setAccessible(true);
            field.set(owner, client);
            Thread lifecycleThread = Thread.currentThread();
            if (monitor) ((XbvrTaskMonitor) owner).close();
            else ((XbvrManagementApi) owner).close();
            assertTrue("Pooled socket must eventually close",closed.await(3,TimeUnit.SECONDS));
            assertNotSame("TLS socket cleanup must not run on lifecycle caller",lifecycleThread,closeThread.get());
        }
    }
}
