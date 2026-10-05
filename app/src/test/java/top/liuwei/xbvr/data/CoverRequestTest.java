package top.liuwei.xbvr.data;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.Test;
import static org.junit.Assert.*;

/** Cover request behaviour over loopback fixtures only. */
public class CoverRequestTest {
    private final ExecutorService io =
            Executors.newFixedThreadPool(
                    4,
                    r -> {
                        Thread t = new Thread(r);
                        t.setDaemon(true);
                        return t;
                    });

    private CoverRequests requests(OkHttpClient client) {
        return new CoverRequests(client, io);
    }

    @Test public void fallsBackToTheNextCandidateWhenTheFirstFails() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(new MockResponse().setBody("cover-bytes"));
        server.start();
        try {
            CoverRequests requests = requests(new OkHttpClient());
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<byte[]> received = new AtomicReference<>();
            requests.request(
                    1,
                    "key",
                    List.of(server.url("/first").toString(), server.url("/second").toString()),
                    new CoverRequests.Completion() {
                        public void bytes(byte[] data) {
                            received.set(data);
                            done.countDown();
                        }

                        public void failed() {
                            done.countDown();
                        }
                    });
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals("cover-bytes", new String(received.get(), "UTF-8"));
            assertEquals(2, server.getRequestCount());
        } finally {
            server.shutdown();
        }
    }

    @Test public void reportsFailureWhenEveryCandidateFails() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(404));
        server.start();
        try {
            CoverRequests requests = requests(new OkHttpClient());
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Boolean> failed = new AtomicReference<>();
            requests.request(
                    1,
                    "key",
                    List.of(server.url("/a").toString(), server.url("/b").toString()),
                    new CoverRequests.Completion() {
                        public void bytes(byte[] data) {
                            failed.set(false);
                            done.countDown();
                        }

                        public void failed() {
                            failed.set(true);
                            done.countDown();
                        }
                    });
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals(Boolean.TRUE, failed.get());
        } finally {
            server.shutdown();
        }
    }

    @Test public void rejectsABodyOverTheTwelveMiBCap() throws Exception {
        MockWebServer server = new MockWebServer();
        byte[] oversized = new byte[CoverRequests.MAX_BYTES + 1];
        server.enqueue(new MockResponse().setBody(new Buffer().write(oversized)));
        server.start();
        try {
            CoverRequests requests = requests(new OkHttpClient());
            try {
                requests.fetch(server.url("/big").toString());
                fail("An oversized cover body must be rejected");
            } catch (IOException expected) {
                assertEquals("Cover too large", expected.getMessage());
            }
        } finally {
            server.shutdown();
        }
    }

    @Test public void acceptsABodyExactlyAtTheCap() throws Exception {
        MockWebServer server = new MockWebServer();
        byte[] atCap = new byte[CoverRequests.MAX_BYTES];
        server.enqueue(new MockResponse().setBody(new Buffer().write(atCap)));
        server.start();
        try {
            CoverRequests requests = requests(new OkHttpClient());
            assertEquals(CoverRequests.MAX_BYTES, requests.fetch(server.url("/cap").toString()).length);
        } finally {
            server.shutdown();
        }
    }

    @Test public void sameKeyIsRequestedOnlyOnce() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setBody("cover").setBodyDelay(300, TimeUnit.MILLISECONDS));
        server.start();
        try {
            CoverRequests requests = requests(new OkHttpClient());
            CountDownLatch done = new CountDownLatch(1);
            AtomicInteger completions = new AtomicInteger();
            CoverRequests.Completion counting =
                    new CoverRequests.Completion() {
                        public void bytes(byte[] data) {
                            completions.incrementAndGet();
                            done.countDown();
                        }

                        public void failed() {
                            completions.incrementAndGet();
                            done.countDown();
                        }
                    };
            String url = server.url("/same").toString();
            requests.request(1, "same-key", List.of(url), counting);
            requests.request(1, "same-key", List.of(url), counting);
            assertTrue(done.await(5, TimeUnit.SECONDS));
            Thread.sleep(400);
            assertEquals(1, completions.get());
            assertEquals(1, server.getRequestCount());
        } finally {
            server.shutdown();
        }
    }

    @Test public void differentEpochsRunIndependently() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setBody("one").setBodyDelay(200, TimeUnit.MILLISECONDS));
        server.enqueue(new MockResponse().setBody("two"));
        server.start();
        try {
            CoverRequests requests = requests(new OkHttpClient());
            CountDownLatch done = new CountDownLatch(2);
            AtomicInteger completions = new AtomicInteger();
            CoverRequests.Completion counting =
                    new CoverRequests.Completion() {
                        public void bytes(byte[] data) {
                            completions.incrementAndGet();
                            done.countDown();
                        }

                        public void failed() {
                            done.countDown();
                        }
                    };
            requests.request(1, "key-a", List.of(server.url("/a").toString()), counting);
            requests.request(2, "key-b", List.of(server.url("/b").toString()), counting);
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals(2, completions.get());
            assertEquals(2, server.getRequestCount());
            assertTrue(requests.live(1));
            assertTrue(requests.live(2));
        } finally {
            server.shutdown();
        }
    }

    @Test public void invalidatedEpochDropsItsResultAndStopsItsCandidates() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setBody("late").setBodyDelay(400, TimeUnit.MILLISECONDS));
        server.start();
        try {
            CoverRequests requests = requests(new OkHttpClient());
            AtomicInteger completions = new AtomicInteger();
            requests.request(
                    7,
                    "key",
                    List.of(server.url("/late").toString()),
                    new CoverRequests.Completion() {
                        public void bytes(byte[] data) {
                            completions.incrementAndGet();
                        }

                        public void failed() {
                            completions.incrementAndGet();
                        }
                    });
            requests.invalidate(7);
            assertFalse(requests.live(7));
            Thread.sleep(700);
            assertEquals(0, completions.get());
        } finally {
            server.shutdown();
        }
    }

    @Test public void clearDropsEveryLiveEpoch() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        try {
            CoverRequests requests = requests(new OkHttpClient());
            requests.request(3, "key", List.of(server.url("/x").toString()), new CoverRequests.Completion() {
                public void bytes(byte[] data) {
                }

                public void failed() {
                }
            });
            assertTrue(requests.live(3));
            requests.clear();
            assertTrue(requests.liveEpochs().isEmpty());
            assertFalse(requests.live(3));
        } finally {
            server.shutdown();
        }
    }
}
