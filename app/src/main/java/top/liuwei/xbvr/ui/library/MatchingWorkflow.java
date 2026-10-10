package top.liuwei.xbvr.ui.library;

import java.text.Normalizer;
import org.json.JSONArray;
import org.json.JSONObject;

/** Filename search and queue order, independent of Android views and server writes. */
final class MatchingWorkflow {
    static String query(String filename) {
        if(filename==null)return "";
        String name=filename.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1);
        name=name.replaceFirst("(?i)\\.(mp4|mkv|webm|avi|mov|m4v|wmv|ts|m2ts|mpg|mpeg|vob|flv)$", "");
        return Normalizer.normalize(name,Normalizer.Form.NFKC)
            .replaceAll("[^\\p{L}\\p{N}\\p{M}]+"," ").trim().replaceAll(" +"," ");
    }
    static int nextIndex(JSONArray files,long selected) {
        boolean found=false;
        for(int i=0;i<files.length();i++) {
            JSONObject file=files.optJSONObject(i);if(file==null||file.optLong("id",-1)<=0)continue;
            if(found)return i;
            if(file.optLong("id",-1)==selected)found=true;
        }
        return -1;
    }
}
