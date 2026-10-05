package top.liuwei.xbvr.ui.player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The auto-hide condition only allows hiding when every one of the six inputs permits it. */
public class ControlsVisibilityTest {
    @Test
    public void hidesOnlyWhenEveryInputAllowsIt() {
        assertTrue(ControlsVisibility.canHide(true, true, false, 0, true, true));
    }

    @Test
    public void inactivePlayerForbidsHiding() {
        assertFalse(ControlsVisibility.canHide(false, true, false, 0, true, true));
    }

    @Test
    public void alreadyHiddenControlsForbidHiding() {
        assertFalse(ControlsVisibility.canHide(true, false, false, 0, true, true));
    }

    @Test
    public void seekingForbidsHiding() {
        assertFalse(ControlsVisibility.canHide(true, true, true, 0, true, true));
    }

    @Test
    public void openDialogForbidsHiding() {
        assertFalse(ControlsVisibility.canHide(true, true, false, 1, true, true));
    }

    @Test
    public void missingPlayerForbidsHiding() {
        assertFalse(ControlsVisibility.canHide(true, true, false, 0, false, true));
    }

    @Test
    public void pausedPlaybackForbidsHiding() {
        assertFalse(ControlsVisibility.canHide(true, true, false, 0, true, false));
    }

    @Test
    public void allBlockingValuesForbidHiding() {
        assertFalse(ControlsVisibility.canHide(false, false, true, 2, false, false));
    }
}
