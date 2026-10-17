package top.liuwei.xbvr.ui.library;

import org.junit.Test;
import static org.junit.Assert.*;

public class ManagementLayoutTest {
    @Test public void approvedPhoneLandscapeNeverUsesTabletTable() {
        assertEquals(ManagementLayout.StorageMode.COMPACT,ManagementLayout.storageMode(420,930,420,850,1f));
    }
    @Test public void approvedTabletPortraitUsesGroupedMetricsEvenAtWideWidth() {
        assertEquals(ManagementLayout.StorageMode.CARDS,ManagementLayout.storageMode(800,900,1400,850,1f));
    }
    @Test public void approvedTabletLandscapeKeepsFullTable() {
        assertEquals(ManagementLayout.StorageMode.TABLE,ManagementLayout.storageMode(800,1280,800,1000,1f));
    }
    @Test public void phonePortraitAndNarrowSafeContentUseStackedGroups() {
        assertEquals(ManagementLayout.StorageMode.CARDS,ManagementLayout.storageMode(420,420,930,380,1f));
        assertEquals(ManagementLayout.StorageMode.CARDS,ManagementLayout.storageMode(420,700,420,510,1f));
    }
    @Test public void enlargedFontsFallBackFromWideRowsWithoutShrinkingText() {
        assertEquals(ManagementLayout.StorageMode.CARDS,ManagementLayout.storageMode(420,850,420,770,1.4f));
        assertEquals(ManagementLayout.StorageMode.CARDS,ManagementLayout.storageMode(800,1280,800,1000,1.4f));
    }
    @Test public void storageUsesContentWidthRatherThanDeviceOrientation() {
        assertEquals(ManagementLayout.StorageMode.TABLE,ManagementLayout.storageMode(850,1f));
        assertEquals(ManagementLayout.StorageMode.COMPACT,ManagementLayout.storageMode(660,1f));
        assertEquals(ManagementLayout.StorageMode.CARDS,ManagementLayout.storageMode(350,1f));
    }
    @Test public void enlargedFontsReduceStorageColumnDensity() {
        assertEquals(ManagementLayout.StorageMode.COMPACT,ManagementLayout.storageMode(850,1.4f));
        assertEquals(ManagementLayout.StorageMode.CARDS,ManagementLayout.storageMode(660,1.4f));
    }
    @Test public void phoneKeepsExistingNavigationEvenInLandscape() {
        assertEquals(ManagementLayout.Mode.PHONE, ManagementLayout.mode(420,900,420));
    }
    @Test public void tabletPortraitUsesTabs() {
        assertEquals(ManagementLayout.Mode.TABS, ManagementLayout.mode(800,800,1280));
    }
    @Test public void wideTabletLandscapeUsesSidebar() {
        assertEquals(ManagementLayout.Mode.SIDEBAR, ManagementLayout.mode(800,1280,800));
    }
    @Test public void narrowSplitWindowFallsBackToPhone() {
        assertEquals(ManagementLayout.Mode.PHONE, ManagementLayout.mode(800,480,800));
        assertEquals(ManagementLayout.Mode.TABS, ManagementLayout.mode(600,900,600));
    }
    @Test public void matchingDetailsKeepTheirCategorySelected() {
        for(String route:new String[]{"matching","file","search","compare"})
            assertEquals("matching",ManagementLayout.section(route));
        assertEquals("maintenance",ManagementLayout.section("home"));
        assertEquals("scrapers",ManagementLayout.section("scrapers"));
    }
    @Test public void topLevelTabletBackLeavesManagementButDetailsStayWithinIt() {
        assertNull(ManagementLayout.backTarget("maintenance",true));
        assertNull(ManagementLayout.backTarget("scrapers",true));
        assertEquals("home",ManagementLayout.backTarget("maintenance",false));
        assertEquals("matching",ManagementLayout.backTarget("file",true));
        assertEquals("matching",ManagementLayout.backTarget("search",true));
        assertEquals("search",ManagementLayout.backTarget("compare",true));
    }
}
