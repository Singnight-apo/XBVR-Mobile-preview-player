package top.liuwei.xbvr.ui.library;

/** Settings routes are independent of library loading and filtering. */
public final class SettingsNavigation {
    public enum Page { HOME, SETTINGS, LIBRARY, ABOUT }
    private Page page = Page.HOME;

    public Page page() { return page; }
    public void open(Page value) { page = value; }
    public String savedPage() { return page.name(); }

    public void restore(String value) {
        try { page = Page.valueOf(value); }
        catch (IllegalArgumentException | NullPointerException e) { page = Page.HOME; }
    }

    public boolean back() {
        if (page == Page.HOME) return false;
        page = page == Page.SETTINGS ? Page.HOME : Page.SETTINGS;
        return true;
    }
}
