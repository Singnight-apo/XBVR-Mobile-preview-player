package top.liuwei.xbvr.domain;

import org.junit.Test;
import static org.junit.Assert.*;

public class SeekGestureTest {
    private SeekGesture start(float y,long duration,long position) {
        SeekGesture gesture=new SeekGesture();gesture.begin(200,y,1000,1000,10,duration,position);return gesture;
    }
    @Test public void onlyBottomHorizontalDragSeeksAndLocksFromTouchDown() {
        SeekGesture bottom=start(850,600000,100000);
        assertEquals(SeekGesture.Mode.PENDING,bottom.move(205,852));
        assertEquals(SeekGesture.Mode.SEEK,bottom.move(300,850));
        assertEquals(112000,bottom.target());
        assertEquals(SeekGesture.Mode.SEEK,bottom.move(400,100));
        assertEquals(124000,bottom.finish());
        SeekGesture top=start(400,600000,100000);
        assertEquals(SeekGesture.Mode.PAN,top.move(300,850));assertEquals(-1,top.finish());
    }
    @Test public void verticalDragLocksToPanAndTapDoesNotSeek() {
        SeekGesture g=start(850,600000,100000);
        assertEquals(SeekGesture.Mode.PAN,g.move(205,900));
        assertEquals(SeekGesture.Mode.PAN,g.move(500,900));assertEquals(-1,g.finish());
        assertEquals(-1,start(850,600000,100000).finish());
    }
    @Test public void cancelsWithoutCommitForMultiTouchOrSystemInterruption() {
        SeekGesture g=start(850,600000,100000);g.move(400,850);g.cancel();
        assertEquals(SeekGesture.Mode.CANCELLED,g.move(800,850));assertEquals(-1,g.finish());
        g.continuePan();assertEquals(SeekGesture.Mode.PAN,g.move(900,850));assertEquals(-1,g.finish());
    }
    @Test public void sensitivityIsRelativeToWidthAndClampsToDuration() {
        SeekGesture g=start(850,20000,10000);g.move(1200,850);assertEquals(20000,g.finish());
        g=start(850,20000,10000);g.move(-800,850);assertEquals(0,g.finish());
        g=start(850,0,10000);assertEquals(SeekGesture.Mode.PAN,g.move(500,850));assertEquals(-1,g.finish());
        g=start(850,600000,100000);g.move(700,850);assertEquals(160000,g.finish());
    }
}
