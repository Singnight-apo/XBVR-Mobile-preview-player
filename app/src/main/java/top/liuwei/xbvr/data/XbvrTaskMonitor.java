package top.liuwei.xbvr.data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.*;
import org.json.*;
import top.liuwei.xbvr.domain.ServerProfile;

/** Read-only WAMP subscription to the same task locks used by the XBVR web UI. */
public final class XbvrTaskMonitor {
    public interface Listener {
        void onTask(String task, boolean running, String message);
        void onConnection(boolean connected);
    }
    private final ServerProfile profile;
    private final Listener listener;
    private final OkHttpClient client;
    private final Map<Long,String> subscriptions = new ConcurrentHashMap<>();
    private volatile WebSocket socket;
    private volatile boolean closed;

    public XbvrTaskMonitor(ServerProfile profile, Listener listener) {
        this.profile = profile;
        this.listener = listener;
        client = HttpTransport.jsonClient(HttpTransport.client(profile.base, profile.basicUser, profile.basicPassword))
                .newBuilder().retryOnConnectionFailure(false).build();
    }
    public synchronized void open() {
        if (closed || socket != null) return;
        Request.Builder requestBuilder = new Request.Builder().url(profile.base + "/ws/")
                .header("Sec-WebSocket-Protocol", "wamp.2.json");
        // OkHttp skips network interceptors for WebSocket handshakes. Attach only same-origin
        // reverse-proxy credentials here; the player's login/password are never used.
        if (!profile.basicUser.isBlank())
            requestBuilder.header("Authorization", Credentials.basic(profile.basicUser, profile.basicPassword));
        Request request = requestBuilder.build();
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response response) {
                if (closed) { ws.cancel(); return; }
                ws.send("[1,\"default\",{\"roles\":{\"subscriber\":{}}}]");
            }
            @Override public void onMessage(WebSocket ws, String text) {
                if (closed) return;
                try {
                    JSONArray msg = new JSONArray(text);
                    switch (msg.optInt(0, -1)) {
                        case 2:
                            ws.send("[32,1,{},\"lock.change\"]");
                            break;
                        case 33:
                            if (msg.optLong(1) == 1 && msg.length() > 2) {
                                subscriptions.put(msg.getLong(2), "lock.change");
                                listener.onConnection(true);
                            }
                            break;
                        case 36: decode(msg, subscriptions, listener); break;
                        case 3: case 6: case 8:
                            listener.onConnection(false);
                            ws.close(1000, "Subscription unavailable");
                            break;
                        default: break;
                    }
                } catch (JSONException ignored) { /* An unrelated malformed event cannot finish a task. */ }
            }
            @Override public void onFailure(WebSocket ws, Throwable error, Response response) {
                if (!closed) listener.onConnection(false);
            }
            @Override public void onClosed(WebSocket ws, int code, String reason) {
                if (!closed) listener.onConnection(false);
            }
            @Override public void onClosing(WebSocket ws, int code, String reason) {
                if (!closed) listener.onConnection(false);
                ws.close(code, reason);
            }
        });
    }
    static void decode(JSONArray msg, Map<Long,String> subscriptions, Listener listener) {
        if (msg.optInt(0, -1) != 36 || !"lock.change".equals(subscriptions.get(msg.optLong(1, -1)))) return;
        JSONObject data = msg.optJSONObject(5);
        if (data == null || !(data.opt("locked") instanceof Boolean)) return;
        String task = data.optString("name");
        if (!task.equals("rescan") && !task.equals("scrape")) return;
        listener.onTask(task, data.optBoolean("locked"), "");
    }
    public synchronized void close() {
        if (closed) return;
        closed = true;
        WebSocket ws = socket;
        socket = null;
        subscriptions.clear();
        if (ws != null) ws.cancel();
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
