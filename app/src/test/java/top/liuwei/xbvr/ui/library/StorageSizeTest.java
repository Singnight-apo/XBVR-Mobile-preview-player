package top.liuwei.xbvr.ui.library;

import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;

public class StorageSizeTest {
    @Test public void retainsWebCapacityPrecisionInSiUnits(){
        assertEquals("9.91 TB",StorageSize.format(9910378554319L,Locale.ENGLISH));
        assertEquals("968 GB",StorageSize.format(968217284470L,Locale.ENGLISH));
        assertEquals("14.7 TB",StorageSize.format(14679037831400L,Locale.ENGLISH));
        assertEquals("25.6 TB",StorageSize.format(25557633670189L,Locale.ENGLISH));
        assertEquals("0 B",StorageSize.format(0L,Locale.ENGLISH));
    }
    @Test public void localizesDecimalSeparatorAndKeepsUnknownUnknown(){
        assertEquals("9,91 TB",StorageSize.format(9910378554319L,Locale.GERMAN));
        assertNull(StorageSize.format(null,Locale.ENGLISH));
        assertNull(StorageSize.format(-1L,Locale.ENGLISH));
    }
}
