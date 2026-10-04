package top.liuwei.xbvr;
import okhttp3.*;
import okhttp3.Cookie;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.*;
import static top.liuwei.xbvr.Models.*;

public final class Api {
    public final String base,id;private final String user,password;
    public final OkHttpClient client;
    private final OkHttpClient jsonClient;
    public Api(JSONObject profile){base=profile.optString("base");id=profile.optString("id");user=profile.optString("user");password=profile.optString("password");
        String basicUser=profile.optString("basicUser"),basicPass=profile.optString("basicPassword");
        Map<String,List<Cookie>> jar=new java.util.concurrent.ConcurrentHashMap<>();
        client=new OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS)
        .cookieJar(new CookieJar(){public void saveFromResponse(HttpUrl u,List<Cookie> cookies){jar.put(u.host(),cookies);}public List<Cookie> loadForRequest(HttpUrl u){List<Cookie> out=new ArrayList<>();for(Cookie c:jar.getOrDefault(u.host(),List.of()))if(c.matches(u)&&c.expiresAt()>System.currentTimeMillis())out.add(c);return out;}})
        .addNetworkInterceptor(chain->{Request r=chain.request();Request.Builder b=r.newBuilder().removeHeader("Authorization");if(!basicUser.isBlank()&&Protocol.sameOrigin(base,r.url().toString()))b.header("Authorization",Credentials.basic(basicUser,basicPass));return chain.proceed(b.build());})
        .build();
        // 307/308 preserve POST bodies. Follow JSON redirects ourselves so app credentials never cross origins.
        jsonClient=client.newBuilder().followRedirects(false).followSslRedirects(false).build();
    }
    public JSONObject json(String url,boolean hs,JSONObject extra)throws Exception {
        if(!Protocol.sameOrigin(base,url))throw new IOException("详情地址跨服务器，未转发账号，请检查 XBVR 返回的地址");
        JSONObject body=extra==null?new JSONObject():new JSONObject(extra.toString());
        if(!user.isBlank()){body.put(hs?"username":"login",user);body.put("password",password);}
        String target=url;
        for(int hop=0;hop<6;hop++) {
        Request r=new Request.Builder().url(target).post(RequestBody.create(body.toString(),MediaType.get("application/json; charset=utf-8"))).build();
        try(Response response=jsonClient.newCall(r).execute()) {
            if(response.isRedirect()) {
                String location=response.header("Location");HttpUrl next=location==null?null:r.url().resolve(location);
                if(next==null||!Protocol.sameOrigin(base,next.toString()))throw new IOException("接口重定向到另一服务器，已停止转发账号；请填写最终 XBVR 地址");
                target=next.toString();continue;
            }
            if(!response.isSuccessful())throw new IOException("接口 HTTP "+response.code()+"；检查地址、认证与接口设置");
            String text=response.body().string();if(text.isBlank())throw new IOException("接口返回为空，请在 XBVR 开启播放器接口");
            JSONObject j;try{j=new JSONObject(text);}catch(JSONException e){throw new IOException("接口未返回 JSON；检查反向代理地址与登录页");}Protocol.authorized(j);return j;
        }
        }
        throw new IOException("接口重定向次数过多，请检查代理配置");
    }
    public JSONObject library()throws Exception{return json(base+"/deovr",false,null);}
    /** Call on the library IO executor. Return snapshots; never modify live UI entries. */
    public JSONObject libraryMetadata(List<Entry> entries,JSONObject cached,boolean force)throws Exception {
        JSONObject cache=cached==null?new JSONObject():cached.optJSONObject("_metadata");if(cache==null)cache=cached;
        JSONObject result=new JSONObject();LinkedHashMap<String,Entry> pending=new LinkedHashMap<>();
        Map<String,String> sceneKeys=new HashMap<>();long now=System.currentTimeMillis();
        for(Entry original:entries){
            String key=Protocol.identity(original.url);Entry copy=new Entry();copy.url=original.url;copy.poster=original.poster;
            Protocol.metadata(copy,Protocol.normalized(original),base);
            JSONObject saved=cache.optJSONObject(key);if(saved!=null)Protocol.metadata(copy,saved,base);
            JSONObject snapshot=Protocol.normalized(copy);result.put(key,snapshot);
            if(!force&&(copy.metadataLoaded||(saved!=null&&saved.optLong("_retryAfter",0)>now))){if(saved!=null&&saved.has("_retryAfter"))snapshot.put("_retryAfter",saved.optLong("_retryAfter"));continue;}
            pending.put(key,copy);
            int marker=key.lastIndexOf("/scene:");if(marker>=0&&Protocol.sameOrigin(base,original.url))sceneKeys.put(key.substring(marker+7),key);
        }
        if(pending.isEmpty())return result;
        // REST list is a read-only query. Do not send the DeoVR player's login/password to backend routes.
        if(!sceneKeys.isEmpty())try {
            final int limit=200;
            for(int page=0;page<100&&!sceneKeys.isEmpty();page++){
                JSONObject query=new JSONObject().put("isAvailable",true).put("isAccessible",true).put("dlState","available").put("limit",limit).put("offset",page*limit).put("sort","added_desc");
                Request request=new Request.Builder().url(base+"/api/scene/list").post(RequestBody.create(query.toString(),MediaType.get("application/json; charset=utf-8"))).build();
                JSONObject response;
                try(Response r=jsonClient.newCall(request).execute()){if(!r.isSuccessful()||r.body()==null)throw new IOException("Metadata list unavailable");response=new JSONObject(r.body().string());Protocol.authorized(response);}
                JSONArray scenes=response.optJSONArray("scenes");if(scenes==null)throw new IOException("Metadata list unsupported");
                for(int i=0;i<scenes.length();i++){JSONObject scene=scenes.optJSONObject(i);if(scene==null)continue;String key=sceneKeys.remove(String.valueOf(scene.opt("id")));if(key==null)continue;Entry copy=pending.remove(key);if(copy==null)continue;Protocol.metadata(copy,scene,base);copy.metadataLoaded=true;result.put(key,Protocol.normalized(copy));}
                if(scenes.length()<limit)break;
            }
        }catch(Exception ignored){/* Player-only servers retain the authorized DeoVR fallback below. */}
        List<Map.Entry<String,Entry>> missing=new ArrayList<>(pending.entrySet());
        ExecutorService workers=Executors.newFixedThreadPool(3);int failures=0;
        try {
            // Three bounded requests at a time. A blocked player API stops after one failed wave.
            for(int start=0;start<missing.size()&&failures<3;start+=3){
                List<Future<JSONObject>> jobs=new ArrayList<>();int end=Math.min(start+3,missing.size());
                for(int i=start;i<end;i++){Entry copy=missing.get(i).getValue();jobs.add(workers.submit(()->{Protocol.metadata(copy,json(copy.url,false,null),base);copy.metadataLoaded=true;return Protocol.normalized(copy);}));}
                for(int i=start;i<end;i++)try {result.put(missing.get(i).getKey(),jobs.get(i-start).get());}catch(ExecutionException failed){failures++;result.getJSONObject(missing.get(i).getKey()).put("_retryAfter",now+TimeUnit.MINUTES.toMillis(10));}
            }
            if(failures>=3)for(Map.Entry<String,Entry> item:missing)if(!result.getJSONObject(item.getKey()).optBoolean("metadataLoaded"))result.getJSONObject(item.getKey()).put("_retryAfter",now+TimeUnit.MINUTES.toMillis(10));
        }finally{workers.shutdownNow();}
        return result;
    }
    public Detail detail(String url)throws Exception {
        Detail d=Protocol.detail(json(url,false,null),base,false);String hs=Protocol.hereUrl(url);
        if(!hs.isBlank())try {Detail h=Protocol.detail(json(hs,true,null),base,true);h.hsUrl=hs;if(!h.sources.isEmpty())d=h;}catch(Exception ignored){}
        for(Source s:d.sources){String file=Protocol.fileId(s.url);if(!file.isBlank())try{
            Request request=new Request.Builder().url(base+"/api/files/file/"+file).get().build();
            try(Response r=client.newCall(request).execute()){if(r.isSuccessful()){JSONObject j=new JSONObject(r.body().string());Protocol.authorized(j);Protocol.fileMetadata(s,j);}}
        }catch(Exception ignored){} }
        if(d.sources.isEmpty())throw new IOException("场景没有可用媒体文件，请检查 XBVR 文件可用状态");return d;
    }
    public void favorite(Detail d,boolean value)throws Exception {
        if(!d.writeFavorite||d.hsUrl.isBlank())throw new IOException("服务器未开放收藏写入");
        JSONObject result=json(d.hsUrl,true,new JSONObject().put("isFavorite",value));
        if(result.optBoolean("isFavorite")!=value)throw new IOException("服务器未确认收藏结果");
        JSONObject verify=json(d.hsUrl,true,null);if(verify.optBoolean("isFavorite")!=value)throw new IOException("重新读取后收藏状态不一致");d.favorite=value;
    }
}
