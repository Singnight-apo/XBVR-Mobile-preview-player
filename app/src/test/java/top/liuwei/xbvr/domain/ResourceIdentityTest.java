package top.liuwei.xbvr.domain;

import org.junit.Test;
import static org.junit.Assert.*;

/** Differential guards for the stable object identity that positions, favourites and views key on. */
public class ResourceIdentityTest {
    private static final String PROFILE = "643ece43-2aa5-4a4a-82a5-f131e94c0da7";

    @Test public void defaultPortsAreNormalisedPerScheme() {
        assertEquals("http://host:80/proxy/scene:12", ResourceIdentity.of("http://host/proxy/deovr/12"));
        assertEquals("https://host:443/proxy/scene:12", ResourceIdentity.of("https://host/proxy/deovr/12"));
    }

    @Test public void explicitPortsArePreserved() {
        assertEquals("http://host:9999/proxy/scene:12", ResourceIdentity.of("http://host:9999/proxy/deovr/12"));
        assertEquals("https://host:8443/proxy/scene:12", ResourceIdentity.of("https://host:8443/proxy/deovr/12"));
        assertEquals("http://10.0.2.2:18766/file:1", ResourceIdentity.of("http://10.0.2.2:18766/deovr/file/1"));
    }

    @Test public void schemeAndHostCaseAreLowered() {
        assertEquals("https://host.example:443/proxy/scene:12", ResourceIdentity.of("HTTPS://HOST.Example/proxy/deovr/12"));
    }

    @Test public void proxyPrefixIsKeptAndSignificant() {
        assertEquals("https://host:443/a/scene:12", ResourceIdentity.of("https://host/a/deovr/12"));
        assertNotEquals(ResourceIdentity.of("https://host/a/deovr/12"), ResourceIdentity.of("https://host/b/deovr/12"));
    }

    @Test public void deovrAndHeresphereShareOneIdentity() {
        assertEquals(ResourceIdentity.of("https://host/proxy/deovr/12?dnt=true"),
                ResourceIdentity.of("https://host/proxy/heresphere/12?session=abc"));
    }

    @Test public void sceneAndFileStayDistinct() {
        assertNotEquals(ResourceIdentity.of("https://host/proxy/deovr/12"),
                ResourceIdentity.of("https://host/proxy/deovr/file/12"));
        assertNotEquals(ResourceIdentity.of("https://host/proxy/deovr/12"),
                ResourceIdentity.of("https://host/proxy/api/dms/file/12"));
    }

    @Test public void dmsFilePathsMatchTheDeovrFileForm() {
        assertEquals(ResourceIdentity.of("https://host/proxy/deovr/file/12?dnt=true"),
                ResourceIdentity.of("https://host/proxy/api/dms/file/12/title?session=abc"));
    }

    @Test public void trailingSlashAndBarePrefixAreEquivalent() {
        assertEquals(ResourceIdentity.of("https://host/deovr/12"), ResourceIdentity.of("https://host/deovr/12/"));
    }

    @Test public void unknownAndInvalidUrlsAreReturnedUnchanged() {
        String plain = "https://host/video.mp4?token=a%2Fb";
        assertEquals(plain, ResourceIdentity.of(plain));
        String relative = "notaurl";
        assertEquals(relative, ResourceIdentity.of(relative));
        String withSpace = "not a url";
        assertEquals(withSpace, ResourceIdentity.of(withSpace));
        assertEquals("", ResourceIdentity.of(""));
        String nonNumeric = "https://host/deovr/abc";
        assertEquals(nonNumeric, ResourceIdentity.of(nonNumeric));
    }

    @Test public void playbackKeyReproducesTheStoredOldFormatKeys() {
        // Written by the previous implementation and still present on the device as pos:/fav:/source: keys.
        assertEquals(PROFILE + ":http://10.0.2.2:18766/file:1",
                ResourceIdentity.playbackKey(PROFILE, "http://10.0.2.2:18766/deovr/file/1"));
        assertEquals(PROFILE + ":http://10.0.2.2:18766/scene:1",
                ResourceIdentity.playbackKey(PROFILE, "http://10.0.2.2:18766/deovr/1"));
        assertEquals(PROFILE + ":http://10.0.2.2:18766/scene:8",
                ResourceIdentity.playbackKey(PROFILE, "http://10.0.2.2:18766/heresphere/8?session=x"));
        assertEquals(ResourceIdentity.playbackKey(PROFILE, "https://host/deovr/12?dnt=true"),
                ResourceIdentity.playbackKey(PROFILE, "https://host/deovr/12"));
    }
}
