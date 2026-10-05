package top.liuwei.xbvr.media;

import org.junit.Test;
import static org.junit.Assert.*;

public class RendererShaderTest {
    @Test public void capableGpuKeepsHighpAndUnsupportedGpuUsesMediump(){
        assertTrue(RendererShader.supportsHighp(127,127,23));
        assertTrue(RendererShader.supportsHighp(62,62,16));
        // ES2 reports zero range and precision for unsupported fragment highp.
        assertFalse(RendererShader.supportsHighp(0,0,0));
        assertFalse(RendererShader.supportsHighp(127,127,0));
    }
    @Test public void bothShaderStagesUseTheSameVaryingPrecision(){
        for(boolean highp:new boolean[]{true,false}){
            String qualifier=highp?"highp":"mediump";
            assertTrue(RendererShader.vertex(highp).contains("varying "+qualifier+" vec2 pos;"));
            assertTrue(RendererShader.fragment(highp).contains("varying "+qualifier+" vec2 pos;"));
            assertTrue(RendererShader.fragment(highp).contains("precision "+qualifier+" float;"));
        }
    }
    @Test public void precisionFallbackChangesDeclarationsOnly(){
        String high=RendererShader.fragment(true),medium=RendererShader.fragment(false);
        assertEquals(high.replace("highp","mediump"),medium);
        assertTrue(high.contains("uniform lowp samplerExternalOES video;"));
        assertTrue(high.contains("kind,stereoLayout,eye"));
        assertFalse(high.matches("(?s).*\\blayout\\b.*"));
        assertTrue(high.contains("gl_FragColor=texture2D(video,mapped);"));
    }
}
