package top.liuwei.xbvr.data;

import org.json.*;
import java.net.URI;
import java.util.*;
import top.liuwei.xbvr.domain.ResourceIdentity;
import static top.liuwei.xbvr.domain.Models.*;

/** Protocol-only functions; no network, UI or Android dependencies. */
public final class XbvrProtocol {
    public static String base(String input) {
        URI u=URI.create(input.trim());
        if(u.getScheme()==null||!Set.of("http","https").contains(u.getScheme().toLowerCase(Locale.ROOT))||u.getHost()==null||u.getUserInfo()!=null)throw new IllegalArgumentException("请输入 HTTP/HTTPS 地址；账号请填在独立字段");
        String p=u.getRawPath()==null?"":u.getRawPath();
        p=p.replaceAll("/+$","").replaceAll("/(?:deovr|heresphere)(?:/.*)?$","").replaceAll("/ui$","");
        return u.getScheme().toLowerCase(Locale.ROOT)+"://"+u.getRawAuthority()+p;
    }
    public static String resolve(String base,String value) {
        if(value==null||value.isBlank())return "";
        return URI.create(base+"/").resolve(value.replace(" ","%20")).toString();
    }
    public static boolean sameOrigin(String a,String b) {
        try {URI x=URI.create(a),y=URI.create(b); return x.getHost()!=null&&y.getHost()!=null&&x.getScheme()!=null&&y.getScheme()!=null&&y.getUserInfo()==null&&x.getScheme().equalsIgnoreCase(y.getScheme())&&x.getHost().equalsIgnoreCase(y.getHost())&&port(x)==port(y);} catch(Exception e){return false;}
    }
    private static int port(URI u) {return u.getPort()!=-1?u.getPort():"https".equalsIgnoreCase(u.getScheme())?443:80;}
    public static void authorized(JSONObject j) {
        for(String key:List.of("authorized","access"))if(j.has(key)) {
            String status=String.valueOf(j.opt(key));
            if(!"1".equals(status)&&!"true".equals(status))throw new IllegalStateException("-1".equals(status)?"播放器账号或密码错误":"需要播放器账号，请在服务器设置中登录");
        }
    }
    public static List<Entry> library(JSONObject j,String base) {
        authorized(j); LinkedHashMap<String,Entry> entries=new LinkedHashMap<>();
        JSONArray groups=j.optJSONArray("scenes");
        if(groups==null)throw new IllegalArgumentException("目录接口没有 scenes；请确认 XBVR 已启用 DeoVR");
        for(int i=0;i<groups.length();i++){
            JSONObject g=groups.optJSONObject(i);if(g==null)continue;
            JSONArray list=g.optJSONArray("list"); if(list==null)continue;
            for(int k=0;k<list.length();k++) {
                JSONObject o=list.optJSONObject(k);if(o==null)continue;
                String url=resolve(base,o.optString("video_url")); if(url.isBlank())continue;
                String key=identity(url);Entry e=entries.get(key);
                if(e==null){e=new Entry();e.url=url;e.title=o.optString("title","未命名视频");e.poster=resolve(base,o.optString("thumbnailUrl"));e.duration=(long)(o.optDouble("videoLength",0)*1000);entries.put(key,e);}
                String group=g.optString("name","未分类");e.groups.add(group);e.groupOrder.putIfAbsent(group,k);
                metadata(e,o,base);
            }
        }
        List<Entry> result=new ArrayList<>(entries.values());merge(result,j.optJSONObject("_metadata"));return result;
    }
    /** Accept DeoVR details, REST summaries, or our minimal cached dictionary. */
    public static void metadata(Entry e,JSONObject j,String base) {
        if(j==null)return;
        JSONObject paysite=j.optJSONObject("paysite");
        String studio=paysite==null?name(j.opt("site")):paysite.optString("name");
        if(studio.isBlank())studio=name(j.opt("studio"));if(!studio.isBlank()||j.has("studio"))e.studio=studio;
        if(j.has("actors")||j.has("cast")){e.actors.clear();names(j.optJSONArray(j.has("actors")?"actors":"cast"),e.actors,false);}
        if(j.has("categories")||j.has("tags")){e.tags.clear();names(j.optJSONArray(j.has("categories")?"categories":"tags"),e.tags,true);}
        LinkedHashSet<String> posters=new LinkedHashSet<>();
        JSONArray candidates=j.optJSONArray("posterCandidates");if(candidates!=null)for(int i=0;i<candidates.length();i++)addPoster(posters,"",candidates.optString(i));
        for(String field:List.of("cover_url","thumbnailUrl","thumbnailImage"))addPoster(posters,base,j.optString(field));
        JSONArray images=j.optJSONArray("images");if(images==null)try{images=new JSONArray(j.optString("images"));}catch(JSONException ignored){}
        if(images!=null)for(int i=0;i<images.length();i++){JSONObject image=images.optJSONObject(i);if(image!=null&&"cover".equalsIgnoreCase(image.optString("type")))addPoster(posters,base,image.optString("url"));}
        addPoster(posters,base,e.poster);
        posters.addAll(e.posterCandidates);e.posterCandidates.clear();e.posterCandidates.addAll(posters);
        if(j.has("metadataLoaded"))e.metadataLoaded=j.optBoolean("metadataLoaded");
        else if((j.has("cast")||j.has("actors"))&&(j.has("categories")||j.has("tags")))e.metadataLoaded=true;
    }
    private static String name(Object value){if(value instanceof JSONObject)return ((JSONObject)value).optString("name");return value instanceof String?((String)value).trim():"";}
    private static void names(JSONArray values,Set<String> out,boolean category){if(values==null)return;for(int i=0;i<values.length();i++){Object value=values.opt(i);if(category&&value instanceof JSONObject&&((JSONObject)value).has("tag"))value=((JSONObject)value).opt("tag");String n=name(value);if(!n.isBlank())out.add(n);}}
    private static void addPoster(Set<String> out,String base,String value){
        try{String url=resolve(base,value);URI u=URI.create(url);if(!Set.of("http","https").contains(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null)return;
            // Web SceneCard uses this same XBVR proxy route; retain the origin URL as fallback.
            if(out.contains(url))return;
            String path=u.getRawPath(),prefix=base.isBlank()?"":URI.create(base).getRawPath();
            boolean alreadyProxy=!base.isBlank()&&sameOrigin(base,url)&&path!=null&&(path.startsWith(prefix+"/img/")||path.startsWith("/img/"));
            boolean absolute=value.regionMatches(true,0,"http://",0,7)||value.regionMatches(true,0,"https://",0,8);
            if(!base.isBlank()&&absolute&&!alreadyProxy)out.add(base+"/img/700x/"+url);out.add(url);
        }catch(Exception ignored){}
    }
    public static void merge(List<Entry> entries,JSONObject normalized){if(normalized==null)return;for(Entry e:entries)metadata(e,normalized.optJSONObject(identity(e.url)),"");}
    public static JSONObject normalized(Entry e)throws JSONException{return new JSONObject().put("studio",e.studio).put("actors",new JSONArray(e.actors)).put("tags",new JSONArray(e.tags)).put("posterCandidates",new JSONArray(e.posterCandidates)).put("metadataLoaded",e.metadataLoaded);}
    public static Detail detail(JSONObject j,String base,boolean hs) {
        authorized(j);Detail d=new Detail();d.title=j.optString("title");d.poster=resolve(base,j.optString(hs?"thumbnailImage":"thumbnailUrl"));
        d.duration=(long)(j.optDouble(hs?"duration":"videoLength",0)*(hs?1:1000));
        d.metadata=j.optString(hs?"projection":"screenType");d.stereo=j.optString(hs?"stereo":"stereoMode");d.fov=j.optDouble("fov",0);
        d.favorite=j.optBoolean("isFavorite");d.writeFavorite=hs&&j.optBoolean("writeFavorite");
        JSONArray media=j.optJSONArray(hs?"media":"encodings");
        if(media!=null)for(int i=0;i<media.length();i++) {
            JSONObject group=media.optJSONObject(i);if(group==null)continue;JSONArray sources=group.optJSONArray(hs?"sources":"videoSources");if(sources==null)continue;
            for(int k=0;k<sources.length();k++){
                JSONObject o=sources.optJSONObject(k);if(o==null)continue;Source s=new Source();
                s.url=resolve(base,o.optString("url"));if(s.url.isBlank())continue;
                s.name=group.optString("name","文件 "+(i+1))+" · "+o.optString("resolution","")+"p";
                s.filename=o.optString("filename");s.projection=o.optString("projection");s.stereo=o.optString("stereo",o.optString("stereoMode"));s.fov=o.optDouble("fov",0);s.width=o.optInt("width");s.height=o.optInt("height");d.sources.add(s);
            }
        }
        JSONArray tags=j.optJSONArray(hs?"tags":"timeStamps");
        if(tags!=null)for(int i=0;i<tags.length();i++) {JSONObject t=tags.optJSONObject(i);if(t!=null&&t.has(hs?"start":"ts"))d.tags.add(new Tag(t.optString("name"),(long)(t.optDouble(hs?"start":"ts",0)*(hs?1:1000))));}
        d.tags.sort(Comparator.comparingLong(t->t.time));
        JSONArray subs=j.optJSONArray("subtitles");if(subs!=null)for(int i=0;i<subs.length();i++){JSONObject o=subs.optJSONObject(i);if(o!=null){Subtitle s=new Subtitle();s.name=o.optString("name");s.url=resolve(base,o.optString("url"));s.language=o.optString("language","und");d.subtitles.add(s);}}
        return d;
    }
    public static String hereUrl(String url) {
        URI u=URI.create(url);String p=u.getRawPath();
        if(p!=null&&p.matches(".*/deovr/(?:file/)?[0-9]+"))return url.replaceFirst("/deovr/","/heresphere/");
        return "";
    }
    public static String fileId(String url){try{var m=java.util.regex.Pattern.compile("/api/dms/file/([0-9]+)(?:/|$)").matcher(URI.create(url).getRawPath());return m.find()?m.group(1):"";}catch(Exception e){return "";}}
    /** Stable XBVR object key; the rule lives in the domain so identity cannot drift per caller. */
    public static String identity(String url) {
        return ResourceIdentity.of(url);
    }
    /** Only the selected file's fields belong here; scene-level metadata may describe its first file. */
    public static void fileMetadata(Source source,JSONObject file) {
        if(file.has("filename"))source.filename=file.optString("filename");
        if(file.has("projection")||file.has("video_projection"))source.projection=file.optString("projection",file.optString("video_projection"));
        if(file.has("stereo")||file.has("stereoMode"))source.stereo=file.optString("stereo",file.optString("stereoMode"));
        if(file.has("fov"))source.fov=file.optDouble("fov",0);
        source.width=file.optInt("video_width",file.optInt("width",source.width));
        source.height=file.optInt("video_height",file.optInt("height",source.height));
    }
}
