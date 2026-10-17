package top.liuwei.xbvr.data;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import top.liuwei.xbvr.domain.ServerProfile;

/** XBVR backend management. Call on an IO executor; player credentials never enter this API. */
public final class XbvrManagementApi implements AutoCloseable {
    public enum Kind { UNSUPPORTED, AUTH, NETWORK, INVALID_RESPONSE, VERIFICATION, SERVER }

    /** Safe messages deliberately omit response bodies, URLs, account details and request payloads. */
    public static final class ManagementException extends IOException {
        public final Kind kind;
        public final int statusCode;

        private ManagementException(Kind kind, String message, int statusCode) {
            super(message);
            this.kind = kind;
            this.statusCode = statusCode;
        }
    }

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final HttpUrl base;
    private final OkHttpClient client;
    private final OkHttpClient readClient;
    private boolean closed;

    public XbvrManagementApi(ServerProfile profile) {
        HttpUrl parsed = HttpUrl.parse(profile.base);
        if (parsed == null || !parsed.username().isEmpty() || !parsed.password().isEmpty()
                || parsed.query() != null || parsed.fragment() != null)
            throw new IllegalArgumentException("管理地址必须是无账号、查询参数或片段的 HTTP(S) 地址");
        base = parsed;
        // Even GET task routes mutate the server. Never repeat a request or follow a redirect.
        client = HttpTransport.jsonClient(HttpTransport.client(profile.base, profile.basicUser, profile.basicPassword))
                .newBuilder().retryOnConnectionFailure(false).callTimeout(35, TimeUnit.SECONDS)
                // A pooled socket may have expired at the server while idle. With retries off,
                // reusing it can lose the first task submission. Use a fresh management socket.
                .connectionPool(new ConnectionPool(0, 1, TimeUnit.SECONDS))
                .addNetworkInterceptor(chain -> {
                    Response response = chain.proceed(chain.request());
                    // OkHttp retries 503 + Retry-After: 0 even when connection retries are off.
                    // Its default for a 503 without that header is to return, preserving one send.
                    return response.code() == 503
                            ? response.newBuilder().removeHeader("Retry-After").build() : response;
                }).build();
        // Read-only queries can reuse sockets and recover from an expired idle connection.
        // A separate dispatcher lets navigation cancel reads without touching server mutations.
        readClient=client.newBuilder().dispatcher(new okhttp3.Dispatcher())
            .connectionPool(new ConnectionPool()).retryOnConnectionFailure(true).build();
    }
    public void cancelReads() {readClient.dispatcher().cancelAll();}

    public JSONObject state() throws IOException {
        JSONObject result = object(get(url("api", "options", "state")));
        if (result.optJSONObject("currentState") == null) throw invalid();
        return result;
    }

    /** Read-only Swagger/OpenAPI document used to gate controls before any mutation. */
    public JSONObject capabilities() throws IOException {
        JSONObject result = object(get(url("api.json")));
        if (result.optJSONObject("paths") == null) throw invalid();
        return result;
    }

    public JSONArray sites() throws IOException {
        return array(get(url("api", "options", "sites")));
    }

    public JSONObject storage() throws IOException {
        JSONObject result = object(get(url("api", "options", "storage")));
        if (result.optJSONArray("volumes") == null) throw invalid();
        return result;
    }

    /**
     * Registers a local folder exactly as the XBVR 0.4.40 web UI does, then re-reads the list
     * to confirm the path is present. XBVR normalises the path with filepath.Abs, so callers must
     * pass an absolute path; a rejected folder (missing, not a directory, already registered)
     * answers 400 and is never retried. One POST is sent at most.
     *
     * <p>A 200 alone proves nothing: XBVR answers 200 after silently ignoring an unknown type, and
     * a path that was already registered (or normalised onto an existing one) leaves the list
     * unchanged. Success therefore requires a volume id that did not exist before the write.
     */
    public JSONObject addStorage(String path) throws IOException {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("目录路径不能为空");
        // Snapshot first: without the pre-write id set a 200 that changed nothing would be
        // indistinguishable from a real add.
        JSONObject before = storage();
        if (listsPath(before, path)) throw verification();
        Set<Integer> existing = ids(before);
        JSONObject body = new JSONObject();
        try {
            // "local" is the only path-based type; any other value is silently ignored by XBVR
            // while still answering 200, so the payload is never caller-controlled.
            body.put("type", "local");
            body.put("path", path);
        } catch (JSONException e) { throw invalid(); }
        send(url("api", "options", "storage"), "POST", body);
        // filepath.Abs may clean a trailing separator or "." away, so the read-back path is
        // compared exactly: an uncertain normalisation is a failed verification, never a success.
        JSONObject after = storage();
        Integer added = idForPath(after, path);
        if (added == null || existing.contains(added)) throw verification();
        return after;
    }

    /**
     * Removes a storage volume. XBVR deletes every file record of that volume and addresses it by
     * id alone, so a stale list could remove a different folder: the id and the expected path must
     * both match one volume of a freshly read snapshot before the single DELETE is sent. After the
     * write the id must be gone. Failed preconditions send nothing and report VERIFICATION.
     */
    public JSONObject removeStorage(int id, String expectedPath) throws IOException {
        if (id <= 0) throw new IllegalArgumentException("目录 ID 必须大于零");
        JSONObject before = storage();
        if (!matchesVolume(before, id, expectedPath)) throw verification();
        send(url("api", "options", "storage", Integer.toString(id)), "DELETE", null);
        JSONObject after = storage();
        if (listsId(after, id)) throw verification();
        return after;
    }

    /** Volumes are matched by exact path: a path that is only a prefix of another must not match. */
    private boolean listsPath(JSONObject snapshot, String path) throws IOException {
        JSONArray volumes = object(snapshot).optJSONArray("volumes");
        if (volumes == null) throw invalid();
        for (int i = 0; i < volumes.length(); i++) {
            JSONObject volume = volumes.optJSONObject(i);
            if (volume == null) throw invalid();
            Object candidate = volume.opt("path");
            if (candidate instanceof String && candidate.equals(path)) return true;
        }
        return false;
    }

    /** Every volume id in a snapshot. An unreadable id makes the snapshot unusable for verification. */
    private Set<Integer> ids(JSONObject snapshot) throws IOException {
        JSONArray volumes = object(snapshot).optJSONArray("volumes");
        if (volumes == null) throw invalid();
        Set<Integer> ids = new HashSet<>();
        for (int i = 0; i < volumes.length(); i++) {
            JSONObject volume = volumes.optJSONObject(i);
            if (volume == null) throw invalid();
            ids.add(volumeId(volume));
        }
        return ids;
    }

    /** Id of the volume whose path matches exactly, or null when no volume carries that path. */
    private Integer idForPath(JSONObject snapshot, String path) throws IOException {
        JSONArray volumes = object(snapshot).optJSONArray("volumes");
        if (volumes == null) throw invalid();
        for (int i = 0; i < volumes.length(); i++) {
            JSONObject volume = volumes.optJSONObject(i);
            if (volume == null) throw invalid();
            Object candidate = volume.opt("path");
            if (!(candidate instanceof String) || !candidate.equals(path)) continue;
            return volumeId(volume);
        }
        return null;
    }

    private int volumeId(JSONObject volume) throws IOException {
        Object candidate = volume.opt("id");
        if (!(candidate instanceof Number)) throw invalid();
        try {
            long id=Long.parseLong(candidate.toString());
            if(id<=0||id>Integer.MAX_VALUE)throw invalid();
            return (int)id;
        }
        catch (NumberFormatException e) { throw invalid(); }
    }

    private boolean listsId(JSONObject snapshot, int id) throws IOException {
        JSONArray volumes = object(snapshot).optJSONArray("volumes");
        if (volumes == null) throw invalid();
        for (int i = 0; i < volumes.length(); i++) {
            JSONObject volume = volumes.optJSONObject(i);
            if (volume == null) throw invalid();
            Object candidate = volume.opt("id");
            if (!(candidate instanceof Number)) throw invalid();
            try {
                if (Long.parseLong(candidate.toString()) == id) return true;
            } catch (NumberFormatException e) { throw invalid(); }
        }
        return false;
    }

    private boolean matchesVolume(JSONObject snapshot, int id, String expectedPath) throws IOException {
        JSONArray volumes = object(snapshot).optJSONArray("volumes");
        if (volumes == null) throw invalid();
        for (int i = 0; i < volumes.length(); i++) {
            JSONObject volume = volumes.optJSONObject(i);
            if (volume == null) throw invalid();
            Object candidateId = volume.opt("id");
            if (!(candidateId instanceof Number)) throw invalid();
            long value;
            try { value = Long.parseLong(candidateId.toString()); }
            catch (NumberFormatException e) { throw invalid(); }
            if (value != id) continue;
            Object candidatePath = volume.opt("path");
            if (!(candidatePath instanceof String)) throw invalid();
            // The id matched; only an exact path match authorises the destructive DELETE.
            return candidatePath.equals(expectedPath);
        }
        return false;
    }

    /** A successful response means submission only; XBVR does not report completion here. */
    public void rescan(Integer storageId) throws IOException {
        if (storageId != null && storageId <= 0) throw new IllegalArgumentException("目录 ID 无效");
        get(storageId == null ? url("api", "task", "rescan")
                : url("api", "task", "rescan", storageId.toString()));
    }

    public void refreshScenes() throws IOException { get(url("api", "task", "scene-refresh")); }

    public void scrape(String site) throws IOException {
        HttpUrl target = url("api", "task", "scrape");
        if (site != null) target = target.newBuilder().addQueryParameter("site", nonblank(site)).build();
        get(target);
    }

    public JSONArray files(boolean matched, String filename) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("state", matched ? "matched" : "unmatched");
            body.put("filename", filename == null ? "" : filename);
        } catch (JSONException e) { throw invalid(); }
        return array(send(url("api", "files", "list"), "POST", body));
    }

    public JSONObject searchScenes(String query) throws IOException {
        JSONObject result = object(get(url("api", "scene", "search").newBuilder()
                .addQueryParameter("q", query == null ? "" : query).build()));
        // The Go search handler serializes its untouched nil slice as null for zero results.
        if (result.opt("results") instanceof Number && result.optInt("results", -1) == 0
                && result.has("scenes") && result.isNull("scenes")) {
            try { result.put("scenes", new JSONArray()); } catch (JSONException e) { throw invalid(); }
        }
        if (!(result.opt("results") instanceof Number) || result.optJSONArray("scenes") == null) throw invalid();
        return result;
    }

    public JSONObject file(long id) throws IOException {
        positive(id);
        JSONObject result = object(get(url("api", "files", "file", Long.toString(id))));
        if (integer(result, "id") != id) throw verification();
        integer(result, "scene_id");
        return result;
    }

    /** Resolves the external scene key to its internal primary key before verifying the file link. */
    public void match(long fileId, String sceneId) throws IOException {
        positive(fileId);
        String supplied = nonblank(sceneId);
        JSONObject scene = object(get(url("api", "scene", supplied)));
        matchResolved(fileId, supplied, scene, 0);
    }

    /** Search results supply both keys, avoiding XBVR's ambiguous non-hyphenated route lookup. */
    public void match(long fileId, String sceneId, long internalId) throws IOException {
        positive(fileId);
        positive(internalId);
        String supplied = nonblank(sceneId);
        JSONObject scene = object(get(url("api", "scene", Long.toString(internalId))));
        matchResolved(fileId, supplied, scene, internalId);
    }

    private void matchResolved(long fileId, String supplied, JSONObject scene, long expectedInternalId) throws IOException {
        long internalId = integer(scene, "id");
        Object external = scene.opt("scene_id");
        if (internalId <= 0 || !(external instanceof String) || ((String) external).isBlank()) throw invalid();
        if (expectedInternalId != 0 && internalId != expectedInternalId) throw verification();
        if (!supplied.equals(external)) throw verification();
        JSONObject body = payload(fileId);
        try { body.put("scene_id", external); } catch (JSONException e) { throw invalid(); }
        send(url("api", "files", "match"), "POST", body);
        if (integer(file(fileId), "scene_id") != internalId) throw verification();
    }

    public void unmatch(long fileId) throws IOException {
        positive(fileId);
        send(url("api", "files", "unmatch"), "POST", payload(fileId));
        if (integer(file(fileId), "scene_id") != 0) throw verification();
    }

    /** XBVR offers a toggle, not a setter. Read fresh state and send at most one toggle. */
    public synchronized JSONArray toggleSite(String siteId, boolean desired) throws IOException {
        String id = nonblank(siteId);
        JSONArray current = sites();
        if (enabled(current, id) == desired) return current;
        array(send(url("api", "options", "sites", id), "PUT", new JSONObject()));
        JSONArray actual = sites();
        if (enabled(actual, id) != desired) throw verification();
        return actual;
    }

    private JSONObject payload(long fileId) throws IOException {
        try { return new JSONObject().put("file_id", fileId); }
        catch (JSONException e) { throw invalid(); }
    }

    private boolean enabled(JSONArray sites, String id) throws IOException {
        JSONObject found = null;
        for (int i = 0; i < sites.length(); i++) {
            JSONObject item = sites.optJSONObject(i);
            if (item == null || !(item.opt("id") instanceof String)) throw invalid();
            if (id.equals(item.opt("id"))) {
                if (found != null) throw invalid();
                found = item;
            }
        }
        if (found == null || !(found.opt("is_enabled") instanceof Boolean)) throw invalid();
        return (Boolean) found.opt("is_enabled");
    }

    private long integer(JSONObject object, String field) throws IOException {
        Object value = object.opt(field);
        if (!(value instanceof Number)) throw invalid();
        // IDs are unsigned integers; reject strings, fractions, overflow and missing values.
        try {
            long number = Long.parseLong(value.toString());
            if (number < 0) throw invalid();
            return number;
        } catch (NumberFormatException e) { throw invalid(); }
    }

    private HttpUrl url(String... segments) {
        HttpUrl.Builder builder = base.newBuilder();
        // Preserve a configured reverse-proxy prefix, removing only its final empty segment.
        if (base.encodedPath().endsWith("/")) builder.removePathSegment(base.pathSize() - 1);
        for (String segment : segments) builder.addPathSegment(segment);
        return builder.build();
    }

    private Object get(HttpUrl url) throws IOException { return send(url, "GET", null); }

    private Object send(HttpUrl target, String method, JSONObject body) throws IOException {
        Request.Builder request = new Request.Builder().url(target).header("Accept", "application/json");
        // A body-less call is a GET for every existing caller. XBVR's storage removal is a
        // body-less DELETE, so the method is honoured when it is not GET.
        if (body == null) {
            if ("DELETE".equals(method)) request.delete(); else request.get();
        } else request.method(method, RequestBody.create(body.toString(), JSON));
        boolean readonly=(method.equals("GET")&&!target.encodedPath().contains("/api/task/"))
            ||(method.equals("POST")&&target.encodedPath().endsWith("/api/files/list"));
        try (Response response = (readonly?readClient:client).newCall(request.build()).execute()) {
            int status = response.code();
            if (status == 401 || status == 403)
                throw new ManagementException(Kind.AUTH, "管理接口认证失败；请检查反向代理认证或服务端访问限制", status);
            if (response.isRedirect() || status == 404 || status == 405 || status == 501)
                throw new ManagementException(Kind.UNSUPPORTED, "管理接口不可用或发生重定向；请检查 XBVR 版本与最终管理地址", status);
            if (!response.isSuccessful())
                throw new ManagementException(Kind.SERVER, "管理接口返回 HTTP " + status, status);
            String text = response.body() == null ? "" : response.body().string().trim();
            if (text.isEmpty()) return JSONObject.NULL;
            Object result;
            try {
                // JSONTokener accepts trailing data by default; explicitly reject it.
                JSONTokener tokener = new JSONTokener(text);
                result = tokener.nextValue();
                if (tokener.nextClean() != 0 || !(result instanceof JSONObject || result instanceof JSONArray || result == JSONObject.NULL))
                    throw invalid();
            } catch (JSONException e) { throw invalid(); }
            if (result instanceof JSONObject) {
                Object authorized = ((JSONObject) result).opt("authorized");
                if (Boolean.FALSE.equals(authorized) || (authorized instanceof Number && ((Number) authorized).intValue() == 0))
                    throw new ManagementException(Kind.AUTH, "管理接口未授权；请检查服务端访问限制", status);
            }
            return result;
        } catch (ManagementException e) { throw e; }
        catch (IOException e) {
            ManagementException failure = new ManagementException(Kind.NETWORK, "管理请求超时或连接中断；操作结果可能未知，请先刷新确认，避免重复提交", 0);
            failure.initCause(e);
            throw failure;
        }
    }

    private JSONObject object(Object value) throws IOException {
        if (!(value instanceof JSONObject)) throw invalid();
        return (JSONObject) value;
    }
    private JSONArray array(Object value) throws IOException {
        if (!(value instanceof JSONArray)) throw invalid();
        return (JSONArray) value;
    }
    private static String nonblank(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("ID 不能为空");
        if (value.equals(".") || value.equals("..")) throw new IllegalArgumentException("ID 不能是路径跳转片段");
        return value;
    }
    private static void positive(long value) {
        if (value <= 0) throw new IllegalArgumentException("ID 必须大于零");
    }
    private static ManagementException invalid() {
        return new ManagementException(Kind.INVALID_RESPONSE, "管理接口未返回有效 JSON 或必要字段；请检查登录页、代理与服务端版本", 0);
    }
    private static ManagementException verification() {
        return new ManagementException(Kind.VERIFICATION, "操作后的服务器状态未通过核验；请刷新确认后再决定是否重试", 0);
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        HttpTransport.closeAsync(null, readClient, client);
    }
}
