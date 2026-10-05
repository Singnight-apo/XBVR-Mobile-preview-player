package top.liuwei.xbvr.data;

import org.json.JSONObject;
import org.junit.Test;
import top.liuwei.xbvr.domain.Projection;
import static org.junit.Assert.*;

/** Fixed-sample guards for the saved playback JSON that the previous Store wrote and read. */
public class PlaybackJsonCodecTest {
    private static final String SAVED_MANUAL =
            "{\"kind\":2,\"layout\":1,\"capture\":190,\"eye\":1,\"yaw\":12.5,\"pitch\":-3.25,\"fov\":80,"
                    + "\"cx\":0.4,\"cy\":0.6,\"radius\":1.2,\"rotation\":15,\"mirror\":true,\"half\":false,\"override\":true}";
    private static final String SAVED_AUTOMATIC =
            "{\"kind\":1,\"layout\":1,\"capture\":360,\"eye\":0,\"yaw\":4,\"pitch\":0,\"fov\":75,\"cx\":0.5,"
                    + "\"cy\":0.5,\"radius\":1,\"rotation\":0,\"mirror\":false,\"half\":false,\"override\":false}";

    @Test public void emptyObjectKeepsDefaultsAndReportsAutomatic() {
        Projection p = new Projection();
        p.kind = Projection.FISHEYE;
        p.capture = 200;
        p.viewFov = 100;
        assertFalse(PlaybackJsonCodec.apply("{}", p));
        assertEquals(Projection.FISHEYE, p.kind);
        assertEquals(200, p.capture);
        assertFalse(p.known);
        assertEquals(0, p.eye);
        assertEquals(0f, p.yaw, 0f);
        assertEquals(75f, p.viewFov, 0f);
        assertEquals(.5f, p.centerX, 0f);
        assertEquals(.5f, p.centerY, 0f);
        assertEquals(1f, p.radius, 0f);
        assertEquals(0f, p.rotation, 0f);
        assertFalse(p.mirror);
        assertFalse(p.halfPacked);
    }

    @Test public void storedManualSampleRestoresEveryField() {
        Projection p = new Projection();
        assertTrue(PlaybackJsonCodec.apply(SAVED_MANUAL, p));
        assertEquals(Projection.FISHEYE, p.kind);
        assertEquals(Projection.SBS, p.layout);
        assertEquals(190, p.capture);
        assertTrue(p.known);
        assertEquals("使用已保存的手动格式", p.reason);
        assertFalse(p.halfPacked);
        assertEquals(1, p.eye);
        assertEquals(12.5f, p.yaw, 0f);
        assertEquals(-3.25f, p.pitch, 0f);
        assertEquals(80f, p.viewFov, 0f);
        assertEquals(.4f, p.centerX, 0f);
        assertEquals(.6f, p.centerY, 0f);
        assertEquals(1.2f, p.radius, 0f);
        assertEquals(15f, p.rotation, 0f);
        assertTrue(p.mirror);
    }

    @Test public void storedAutomaticSampleKeepsTheInferredFormatAndReportsAutomatic() {
        Projection p = new Projection();
        p.kind = Projection.FLAT;
        p.capture = 180;
        assertFalse(PlaybackJsonCodec.apply(SAVED_AUTOMATIC, p));
        assertEquals(Projection.FLAT, p.kind);
        assertEquals(180, p.capture);
        assertFalse(p.known);
        assertEquals(4f, p.yaw, 0f);
        assertEquals(0, p.eye);
    }

    @Test public void missingFieldsUseTheDocumentedDefaults() {
        Projection p = new Projection();
        assertFalse(PlaybackJsonCodec.apply("{\"override\":false,\"yaw\":5}", p));
        assertEquals(5f, p.yaw, 0f);
        assertEquals(75f, p.viewFov, 0f);
        assertEquals(.5f, p.centerX, 0f);
        assertEquals(1f, p.radius, 0f);
        assertEquals(0f, p.rotation, 0f);
        assertEquals(0, p.eye);
        assertFalse(p.mirror);
    }

    @Test public void invalidJsonReportsAutomaticWithoutThrowing() {
        Projection p = new Projection();
        assertFalse(PlaybackJsonCodec.apply("not json", p));
        assertFalse(p.known);
        assertEquals(75f, p.viewFov, 0f);
    }

    @Test public void manualFlagWithoutKindLeavesTheSamePartialUpdate() {
        Projection p = new Projection();
        p.eye = 1;
        // The previous restore assigned kind, then threw on the missing layout key: kind survives,
        // while capture/known/reason and every later field keep their previous values.
        assertFalse(PlaybackJsonCodec.apply("{\"override\":true,\"kind\":2,\"eye\":0,\"yaw\":9}", p));
        assertEquals(Projection.FISHEYE, p.kind);
        assertFalse(p.known);
        assertEquals("格式信息不足，请在格式菜单确认", p.reason);
        assertEquals("the later fields stay untouched on failure", 1, p.eye);
        assertEquals(0f, p.yaw, 0f);
        assertEquals(75f, p.viewFov, 0f);
    }

    @Test public void encodeWritesExactlyTheOldFieldNames() throws Exception {
        Projection p = new Projection();
        p.kind = Projection.EQUIRECT;
        p.layout = Projection.TB;
        p.capture = 360;
        p.eye = 1;
        p.yaw = 1.5f;
        p.pitch = -2.5f;
        p.viewFov = 90;
        p.centerX = .25f;
        p.centerY = .75f;
        p.radius = 1.5f;
        p.rotation = 30;
        p.mirror = true;
        p.halfPacked = true;

        JSONObject j = new JSONObject(PlaybackJsonCodec.encode(p, true));
        assertEquals(14, j.length());
        assertEquals(Projection.EQUIRECT, j.getInt("kind"));
        assertEquals(Projection.TB, j.getInt("layout"));
        assertEquals(360, j.getInt("capture"));
        assertEquals(1, j.getInt("eye"));
        assertEquals(1.5, j.getDouble("yaw"), 1e-6);
        assertEquals(-2.5, j.getDouble("pitch"), 1e-6);
        assertEquals(90.0, j.getDouble("fov"), 1e-6);
        assertEquals(.25, j.getDouble("cx"), 1e-6);
        assertEquals(.75, j.getDouble("cy"), 1e-6);
        assertEquals(1.5, j.getDouble("radius"), 1e-6);
        assertEquals(30.0, j.getDouble("rotation"), 1e-6);
        assertTrue(j.getBoolean("mirror"));
        assertTrue(j.getBoolean("half"));
        assertTrue(j.getBoolean("override"));
    }

    @Test public void encodeThenApplyRoundTrips() {
        Projection source = new Projection();
        source.kind = Projection.FISHEYE;
        source.layout = Projection.SBS;
        source.capture = 220;
        source.eye = 1;
        source.yaw = 7.25f;
        source.pitch = -1.5f;
        source.viewFov = 95;
        source.centerX = .3f;
        source.centerY = .7f;
        source.radius = 1.4f;
        source.rotation = 5;
        source.mirror = true;
        source.halfPacked = false;

        Projection restored = new Projection();
        assertTrue(PlaybackJsonCodec.apply(PlaybackJsonCodec.encode(source, true), restored));
        assertEquals(source.kind, restored.kind);
        assertEquals(source.layout, restored.layout);
        assertEquals(source.capture, restored.capture);
        assertEquals(source.eye, restored.eye);
        assertEquals(source.yaw, restored.yaw, 0f);
        assertEquals(source.pitch, restored.pitch, 0f);
        assertEquals(source.viewFov, restored.viewFov, 0f);
        assertEquals(source.centerX, restored.centerX, 0f);
        assertEquals(source.centerY, restored.centerY, 0f);
        assertEquals(source.radius, restored.radius, 0f);
        assertEquals(source.rotation, restored.rotation, 0f);
        assertEquals(source.mirror, restored.mirror);
        assertEquals(source.halfPacked, restored.halfPacked);
        assertTrue(restored.known);
    }

    @Test public void automaticRoundTripKeepsTheManualFlagFalse() throws Exception {
        Projection source = new Projection();
        String encoded = PlaybackJsonCodec.encode(source, false);
        assertFalse(new JSONObject(encoded).getBoolean("override"));
        Projection restored = new Projection();
        assertFalse(PlaybackJsonCodec.apply(encoded, restored));
        assertFalse(restored.known);
    }
}
