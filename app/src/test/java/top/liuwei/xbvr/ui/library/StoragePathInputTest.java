package top.liuwei.xbvr.ui.library;

import org.junit.Test;
import static org.junit.Assert.*;

public class StoragePathInputTest {
    @Test public void acceptsServerPathsFromLinuxWindowsAndNetworkShares(){
        assertTrue(StoragePathInput.absolute("/media/VR"));
        assertTrue(StoragePathInput.absolute("D:\\Media\\VR"));
        assertTrue(StoragePathInput.absolute("D:/Media/VR"));
        assertTrue(StoragePathInput.absolute("\\\\NAS\\Media\\VR"));
    }
    @Test public void rejectsRelativeAndMultilineInput(){
        for(String path:new String[]{"", "  ", "Media/VR", "D:Media", "\\VR", "/media\nVR", "/media\u0000VR"})
            assertFalse(path,StoragePathInput.absolute(path));
        assertFalse(StoragePathInput.absolute(null));
    }
}
