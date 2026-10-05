package top.liuwei.xbvr.domain;

import org.junit.Test;
import top.liuwei.xbvr.domain.Models.Detail;
import top.liuwei.xbvr.domain.Models.Source;
import static org.junit.Assert.*;

/** Characterisation of the selected-file format rules previously inlined in PlayerActivity. */
public class SelectedFormatPolicyTest {
    private static Source source(String url) {
        Source s = new Source();
        s.url = url;
        return s;
    }

    private static Detail detailWith(int sourceCount, String metadata, String stereo, double fov) {
        Detail d = new Detail();
        d.title = "Scene title";
        d.metadata = metadata;
        d.stereo = stereo;
        d.fov = fov;
        for (int i = 0; i < sourceCount; i++) d.sources.add(source("https://host/deovr/file/" + i));
        return d;
    }

    @Test public void singleSourceFallsBackToSceneMetadata() {
        Detail d = detailWith(1, "180_sbs", "sbs", 0);
        Source s = d.sources.get(0);
        Projection p = SelectedFormatPolicy.infer(d, s);
        assertTrue(p.known);
        assertEquals(Projection.EQUIRECT, p.kind);
        assertEquals(180, p.capture);
        assertEquals(Projection.SBS, p.layout);
    }

    @Test public void singleSourceFallsBackToSceneFov() {
        Detail d = detailWith(1, "fisheye", "", 200);
        Projection p = SelectedFormatPolicy.infer(d, d.sources.get(0));
        assertTrue(p.known);
        assertEquals(Projection.FISHEYE, p.kind);
        assertEquals(200, p.capture);
    }

    @Test public void multipleSourcesDoNotUseSceneFallback() {
        Detail d = detailWith(2, "180_sbs", "sbs", 200);
        Projection p = SelectedFormatPolicy.infer(d, d.sources.get(0));
        assertFalse(p.known);
        assertEquals("格式信息不足，请在格式菜单确认", p.reason);
        assertEquals(180, p.capture);
    }

    @Test public void fileFieldsWinOverSceneMetadata() {
        Detail d = detailWith(2, "180_sbs", "sbs", 200);
        Source s = d.sources.get(0);
        s.projection = "360_tb";
        s.stereo = "tb";
        Projection p = SelectedFormatPolicy.infer(d, s);
        assertTrue(p.known);
        assertEquals(Projection.EQUIRECT, p.kind);
        assertEquals(360, p.capture);
        assertEquals(Projection.TB, p.layout);
    }

    @Test public void blankFilenameUsesTheSceneTitle() {
        Detail d = detailWith(1, "", "", 0);
        Source s = d.sources.get(0);
        s.filename = "";
        d.title = "SAMPLE_MKX200_SBS.mp4";
        Projection p = SelectedFormatPolicy.infer(d, s);
        assertTrue(p.known);
        assertEquals(Projection.FISHEYE, p.kind);
        assertEquals(200, p.capture);
    }

    @Test public void positiveSourceFovWinsOverSceneFov() {
        Detail d = detailWith(1, "fisheye", "", 200);
        Source s = d.sources.get(0);
        s.fov = 220;
        assertEquals(220, SelectedFormatPolicy.infer(d, s).capture);
        s.fov = 180;
        assertEquals(180, SelectedFormatPolicy.infer(d, s).capture);
    }

    @Test public void nonPositiveSourceFovFallsBackOnlyForASingleSource() {
        Detail single = detailWith(1, "fisheye", "", 220);
        Source s = single.sources.get(0);
        s.fov = 0;
        assertEquals(220, SelectedFormatPolicy.infer(single, s).capture);
        s.fov = -1;
        assertEquals(220, SelectedFormatPolicy.infer(single, s).capture);

        Detail multiple = detailWith(2, "fisheye", "", 220);
        Source m = multiple.sources.get(0);
        m.projection = "fisheye";
        m.fov = 0;
        assertEquals(180, SelectedFormatPolicy.infer(multiple, m).capture);
    }

    @Test public void fileProjectionBeatsTheGenericSceneProjectionButKeepsItsLayoutRule() {
        Detail d = detailWith(1, "360", "mono", 0);
        Source s = d.sources.get(0);
        s.projection = "fisheye190";
        Projection p = SelectedFormatPolicy.infer(d, s);
        assertEquals(Projection.FISHEYE, p.kind);
        assertEquals(190, p.capture);
        assertEquals(Projection.MONO, p.layout);
    }
}
