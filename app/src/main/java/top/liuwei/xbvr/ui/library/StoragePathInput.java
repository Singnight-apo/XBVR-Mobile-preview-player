package top.liuwei.xbvr.ui.library;

/** Input belongs to a remote OS; never resolve it against Android's filesystem. */
final class StoragePathInput {
    static boolean absolute(String value){
        if(value==null||value.isBlank())return false;
        for(int i=0;i<value.length();i++)if(Character.isISOControl(value.charAt(i)))return false;
        return value.startsWith("/")||value.startsWith("\\\\")
            ||value.matches("^[A-Za-z]:[\\\\/].*");
    }
}
