package top.liuwei.xbvr.ui.player;
public final class ControlsVisibility {
    private boolean keepHiddenAfterSeek;
    public ControlsVisibility() {}
    public void beginSeek(){keepHiddenAfterSeek=true;}
    /** Only an explicit show action releases the latch, not asynchronous player callbacks. */
    public void allowShow(){keepHiddenAfterSeek=false;}
    public boolean showOnPlaybackStop(){return !keepHiddenAfterSeek;}
    public static boolean canHide(boolean active, boolean shown, boolean seeking, int dialogs, boolean hasPlayer, boolean playing) {
        return active && shown && !seeking && dialogs == 0 && hasPlayer && playing;
    }
}
