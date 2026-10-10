package top.liuwei.xbvr.data;

import java.io.IOException;
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
    }

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
        if (body == null) request.get();
        else request.method(method, RequestBody.create(body.toString(), JSON));
        try (Response response = client.newCall(request.build()).execute()) {
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

    @Override public void close() {
        client.dispatcher().cancelAll();
        client.dispatcher().executorService().shutdownNow();
        client.connectionPool().evictAll();
    }
}
