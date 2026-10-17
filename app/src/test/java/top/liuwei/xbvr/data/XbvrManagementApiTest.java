package top.liuwei.xbvr.data;

import java.util.concurrent.TimeUnit;
import okhttp3.Credentials;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.json.JSONObject;
import org.junit.Test;
import top.liuwei.xbvr.domain.ServerProfile;
import static org.junit.Assert.*;

/** Loopback management fixtures only; never accesses the user's XBVR server. */
public class XbvrManagementApiTest {
    @Test public void addStorageRejectsOverflowingVolumeIdInsteadOfTruncatingIt() throws Exception {
        try(MockWebServer s=new MockWebServer();XbvrManagementApi a=api(s)){
            s.enqueue(json("{\"volumes\":[]}"));
            s.enqueue(new MockResponse().setResponseCode(200));
            s.enqueue(json("{\"volumes\":[{\"id\":4294967303,\"path\":\"/new\"}]}"));
            kind(XbvrManagementApi.Kind.INVALID_RESPONSE,()->a.addStorage("/new"));
        }
    }
    @Test public void abandoningReadDoesNotCancelSubmittedTask() throws Exception {
        try (MockWebServer s=new MockWebServer();XbvrManagementApi a=api(s)) {
            java.util.concurrent.ExecutorService io=java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                s.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
                java.util.concurrent.Future<?> read=io.submit(()->{try {a.state();fail("Read must be cancelled");}catch(java.io.IOException expected){}});
                request(s);
                s.enqueue(json("null").setBodyDelay(1,TimeUnit.SECONDS));
                java.util.concurrent.Future<?> task=io.submit(()->{try {a.rescan(null);}catch(java.io.IOException error){throw new RuntimeException(error);}});
                assertEquals("/proxy/api/task/rescan",request(s).getPath());
                a.cancelReads();read.get(3,TimeUnit.SECONDS);task.get(3,TimeUnit.SECONDS);
                assertEquals(2,s.getRequestCount());
            } finally {io.shutdownNow();}
        }
    }
    private XbvrManagementApi api(MockWebServer server) {
        return new XbvrManagementApi(new ServerProfile("fixture", server.url("/proxy/").toString(),
                "player-user", "player-secret", "proxy-user", "proxy-secret"));
    }
    private MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }
    private RecordedRequest request(MockWebServer s) throws Exception {
        RecordedRequest r = s.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(r);
        return r;
    }
    private void kind(XbvrManagementApi.Kind kind, Throwing action) throws Exception {
        try { action.run(); fail("Expected " + kind); }
        catch (XbvrManagementApi.ManagementException e) { assertEquals(kind, e.kind); }
    }
    private interface Throwing { void run() throws Exception; }

    @Test public void capabilitiesReadsApiSchemaWithoutPlayerCredentials() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            s.enqueue(json("{\"swagger\":\"2.0\",\"paths\":{\"/api/task/rescan\":{\"get\":{}}}}"));
            try (XbvrManagementApi a = api(s)) {
                assertTrue(a.capabilities().getJSONObject("paths").has("/api/task/rescan"));
                RecordedRequest r = request(s);
                assertEquals("/proxy/api.json", r.getPath()); assertEquals("GET", r.getMethod());
                assertEquals(0, r.getBodySize());
                assertEquals(Credentials.basic("proxy-user", "proxy-secret"), r.getHeader("Authorization"));
            }
        }
    }
    @Test public void capabilitiesRejectsMissingSchemaAndClassifiesAuthentication() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            s.enqueue(json("{}")); s.enqueue(new MockResponse().setResponseCode(403));
            try (XbvrManagementApi a = api(s)) {
                kind(XbvrManagementApi.Kind.INVALID_RESPONSE, a::capabilities);
                kind(XbvrManagementApi.Kind.AUTH, a::capabilities);
            }
        }
    }
    @Test public void emptySceneSearchNormalizesOfficialNullSlice() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            s.enqueue(json("{\"results\":0,\"scenes\":null}"));
            try (XbvrManagementApi a = api(s)) { assertEquals(0, a.searchScenes("missing").getJSONArray("scenes").length()); }
        }
    }
    @Test(timeout = 45000) public void taskReadTimeoutLeavesOneTriggerAndUnknownOutcome() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            s.enqueue(new MockResponse().setBody("null").setBodyDelay(31, TimeUnit.SECONDS));
            try (XbvrManagementApi a = api(s)) {
                long start = System.nanoTime();
                kind(XbvrManagementApi.Kind.NETWORK, () -> a.rescan(null));
                long elapsed = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);
                assertTrue("Timeout must be bounded", elapsed >= 25 && elapsed <= 38);
                assertEquals(1, s.getRequestCount());
            }
        }
    }
    @Test public void taskHtmlLoginPageIsNotAcceptedAsSuccess() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            s.enqueue(new MockResponse().setHeader("Content-Type", "text/html").setBody("<html>Login</html>"));
            try (XbvrManagementApi a = api(s)) {
                kind(XbvrManagementApi.Kind.INVALID_RESPONSE, a::refreshScenes); assertEquals(1, s.getRequestCount());
            }
        }
    }
    @Test public void missingScenePreventsMatchMutation() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            s.enqueue(json("{\"id\":0,\"scene_id\":\"\"}"));
            try (XbvrManagementApi a = api(s)) {
                kind(XbvrManagementApi.Kind.INVALID_RESPONSE, () -> a.match(8, "missing-scene"));
                assertEquals(1, s.getRequestCount());
            }
        }
    }
    @Test public void explicitInternalIdResolvesSceneAndVerifiesExternalIdentity() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            s.enqueue(json("{\"id\":42,\"scene_id\":\"site-external\"}"));
            s.enqueue(json("null")); s.enqueue(json("{\"id\":8,\"scene_id\":42}"));
            try (XbvrManagementApi a = api(s)) {
                a.match(8, "site-external", 42); request(s);
                assertEquals("site-external", new JSONObject(request(s).getBody().readUtf8()).getString("scene_id"));
            }
        }
    }
    @Test public void numericExternalIdCannotSilentlyMatchDifferentScene() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            s.enqueue(json("{\"id\":42,\"scene_id\":\"site-external\"}"));
            s.enqueue(json("null")); s.enqueue(json("{\"id\":8,\"scene_id\":42}"));
            try (XbvrManagementApi a = api(s)) {
                kind(XbvrManagementApi.Kind.VERIFICATION, () -> a.match(8, "42"));
                assertEquals(1, s.getRequestCount());
            }
        }
    }
    @Test public void retryAfterZeroNeverRepeatsTaskTrigger() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            s.enqueue(new MockResponse().setResponseCode(503).setHeader("Retry-After", "0"));
            s.enqueue(new MockResponse());
            try (XbvrManagementApi a = api(s)) {
                kind(XbvrManagementApi.Kind.SERVER, () -> a.rescan(null));
                assertEquals(1, s.getRequestCount());
            }
        }
    }
    @Test public void absentSiteOrUnknownEnabledStateNeverToggles() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            s.enqueue(json("[]")); s.enqueue(json("[{\"id\":\"site\"}]"));
            try (XbvrManagementApi a = api(s)) {
                kind(XbvrManagementApi.Kind.INVALID_RESPONSE, () -> a.toggleSite("site", true));
                kind(XbvrManagementApi.Kind.INVALID_RESPONSE, () -> a.toggleSite("site", true));
                assertEquals(2, s.getRequestCount());
                assertEquals("GET", request(s).getMethod()); assertEquals("GET", request(s).getMethod());
            }
        }
    }
    @Test public void rejectedPathIdentifiersNeverReachServer() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            s.enqueue(json("[]")); s.enqueue(json("[]"));
            try (XbvrManagementApi a = api(s)) {
                for (String id : new String[]{".", ".."}) {
                    try { a.toggleSite(id, true); fail("Dot segment must not change route"); }
                    catch (IllegalArgumentException expected) { }
                }
                assertEquals(0, s.getRequestCount());
            }
        }
    }

    @Test public void readsUseProxyBaseAndBasicWithoutPlayerCredentials() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            s.enqueue(json("{\"currentState\":{}}"));
            s.enqueue(json("[]")); s.enqueue(json("{\"volumes\":[]}"));
            try (XbvrManagementApi a = api(s)) {
                a.state(); a.sites(); a.storage();
                String[] paths = {"/proxy/api/options/state", "/proxy/api/options/sites", "/proxy/api/options/storage"};
                for (String path : paths) {
                    RecordedRequest r = request(s);
                    assertEquals(path, r.getPath()); assertEquals("GET", r.getMethod());
                    assertEquals(Credentials.basic("proxy-user", "proxy-secret"), r.getHeader("Authorization"));
                    assertEquals(0, r.getBodySize());
                    assertFalse(r.getPath().contains("player"));
                }
            }
        }
    }
    @Test public void listsAndSearchUseOfficialSchemasAndEncodedQuery() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            s.enqueue(json("[]")); s.enqueue(json("{\"results\":0,\"scenes\":[]}"));
            try (XbvrManagementApi a = api(s)) {
                a.files(false, "name.mp4"); a.searchScenes("a & b/中文");
                RecordedRequest list = request(s);
                assertEquals("POST", list.getMethod()); assertEquals("/proxy/api/files/list", list.getPath());
                JSONObject body = new JSONObject(list.getBody().readUtf8());
                assertEquals("unmatched", body.getString("state")); assertEquals("name.mp4", body.getString("filename"));
                assertFalse(body.has("login")); assertFalse(body.has("password"));
                assertEquals("a & b/中文", request(s).getRequestUrl().queryParameter("q"));
            }
        }
    }
    @Test public void taskRequestsAcceptEmptyResponsesAndUseOneRequestEach() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            for (int i = 0; i < 4; i++) s.enqueue(new MockResponse());
            try (XbvrManagementApi a = api(s)) {
                a.rescan(null); a.rescan(7); a.refreshScenes(); a.scrape("site & x");
                assertEquals("/proxy/api/task/rescan", request(s).getPath());
                assertEquals("/proxy/api/task/rescan/7", request(s).getPath());
                assertEquals("/proxy/api/task/scene-refresh", request(s).getPath());
                assertEquals("site & x", request(s).getRequestUrl().queryParameter("site"));
                assertEquals(4, s.getRequestCount());
            }
        }
    }
    @Test public void nullScrapeUsesEnabledSitesWithoutPlayerFields() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            s.enqueue(new MockResponse());
            try (XbvrManagementApi a = api(s)) { a.scrape(null); assertEquals("/proxy/api/task/scrape", request(s).getPath()); }
        }
    }
    @Test public void togglesFreshStateOnceThenVerifiesReadback() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            s.enqueue(json("[{\"id\":\"site\",\"is_enabled\":false}]"));
            s.enqueue(json("[{\"id\":\"site\",\"is_enabled\":true}]"));
            s.enqueue(json("[{\"id\":\"site\",\"is_enabled\":true}]"));
            try (XbvrManagementApi a = api(s)) {
                assertTrue(a.toggleSite("site", true).getJSONObject(0).getBoolean("is_enabled"));
                assertEquals("GET", request(s).getMethod()); assertEquals("PUT", request(s).getMethod());
                assertEquals("GET", request(s).getMethod()); assertEquals(3, s.getRequestCount());
            }
        }
    }
    @Test public void alreadyDesiredSiteDoesNotToggle() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            s.enqueue(json("[{\"id\":\"site\",\"is_enabled\":true}]"));
            try (XbvrManagementApi a = api(s)) { a.toggleSite("site", true); assertEquals(1, s.getRequestCount()); }
        }
    }
    @Test public void failedToggleReadbackNeverTogglesAgain() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            String disabled = "[{\"id\":\"site\",\"is_enabled\":false}]";
            s.enqueue(json(disabled)); s.enqueue(json("[]")); s.enqueue(json(disabled));
            try (XbvrManagementApi a = api(s)) {
                kind(XbvrManagementApi.Kind.VERIFICATION, () -> a.toggleSite("site", true));
                assertEquals(3, s.getRequestCount());
            }
        }
    }
    @Test public void matchResolvesExternalSceneAndVerifiesNumericInternalId() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            s.enqueue(json("{\"id\":42,\"scene_id\":\"site-external\"}"));
            s.enqueue(json("null")); s.enqueue(json("{\"id\":8,\"scene_id\":42}"));
            try (XbvrManagementApi a = api(s)) {
                a.match(8, "site-external");
                assertEquals("/proxy/api/scene/site-external", request(s).getPath());
                RecordedRequest match = request(s); assertEquals("/proxy/api/files/match", match.getPath());
                JSONObject body = new JSONObject(match.getBody().readUtf8());
                assertEquals("site-external", body.getString("scene_id")); assertEquals(8, body.getLong("file_id"));
                assertEquals("/proxy/api/files/file/8", request(s).getPath());
            }
        }
    }
    @Test public void matchWrongNumericReadbackIsNotReportedSuccessful() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            s.enqueue(json("{\"id\":42,\"scene_id\":\"site-external\"}"));
            s.enqueue(json("null")); s.enqueue(json("{\"id\":8,\"scene_id\":99}"));
            try (XbvrManagementApi a = api(s)) {
                kind(XbvrManagementApi.Kind.VERIFICATION, () -> a.match(8, "site-external")); assertEquals(3, s.getRequestCount());
            }
        }
    }
    @Test public void unmatchRequiresVerifiedNumericZero() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            s.enqueue(json("null")); s.enqueue(json("{\"id\":8,\"scene_id\":0}"));
            s.enqueue(json("null")); s.enqueue(json("{\"id\":8,\"scene_id\":\"0\"}"));
            try (XbvrManagementApi a = api(s)) {
                a.unmatch(8);
                RecordedRequest r = request(s); assertEquals("/proxy/api/files/unmatch", r.getPath());
                assertEquals(8, new JSONObject(r.getBody().readUtf8()).getLong("file_id")); request(s);
                kind(XbvrManagementApi.Kind.INVALID_RESPONSE, () -> a.unmatch(8));
            }
        }
    }
    @Test public void classifiesAuthUnsupportedAndHtmlWithoutLeakingBodies() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            s.enqueue(new MockResponse().setResponseCode(401).setBody("SECRET"));
            s.enqueue(new MockResponse().setResponseCode(404)); s.enqueue(json("<html>login</html>"));
            try (XbvrManagementApi a = api(s)) {
                kind(XbvrManagementApi.Kind.AUTH, a::state);
                kind(XbvrManagementApi.Kind.UNSUPPORTED, a::state);
                kind(XbvrManagementApi.Kind.INVALID_RESPONSE, a::state);
            }
        }
    }
    @Test public void redirectsNeverRepeatTaskOrSendCredentialsToTarget() throws Exception {
        try (MockWebServer s = new MockWebServer(); MockWebServer other = new MockWebServer()) {
            s.enqueue(new MockResponse().setResponseCode(307).setHeader("Location", other.url("/trigger")));
            try (XbvrManagementApi a = api(s)) {
                kind(XbvrManagementApi.Kind.UNSUPPORTED, () -> a.rescan(null));
                assertEquals(1, s.getRequestCount()); assertEquals(0, other.getRequestCount());
            }
        }
    }
    @Test public void disconnectAfterTaskRequestDoesNotRetry() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            s.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
            try (XbvrManagementApi a = api(s)) {
                kind(XbvrManagementApi.Kind.NETWORK, a::refreshScenes); assertEquals(1, s.getRequestCount());
            }
        }
    }
    @Test public void emptyAndMalformedReadResponsesAreRejected() throws Exception {
        try (MockWebServer s = new MockWebServer()) {
            s.enqueue(json("")); s.enqueue(json("{} trailing")); s.enqueue(json("{\"unexpected\":true}"));
            try (XbvrManagementApi a = api(s)) {
                kind(XbvrManagementApi.Kind.INVALID_RESPONSE, a::state);
                kind(XbvrManagementApi.Kind.INVALID_RESPONSE, a::state);
                kind(XbvrManagementApi.Kind.INVALID_RESPONSE, () -> a.searchScenes("test"));
            }
        }
    }

    // ---- storage add/remove (XBVR 0.4.40: POST /api/options/storage, DELETE /api/options/storage/{id}) ----

    private static final String VOLUME_SEVEN = "{\"id\":7,\"type\":\"local\",\"path\":\"/media/vr\","
            + "\"metadata\":\"\",\"last_scan\":\"2026-10-16T12:17:36.7715609+08:00\","
            + "\"is_available\":true,\"file_count\":12,\"unmatched_count\":1,\"total_size\":4200000000000}";
    private static final String VOLUME_NINE = "{\"id\":9,\"type\":\"local\",\"path\":\"/media/other\","
            + "\"is_available\":true,\"file_count\":5,\"unmatched_count\":0,\"total_size\":800}";

    private static String volumes(String... volumes) {
        return "{\"volumes\":[" + String.join(",", volumes) + "]}";
    }

    @Test public void addStoragePostsLocalPathOnceThenVerifiesExactPath() throws Exception {
        try (MockWebServer s = new MockWebServer(); XbvrManagementApi a = api(s)) {
            s.enqueue(json(volumes(VOLUME_NINE)));
            s.enqueue(new MockResponse().setResponseCode(200));
            s.enqueue(json(volumes(VOLUME_NINE, VOLUME_SEVEN)));
            JSONObject after = a.addStorage("/media/vr");
            assertEquals(2, after.getJSONArray("volumes").length());
            RecordedRequest pre = request(s);
            assertEquals("the pre-write id set is read first", "GET", pre.getMethod());
            RecordedRequest post = request(s);
            assertEquals("POST", post.getMethod());
            assertEquals("/proxy/api/options/storage", post.getPath());
            JSONObject body = new JSONObject(post.getBody().readUtf8());
            assertEquals("local", body.getString("type"));
            assertEquals("/media/vr", body.getString("path"));
            assertFalse("the cloud branch must never be reachable", body.has("token"));
            RecordedRequest read = request(s);
            assertEquals("GET", read.getMethod());
            assertEquals("/proxy/api/options/storage", read.getPath());
            assertEquals("one write between the two reads", 3, s.getRequestCount());
        }
    }

    @Test public void addStorageRejectsBlankPathBeforeAnyNetworkCall() throws Exception {
        try (MockWebServer s = new MockWebServer(); XbvrManagementApi a = api(s)) {
            for (String blank : new String[]{null, "", "   ", "\t"}) {
                try { a.addStorage(blank); fail("blank path must be rejected locally"); }
                catch (IllegalArgumentException expected) { }
            }
            assertEquals(0, s.getRequestCount());
        }
    }

    @Test public void addStorageWithoutExactPathReadbackIsVerificationAndWritesOnce() throws Exception {
        try (MockWebServer s = new MockWebServer(); XbvrManagementApi a = api(s)) {
            s.enqueue(json(volumes(VOLUME_NINE)));
            s.enqueue(new MockResponse().setResponseCode(200));
            // "/media/vr2" is only a prefix-related neighbour of the requested "/media/vr".
            s.enqueue(json(volumes(VOLUME_NINE, "{\"id\":2,\"path\":\"/media/vr2\"}")));
            kind(XbvrManagementApi.Kind.VERIFICATION, () -> a.addStorage("/media/vr"));
            assertEquals("no second write when verification fails", 3, s.getRequestCount());
        }
    }

    @Test public void addStorageAlreadyRegisteredPathIsRejectedWithoutWriting() throws Exception {
        try (MockWebServer s = new MockWebServer(); XbvrManagementApi a = api(s)) {
            s.enqueue(json(volumes(VOLUME_SEVEN)));
            kind(XbvrManagementApi.Kind.VERIFICATION, () -> a.addStorage("/media/vr"));
            assertEquals("an already registered path must not trigger a write", 1, s.getRequestCount());
        }
    }

    @Test public void addStorageServerReports200WithoutAddingNewIdIsVerification() throws Exception {
        try (MockWebServer s = new MockWebServer(); XbvrManagementApi a = api(s)) {
            // XBVR answers 200 after silently ignoring an unknown type: the list is unchanged,
            // so the requested path is still absent and the call must not be reported as success.
            s.enqueue(json(volumes(VOLUME_NINE)));
            s.enqueue(new MockResponse().setResponseCode(200));
            s.enqueue(json(volumes(VOLUME_NINE)));
            kind(XbvrManagementApi.Kind.VERIFICATION, () -> a.addStorage("/media/vr"));
            assertEquals(3, s.getRequestCount());
        }
    }

    @Test public void addStorageReusingAnExistingIdForTheNewPathIsNotSuccess() throws Exception {
        try (MockWebServer s = new MockWebServer(); XbvrManagementApi a = api(s)) {
            s.enqueue(json(volumes("{\"id\":7,\"path\":\"/media/other\"}")));
            s.enqueue(new MockResponse().setResponseCode(200));
            // The requested path now maps to an id that already existed before the write, so no
            // new volume was created and the add must be reported as unverified.
            s.enqueue(json(volumes("{\"id\":7,\"path\":\"/media/vr\"}")));
            kind(XbvrManagementApi.Kind.VERIFICATION, () -> a.addStorage("/media/vr"));
            assertEquals(3, s.getRequestCount());
        }
    }

    @Test public void addStorageDuplicateFolderErrorIsServerKindAndNeverRetried() throws Exception {
        try (MockWebServer s = new MockWebServer(); XbvrManagementApi a = api(s)) {
            s.enqueue(json(volumes(VOLUME_NINE)));
            s.enqueue(new MockResponse().setResponseCode(400)
                    .setBody("{\"error\":\"Folder already exists\"}"));
            kind(XbvrManagementApi.Kind.SERVER, () -> a.addStorage("/media/vr"));
            assertEquals(2, s.getRequestCount());
        }
    }

    @Test public void addStorageRetryAfterZeroNeverRepeatsTheWrite() throws Exception {
        try (MockWebServer s = new MockWebServer(); XbvrManagementApi a = api(s)) {
            s.enqueue(json(volumes(VOLUME_NINE)));
            s.enqueue(new MockResponse().setResponseCode(503).setHeader("Retry-After", "0"));
            s.enqueue(new MockResponse());
            kind(XbvrManagementApi.Kind.SERVER, () -> a.addStorage("/media/vr"));
            assertEquals(2, s.getRequestCount());
        }
    }

    @Test public void addStorageRedirectIsUnsupportedAndNotFollowed() throws Exception {
        try (MockWebServer s = new MockWebServer(); MockWebServer other = new MockWebServer();
             XbvrManagementApi a = api(s)) {
            s.enqueue(json(volumes(VOLUME_NINE)));
            s.enqueue(new MockResponse().setResponseCode(307).setHeader("Location", other.url("/elsewhere")));
            kind(XbvrManagementApi.Kind.UNSUPPORTED, () -> a.addStorage("/media/vr"));
            assertEquals(2, s.getRequestCount());
            assertEquals(0, other.getRequestCount());
        }
    }

    @Test public void removeStorageReadsVerifiesThenDeletesExactlyOnce() throws Exception {
        try (MockWebServer s = new MockWebServer(); XbvrManagementApi a = api(s)) {
            s.enqueue(json(volumes(VOLUME_SEVEN, VOLUME_NINE)));
            s.enqueue(new MockResponse().setResponseCode(200));
            s.enqueue(json(volumes(VOLUME_NINE)));
            JSONObject after = a.removeStorage(7, "/media/vr");
            assertEquals(1, after.getJSONArray("volumes").length());
            RecordedRequest pre = request(s);
            assertEquals("GET", pre.getMethod());
            assertEquals("/proxy/api/options/storage", pre.getPath());
            RecordedRequest delete = request(s);
            assertEquals("the fix must send a real DELETE, not a GET", "DELETE", delete.getMethod());
            assertEquals("/proxy/api/options/storage/7", delete.getPath());
            assertEquals("DELETE carries no body", 0, delete.getBodySize());
            RecordedRequest post = request(s);
            assertEquals("GET", post.getMethod());
            assertEquals(3, s.getRequestCount());
        }
    }

    @Test public void removeStorageStaleIdWithDifferentPathNeverDeletes() throws Exception {
        try (MockWebServer s = new MockWebServer(); XbvrManagementApi a = api(s)) {
            s.enqueue(json(volumes("{\"id\":7,\"path\":\"/media/moved\"}")));
            kind(XbvrManagementApi.Kind.VERIFICATION, () -> a.removeStorage(7, "/media/vr"));
            assertEquals("a mismatched path must not reach a destructive call", 1, s.getRequestCount());
        }
    }

    @Test public void removeStorageUnknownIdOrBlankExpectationNeverDeletes() throws Exception {
        try (MockWebServer s = new MockWebServer(); XbvrManagementApi a = api(s)) {
            s.enqueue(json(volumes(VOLUME_SEVEN)));
            kind(XbvrManagementApi.Kind.VERIFICATION, () -> a.removeStorage(8, "/media/vr"));
            s.enqueue(json(volumes(VOLUME_SEVEN)));
            kind(XbvrManagementApi.Kind.VERIFICATION, () -> a.removeStorage(7, "  "));
            s.enqueue(json(volumes(VOLUME_SEVEN)));
            kind(XbvrManagementApi.Kind.VERIFICATION, () -> a.removeStorage(7, null));
            assertEquals(3, s.getRequestCount());
        }
    }

    @Test public void removeStorageInvalidIdIsRejectedBeforeAnyNetworkCall() throws Exception {
        try (MockWebServer s = new MockWebServer(); XbvrManagementApi a = api(s)) {
            for (int invalid : new int[]{0, -1, Integer.MIN_VALUE}) {
                try { a.removeStorage(invalid, "/media/vr"); fail("id must be positive"); }
                catch (IllegalArgumentException expected) { }
            }
            assertEquals(0, s.getRequestCount());
        }
    }

    @Test public void removeStorageStillPresentAfterDeleteIsVerificationNotSuccess() throws Exception {
        try (MockWebServer s = new MockWebServer(); XbvrManagementApi a = api(s)) {
            s.enqueue(json(volumes(VOLUME_SEVEN)));
            s.enqueue(new MockResponse().setResponseCode(200));
            s.enqueue(json(volumes(VOLUME_SEVEN)));
            kind(XbvrManagementApi.Kind.VERIFICATION, () -> a.removeStorage(7, "/media/vr"));
            assertEquals("exactly one destructive attempt", 3, s.getRequestCount());
        }
    }

    @Test public void removeStorageMissingVolumeIsUnsupportedAndNotRetried() throws Exception {
        try (MockWebServer s = new MockWebServer(); XbvrManagementApi a = api(s)) {
            s.enqueue(json(volumes(VOLUME_NINE)));
            s.enqueue(new MockResponse().setResponseCode(404));
            kind(XbvrManagementApi.Kind.UNSUPPORTED, () -> a.removeStorage(9, "/media/other"));
            assertEquals(2, s.getRequestCount());
        }
    }
}
