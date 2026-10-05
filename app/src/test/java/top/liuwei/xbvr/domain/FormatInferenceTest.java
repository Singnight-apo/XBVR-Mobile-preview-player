package top.liuwei.xbvr.domain;

import org.junit.Test;
import static org.junit.Assert.*;

/** Characterisation and migration guards for {@link FormatInference}. */
public class FormatInferenceTest {
    private static final String UNKNOWN_REASON = "格式信息不足，请在格式菜单确认";
    private static final String UNSUPPORTED_REASON = "前后双鱼眼 / Cube / EAC 尚不支持，请选择已展开文件";

    @Test public void defaultsAndUnknownReasonAreStable() {
        Projection p = new Projection();
        assertEquals(Projection.FLAT, p.kind);
        assertEquals(Projection.MONO, p.layout);
        assertEquals(180, p.capture);
        assertEquals(75f, p.viewFov, 0f);
        assertEquals(.5f, p.centerX, 0f);
        assertEquals(.5f, p.centerY, 0f);
        assertEquals(1f, p.radius, 0f);
        assertFalse(p.known);
        assertEquals(UNKNOWN_REASON, p.reason);
        Projection unknown = FormatInference.infer("", "unknown.mp4", "", 0);
        assertFalse(unknown.known);
        assertEquals(Projection.FLAT, unknown.kind);
        assertEquals(Projection.MONO, unknown.layout);
        assertEquals(180, unknown.capture);
        assertEquals(UNKNOWN_REASON, unknown.reason);
    }

    @Test public void fisheyeGenericSceneUsesFovOnlyInsideDocumentedRange() {
        assertEquals(180, FormatInference.infer("fisheye", "", "", 179).capture);
        assertEquals(180, FormatInference.infer("fisheye", "", "", 180).capture);
        assertEquals(190, FormatInference.infer("fisheye", "", "", 190).capture);
        assertEquals(200, FormatInference.infer("fisheye", "", "", 200).capture);
        assertEquals(220, FormatInference.infer("fisheye", "", "", 220).capture);
        assertEquals(180, FormatInference.infer("fisheye", "", "", 221).capture);
    }

    @Test public void selectedFileMetadataWinsOverGenericSceneProjection() {
        assertEquals(Projection.EQUIRECT, FormatInference.infer("180_sbs", "sample_RF52_180_SBS.mp4", "", 0).kind);
        assertEquals(Projection.TB, FormatInference.infer("360_tb", "x_mono.mp4", "sbs", 0).layout);
        assertEquals(Projection.SBS, FormatInference.infer("none", "sample_SBS.mp4", "", 0).layout);
        assertEquals(Projection.MONO, FormatInference.infer("fisheye190", "sample_TB.mp4", "mono", 190).layout);
        assertEquals(Projection.FLAT, FormatInference.infer("flat", "RF52_180.mp4", "mono", 0).kind);
    }

    @Test public void preciseLensTagRefinesOnlyGenericSceneProjection() {
        assertEquals(190, FormatInference.infer("fisheye", "sample_RF52_SBS.mp4", "sbs", 0).capture);
        assertEquals(200, FormatInference.infer("fisheye", "sample_MKX200_SBS.mp4", "sbs", 0).capture);
        assertEquals(190, FormatInference.infer("fisheye190", "sample_MKX220_SBS.mp4", "sbs", 0).capture);
        assertEquals(220, FormatInference.infer("", "SAMPLE_MKX22_180_3dh.mp4", "", 0).capture);
        assertEquals(190, FormatInference.infer("dome", "RF52_180_SBS.mp4", "sbs", 0).capture);
    }

    @Test public void unsupportedPackingIsReportedAndNeverTrusted() {
        Projection dual = FormatInference.infer("", "front_back_dual_fisheye_360.mp4", "", 0);
        assertFalse(dual.known);
        assertEquals(UNSUPPORTED_REASON, dual.reason);
        assertFalse(FormatInference.infer("", "Atmosphere_5180_cubehouse_MKX999.mp4", "", 0).known);
        assertFalse(FormatInference.infer("equirectangular360", "sample_front_back_fisheye.mp4", "", 0).known);
    }

    @Test public void legacyModeKeysKeepTheirKindCaptureAndLayout() {
        String[] keys = {"180_LR", "FISHEYE_180_LR", "FISHEYE_190_LR", "FISHEYE_200_LR", "FISHEYE_220_LR", "360_LR", "360_TB", "180_MONO", "360", "FISHEYE_200", "FISHEYE_220", "SBS_MONO", "NONE"};
        int[] kinds = {1, 2, 2, 2, 2, 1, 1, 1, 1, 2, 2, 0, 0};
        int[] angles = {180, 180, 190, 200, 220, 360, 360, 180, 360, 200, 220, 180, 180};
        int[] layouts = {1, 1, 1, 1, 1, 1, 2, 0, 0, 0, 0, 1, 0};
        for (int i = 0; i < keys.length; i++) {
            Projection p = FormatInference.infer(keys[i], "", "", 0);
            assertTrue(keys[i], p.known);
            assertEquals(keys[i], kinds[i], p.kind);
            assertEquals(keys[i], angles[i], p.capture);
            assertEquals(keys[i], layouts[i], p.layout);
        }
    }

    @Test public void halfPackingOnlyFollowsFlatNonMono() {
        assertTrue(FormatInference.infer("flat", "sample_HSBS.mp4", "", 0).halfPacked);
        assertFalse(FormatInference.infer("flat", "sample_FULL_SBS.mp4", "", 0).halfPacked);
        assertFalse(FormatInference.infer("flat", "sample_FSBS.mp4", "", 0).halfPacked);
        assertFalse(FormatInference.infer("flat", "sample_FTB.mp4", "", 0).halfPacked);
        assertEquals(Projection.SBS, FormatInference.infer("flat", "sample_HSBS.mp4", "", 0).layout);
        assertFalse(FormatInference.infer("180_sbs", "sample_HSBS.mp4", "", 0).halfPacked);
    }
}
