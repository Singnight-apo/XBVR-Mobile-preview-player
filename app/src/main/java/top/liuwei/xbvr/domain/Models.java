package top.liuwei.xbvr.domain;
import java.util.*;
public final class Models {
    public static class Entry {
        public String title="",poster="",url="";
        public long duration;
        public final Set<String> groups=new LinkedHashSet<>();
        public String studio="";
        public final LinkedHashSet<String> actors=new LinkedHashSet<>(),tags=new LinkedHashSet<>();
        public final Map<String,Integer> groupOrder=new LinkedHashMap<>();
        public final List<String> posterCandidates=new ArrayList<>();
        public boolean metadataLoaded;
    }
    public static class Source {
        public String name="",url="",filename="",projection="",stereo="";
        public int width,height;
        public double fov;
    }
    public static class Tag { public String name;public long time; public Tag(String n,long t){name=n;time=t;} }
    public static class Subtitle { public String name,url,language; }
    public static class Detail {
        public String title="",poster="",metadata="",stereo="",hsUrl="";
        public double fov;
        public long duration;
        public boolean favorite,writeFavorite;
        public final List<Source> sources=new ArrayList<>();
        public final List<Tag> tags=new ArrayList<>();
        public final List<Subtitle> subtitles=new ArrayList<>();
    }
}
