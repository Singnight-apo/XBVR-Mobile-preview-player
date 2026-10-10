package top.liuwei.xbvr.data;

import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.*;
import org.junit.Test;
import top.liuwei.xbvr.domain.ServerProfile;
import static org.junit.Assert.*;

public class FilePreviewRepositoryTest {
    @Test public void sameFileIdOnAnotherMountedServerIsRejected() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start();String origin=server.url("/").toString().replaceAll("/$","");String base=origin+"/instance-a";
            server.enqueue(new MockResponse().setBody("{authorized:1,encodings:[{videoSources:[{url:'"+origin+"/instance-b/api/dms/file/202'}]}]}"));
            var repository=new FilePreviewRepository(new XbvrApi(new ServerProfile("qa",base,"","","","")));
            try {repository.detail(base+"/deovr/file/202");fail("Another mounted server's file must not be previewed");}catch(java.io.IOException expected){}
            server.enqueue(new MockResponse().setBody("{authorized:1,encodings:[{videoSources:[{url:'"+base+"/api/dms/file/202/name.mp4?token=keep'}]}]}"));
            assertEquals(base+"/api/dms/file/202/name.mp4?token=keep&dnt=true",repository.detail(base+"/deovr/file/202").sources.get(0).url);
        }
    }
    @Test public void unmatchedFileUsesOnlyItsOwnMediaAndDisablesTracking() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start();String base=server.url("/").toString().replaceAll("/$","");
            server.enqueue(new MockResponse().setBody("{authorized:1,title:'unmatched.mp4',encodings:[{videoSources:[{url:'"+base+"/api/dms/file/202?token=keep',width:1280,height:720}]}]}"));
            var repository=new FilePreviewRepository(new XbvrApi(new ServerProfile("qa",base,"viewer","secret","","")));
            var detail=repository.detail(base+"/deovr/file/202");
            assertEquals(1,detail.sources.size());assertEquals(base+"/api/dms/file/202?token=keep&dnt=true",detail.sources.get(0).url);
            assertFalse(detail.writeFavorite);assertEquals("",detail.hsUrl);
            var request=server.takeRequest(1,TimeUnit.SECONDS);assertEquals("/deovr/file/202",request.getPath());
            assertTrue(request.getBody().readUtf8().contains("viewer"));assertEquals(1,server.getRequestCount());
            try {repository.favorite(detail,true);fail("Preview must not write server favorites");}catch(java.io.IOException expected){}
            assertEquals(1,server.getRequestCount());
        }
    }
    @Test public void rejectsAnotherFilesMediaAndCrossOriginBeforeForwardingCredentials() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start();String base=server.url("/").toString().replaceAll("/$","");
            server.enqueue(new MockResponse().setBody("{authorized:1,encodings:[{videoSources:[{url:'"+base+"/api/dms/file/999'}]}]}"));
            var repository=new FilePreviewRepository(new XbvrApi(new ServerProfile("qa",base,"viewer","secret","","")));
            try {repository.detail(base+"/deovr/file/202");fail();}catch(java.io.IOException expected){}
            try {repository.detail("https://other.invalid/deovr/file/202");fail();}catch(java.io.IOException expected){}
            assertEquals(1,server.getRequestCount());
        }
    }
}
