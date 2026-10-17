package top.liuwei.xbvr.ui.library;

/** Window-size policy shared by the native shell and its navigation. */
final class ManagementLayout {
    enum Mode { PHONE, TABS, SIDEBAR }
    enum StorageMode { CARDS, COMPACT, TABLE }
    static StorageMode storageMode(int smallestWidthDp,int widthDp,int heightDp,int availableWidthDp,float fontScale) {
        float scale=Math.max(1f,fontScale);
        boolean tablet=mode(smallestWidthDp,widthDp,heightDp)!=Mode.PHONE;
        if(tablet) {
            return widthDp>heightDp&&availableWidthDp>=820*scale?StorageMode.TABLE:StorageMode.CARDS;
        }
        return widthDp>heightDp&&availableWidthDp>=620*scale?StorageMode.COMPACT:StorageMode.CARDS;
    }
    static StorageMode storageMode(int availableWidthDp,float fontScale) {
        float scale=Math.max(1f,fontScale);
        if(availableWidthDp>=820*scale)return StorageMode.TABLE;
        return availableWidthDp>=560*scale?StorageMode.COMPACT:StorageMode.CARDS;
    }
    static Mode mode(int smallestWidthDp,int widthDp,int heightDp) {
        if(smallestWidthDp<600||widthDp<600)return Mode.PHONE;
        return widthDp>=960&&widthDp>heightDp?Mode.SIDEBAR:Mode.TABS;
    }
    static String section(String route) {
        if("file".equals(route)||"search".equals(route)||"compare".equals(route))return "matching";
        return "home".equals(route)?"maintenance":route;
    }
    static String backTarget(String route,boolean tablet) {
        if("compare".equals(route))return "search";
        if("search".equals(route)||"file".equals(route))return "matching";
        return tablet||"home".equals(route)?null:"home";
    }
}
