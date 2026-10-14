package top.liuwei.xbvr.ui.library;

import org.junit.Test;
import static org.junit.Assert.*;

public class ManagementLayoutTest {
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
