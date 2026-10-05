package top.liuwei.xbvr.ui.player;
public final class ControlsVisibility {
    private ControlsVisibility() {}
    public static boolean canHide(boolean active, boolean shown, boolean seeking, int dialogs, boolean hasPlayer, boolean playing) {
        return active && shown && !seeking && dialogs == 0 && hasPlayer && playing;
    }
}
