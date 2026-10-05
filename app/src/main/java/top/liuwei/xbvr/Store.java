package top.liuwei.xbvr;
import android.content.*;
import org.json.*;
import top.liuwei.xbvr.data.FavoriteStore;
import top.liuwei.xbvr.data.LibraryCache;
import top.liuwei.xbvr.data.LibraryCacheStore;
import top.liuwei.xbvr.data.LocalSettings;
import top.liuwei.xbvr.data.PlaybackStore;
import top.liuwei.xbvr.data.ProfileStore;
import top.liuwei.xbvr.domain.CoverSettings;
import top.liuwei.xbvr.domain.FavoriteRepository;
import top.liuwei.xbvr.domain.PlaybackRepository;
import top.liuwei.xbvr.domain.Projection;
import top.liuwei.xbvr.domain.ResourceIdentity;
import top.liuwei.xbvr.domain.ServerProfile;

/**
 * Temporary root facade. Profile, playback, favourite and cache storage now live in Data; this class
 * only forwards so the Activities can keep their current call sites until T22 removes the facade.
 */
public final class Store implements CoverSettings, PlaybackRepository, FavoriteRepository, LibraryCacheStore {
    public final SharedPreferences prefs;
    private final ProfileStore profiles;
    private final LocalSettings settings;
    private final PlaybackStore playback;
    private final FavoriteStore favorites;
    private final LibraryCache cache;
    public Store(Context c){
        c=c.getApplicationContext();
        prefs=c.getSharedPreferences("local",Context.MODE_PRIVATE);
        profiles=new ProfileStore(c);
        settings=new LocalSettings(c);
        playback=new PlaybackStore(settings);
        favorites=new FavoriteStore(settings);
        cache=new LibraryCache(c);
    }
    public void saveProfiles(JSONArray value)throws Exception {profiles.saveProfiles(value);}
    public JSONArray profiles()throws Exception {return profiles.profiles();}
    public String current(){return profiles.activeId();}
    public void current(String id){profiles.activeId(id);}
    public JSONObject profile()throws Exception {return profiles.profile();}
    public java.util.List<ServerProfile> serverProfiles()throws Exception {return profiles.all();}
    public ServerProfile serverProfile(String id)throws Exception {return profiles.find(id);}
    public ServerProfile currentProfile()throws Exception {return profiles.currentProfile();}
    public void saveProfile(ServerProfile value)throws Exception {profiles.saveProfile(value);}
    public void removeProfile(String id)throws Exception {profiles.remove(id);}
    @Override public void write(String id,String json)throws Exception {cache.write(id,json);}
    @Override public String read(String id){return cache.read(id);}
    public void cache(String id,String json)throws Exception {write(id,json);}
    public String cache(String id){return read(id);}
    public String playbackKey(String id,String url){return ResourceIdentity.playbackKey(id,url);}
    public int mode(String profileId){return settings.mode(profileId);}
    public void mode(String profileId,int value){settings.mode(profileId,value);}
    public float inferredRatio(String profileId){return settings.inferredRatio(profileId);}
    public void inferredRatio(String profileId,float ratio){settings.inferredRatio(profileId,ratio);}
    public void clearInferredRatio(String profileId){settings.clearInferredRatio(profileId);}
    public long position(String key){return playback.position(key);}
    public void save(String key,long pos,Projection p,boolean override){playback.save(key,pos,p,override);}
    public boolean restore(String key,Projection p){return playback.restore(key,p);}
    public String selectedSource(String entryKey){return playback.selectedSource(entryKey);}
    public void selectedSource(String entryKey,String url){playback.selectedSource(entryKey,url);}
    public void entryPosition(String entryKey,long position){playback.entryPosition(entryKey,position);}
    public boolean favorite(String key){return favorites.favorite(key);}
    public void favorite(String key,boolean b){favorites.favorite(key,b);}
}
