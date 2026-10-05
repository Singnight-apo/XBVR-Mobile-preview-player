package top.liuwei.xbvr;
import top.liuwei.xbvr.domain.Projection;
import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import org.json.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import top.liuwei.xbvr.domain.CoverSettings;
import top.liuwei.xbvr.domain.FavoriteRepository;
import top.liuwei.xbvr.domain.PlaybackRepository;
import top.liuwei.xbvr.domain.ResourceIdentity;
import top.liuwei.xbvr.data.LocalSettings;
import top.liuwei.xbvr.data.ProfileJsonMapper;
import top.liuwei.xbvr.domain.ServerProfile;

public final class Store implements CoverSettings, PlaybackRepository, FavoriteRepository {
    private final Context ctx;public final SharedPreferences prefs;
    private final LocalSettings settings;
    public Store(Context c){ctx=c.getApplicationContext();prefs=ctx.getSharedPreferences("local",Context.MODE_PRIVATE);settings=new LocalSettings(c);}
    private SecretKey key()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias("xbvr-profiles"))return (SecretKey)ks.getKey("xbvr-profiles",null);
        KeyGenerator gen=KeyGenerator.getInstance("AES","AndroidKeyStore");gen.init(new KeyGenParameterSpec.Builder("xbvr-profiles",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return gen.generateKey();
    }
    public void saveProfiles(JSONArray profiles)throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
        prefs.edit().putString("profiles",Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(profiles.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP)).apply();
    }
    public JSONArray profiles()throws Exception {
        String s=prefs.getString("profiles","");if(s.isBlank())return new JSONArray();
        String[] a=s.split(":");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(a[0],0)));
        return new JSONArray(new String(c.doFinal(Base64.decode(a[1],0)),StandardCharsets.UTF_8));
    }
    public String current(){return prefs.getString("active","");}
    public void current(String id){prefs.edit().putString("active",id).apply();}
    public JSONObject profile()throws Exception {JSONArray a=profiles();for(int i=0;i<a.length();i++)if(a.getJSONObject(i).optString("id").equals(current()))return a.getJSONObject(i);return a.length()>0?a.getJSONObject(0):null;}
    public List<ServerProfile> serverProfiles()throws Exception {return ProfileJsonMapper.listFrom(profiles());}
    public ServerProfile serverProfile(String id)throws Exception {return ProfileJsonMapper.find(profiles(),id);}
    public ServerProfile currentProfile()throws Exception {JSONObject p=profile();return p==null?null:ProfileJsonMapper.from(p);}
    public void saveProfile(ServerProfile value)throws Exception {JSONArray all=profiles();ProfileJsonMapper.put(all,value);saveProfiles(all);}
    public void cache(String id,String json)throws Exception {Path p=ctx.getFilesDir().toPath().resolve("library-"+id+".json"),temp=p.resolveSibling(p.getFileName()+".tmp");Files.write(temp,json.getBytes(StandardCharsets.UTF_8));Files.move(temp,p,StandardCopyOption.REPLACE_EXISTING);}
    public String cache(String id){try{return new String(Files.readAllBytes(ctx.getFilesDir().toPath().resolve("library-"+id+".json")),StandardCharsets.UTF_8);}catch(Exception e){return "";}}
    public String playbackKey(String id,String url){return ResourceIdentity.playbackKey(id,url);}
    // Local preference access is owned by LocalSettings; Store only forwards for now.
    public int mode(String profileId){return settings.mode(profileId);}
    public void mode(String profileId,int value){settings.mode(profileId,value);}
    public float inferredRatio(String profileId){return settings.inferredRatio(profileId);}
    public void inferredRatio(String profileId,float ratio){settings.inferredRatio(profileId,ratio);}
    public void clearInferredRatio(String profileId){settings.clearInferredRatio(profileId);}
    public long position(String key){return settings.position(key);}
    public void save(String key,long pos,Projection p,boolean override){settings.save(key,pos,p,override);}
    public boolean restore(String key,Projection p){return settings.restore(key,p);}
    public String selectedSource(String entryKey){return settings.selectedSource(entryKey);}
    public void selectedSource(String entryKey,String url){settings.selectedSource(entryKey,url);}
    public void entryPosition(String entryKey,long position){settings.entryPosition(entryKey,position);}
    public boolean favorite(String key){return settings.favorite(key);}
    public void favorite(String key,boolean b){settings.favorite(key,b);}
}
