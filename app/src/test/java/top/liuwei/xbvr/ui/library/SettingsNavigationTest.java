package top.liuwei.xbvr.ui.library;

import static org.junit.Assert.*;
import org.junit.Test;

public class SettingsNavigationTest {
    @Test public void backReturnsThroughParentsBeforeLeavingLibrary() {
        SettingsNavigation nav = new SettingsNavigation();
        assertFalse(nav.back());
        nav.open(SettingsNavigation.Page.SETTINGS);
        nav.open(SettingsNavigation.Page.LIBRARY);
        assertTrue(nav.back());
        assertEquals(SettingsNavigation.Page.SETTINGS, nav.page());
        assertTrue(nav.back());
        assertEquals(SettingsNavigation.Page.HOME, nav.page());
        assertFalse(nav.back());
    }

    @Test public void aboutReturnsToSettings() {
        SettingsNavigation nav = new SettingsNavigation();
        nav.open(SettingsNavigation.Page.ABOUT);
        assertTrue(nav.back());
        assertEquals(SettingsNavigation.Page.SETTINGS, nav.page());
    }

    @Test public void routeSurvivesRecreationAndRejectsUnknownValues() {
        SettingsNavigation nav = new SettingsNavigation();
        nav.open(SettingsNavigation.Page.LIBRARY);
        SettingsNavigation restored = new SettingsNavigation();
        restored.restore(nav.savedPage());
        assertEquals(SettingsNavigation.Page.LIBRARY, restored.page());
        restored.restore("future-page");
        assertEquals(SettingsNavigation.Page.HOME, restored.page());
        restored.restore(null);
        assertEquals(SettingsNavigation.Page.HOME, restored.page());
    }
}
