package top.liuwei.xbvr.ui.library;

/** Window-size policy shared by the native shell and its navigation. */
final class ManagementLayout {
    enum Mode { PHONE, TABS, SIDEBAR }
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
