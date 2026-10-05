package top.liuwei.xbvr.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * Builds the two HTTP clients used by the XBVR client: the authenticated client with the cookie jar
 * and the same-origin Basic interceptor, and the redirect-free client used for JSON calls so that
 * 307/308 keep their POST body without the app credentials ever crossing origins.
 */
public final class HttpTransport {
    private HttpTransport() {}

    public static OkHttpClient client(String base, String basicUser, String basicPassword) {
        Map<String, List<Cookie>> jar = new ConcurrentHashMap<>();
        return new OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .cookieJar(
                        new CookieJar() {
                            public void saveFromResponse(HttpUrl u, List<Cookie> cookies) {
                                jar.put(u.host(), cookies);
                            }

                            public List<Cookie> loadForRequest(HttpUrl u) {
                                List<Cookie> out = new ArrayList<>();
                                for (Cookie c : jar.getOrDefault(u.host(), List.of()))
                                    if (c.matches(u) && c.expiresAt() > System.currentTimeMillis())
                                        out.add(c);
                                return out;
                            }
                        })
                .addNetworkInterceptor(
                        chain -> {
                            Request r = chain.request();
                            Request.Builder b = r.newBuilder().removeHeader("Authorization");
                            if (!basicUser.isBlank()
                                    && XbvrProtocol.sameOrigin(base, r.url().toString()))
                                b.header("Authorization", Credentials.basic(basicUser, basicPassword));
                            return chain.proceed(b.build());
                        })
                .build();
    }

    public static OkHttpClient jsonClient(OkHttpClient client) {
        return client.newBuilder().followRedirects(false).followSslRedirects(false).build();
    }
}
