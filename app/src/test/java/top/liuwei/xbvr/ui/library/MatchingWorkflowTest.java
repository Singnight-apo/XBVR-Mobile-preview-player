package top.liuwei.xbvr.ui.library;

import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.*;

public class MatchingWorkflowTest {
    @Test public void filenameBecomesPlainSearchWithoutBleveOperators() {
        assertEquals("Studio Sister s Test 2026 1080p",MatchingWorkflow.query("C:\\videos\\Studio_Sister’s.Test-(2026)+[1080p].mp4"));
        assertEquals("中文 标题 01",MatchingWorkflow.query("/media/中文【标题】_01.MKV"));
        assertEquals("A B C",MatchingWorkflow.query("Ａ＆Ｂ＋Ｃ.webm"));
    }
    @Test public void missingAndSymbolOnlyNamesDoNotSearchEntireLibrary() {
        assertEquals("",MatchingWorkflow.query(null));
        assertEquals("",MatchingWorkflow.query("---[].mp4"));
        assertEquals("Title final",MatchingWorkflow.query("Title.final"));
    }
    @Test public void nextFileUsesQueueOrderAndNeverWrapsOrWrites() throws Exception {
        JSONArray files=new JSONArray("[{id:7,filename:'first.mp4'},null,{id:9,filename:'second.mp4'},{id:12,filename:'last.mp4'}]");
        assertEquals(2,MatchingWorkflow.nextIndex(files,7));
        assertEquals(3,MatchingWorkflow.nextIndex(files,9));
        assertEquals(-1,MatchingWorkflow.nextIndex(files,12));
        assertEquals(-1,MatchingWorkflow.nextIndex(files,99));
        assertEquals(4,files.length());
    }
}
