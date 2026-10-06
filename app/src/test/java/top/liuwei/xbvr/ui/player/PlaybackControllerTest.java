package top.liuwei.xbvr.ui.player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import org.junit.Test;
import top.liuwei.xbvr.domain.FavoriteRepository;
import top.liuwei.xbvr.domain.MediaDetailsRepository;
import top.liuwei.xbvr.domain.Models.Detail;
import top.liuwei.xbvr.domain.Models.Source;
import top.liuwei.xbvr.domain.Models.Subtitle;
import top.liuwei.xbvr.domain.PlaybackRepository;
import top.liuwei.xbvr.domain.PlayerPort;
import top.liuwei.xbvr.domain.Projection;
import top.liuwei.xbvr.domain.ResourceIdentity;
import top.liuwei.xbvr.domain.TrackOption;
import static org.junit.Assert.*;

/** Playback decisions and persistence driven through fakes; no Android, no network. */
public class PlaybackControllerTest {
    private static final String ENTRY = "p1:https://s.test:443/scene:1";
    private static final String FILE_1 = ResourceIdentity.playbackKey("p1", "https://s.test/deovr/file/1");
    private static final String FILE_2 = ResourceIdentity.playbackKey("p1", "https://s.test/deovr/file/2");

    private static final class Log {
        final List<String> events = new ArrayList<>();

        void add(String event) {
            events.add(event);
        }

        int first(String prefix) {
            for (int i = 0; i < events.size(); i++) if (events.get(i).startsWith(prefix)) return i;
            return -1;
        }

        int last(String prefix) {
            for (int i = events.size() - 1; i >= 0; i--) if (events.get(i).startsWith(prefix)) return i;
            return -1;
        }

        boolean has(String prefix) {
            return first(prefix) >= 0;
        }

        int count(String prefix) {
            int total = 0;
            for (String event : events) if (event.startsWith(prefix)) total++;
            return total;
        }
    }

    private static final class FakePort implements PlayerPort {
        final Log log;
        final List<String> preparedUrls = new ArrayList<>();
        final List<Long> preparedPositions = new ArrayList<>();
        final List<Boolean> preparedPlay = new ArrayList<>();
        boolean engine = true;
        long position;
        long duration;
        boolean playWhenReady;
        boolean playing;
        boolean ended;

        FakePort(Log log) {
            this.log = log;
        }

        public void prepare(Source source, List<Subtitle> subtitles, long position, boolean play) {
            log.add("port.prepare");
            preparedUrls.add(source == null ? null : source.url);
            preparedPositions.add(position);
            preparedPlay.add(play);
            this.position = position;
            this.playWhenReady = play;
        }

        public boolean hasEngine() {
            return engine;
        }

        public long position() {
            log.add("port.position");
            return position;
        }

        public long duration() {
            return duration;
        }

        public boolean playWhenReady() {
            log.add("port.playWhenReady");
            return playWhenReady;
        }

        public boolean isPlaying() {
            return playing;
        }

        public boolean ended() {
            return ended;
        }

        public void play() {
            log.add("port.play");
            playWhenReady = true;
            playing = true;
        }

        public void pause() {
            log.add("port.pause");
            playWhenReady = false;
            playing = false;
        }

        public void seekTo(long value) {
            log.add("port.seekTo");
            position = value;
        }

        public void speed(float value) {
            log.add("port.speed");
        }

        public List<TrackOption> tracks() {
            return List.of();
        }

        public void audioAuto() {}

        public void subtitlesOff() {}

        public void selectTrack(String token) {}

        public void stop() {
            log.add("port.stop");
            engine = false;
        }
    }

    private static final class FakePlayback implements PlaybackRepository {
        final Log log;
        final Map<String, Long> positions = new HashMap<>();
        final Map<String, String> sources = new HashMap<>();
        final List<String> savedKeys = new ArrayList<>();
        boolean restoreResult;
        Projection restoreProjection;

        FakePlayback(Log log) {
            this.log = log;
        }

        public long position(String key) {
            log.add("playback.position:" + key);
            return positions.getOrDefault(key, 0L);
        }

        public long lastWatched(String key) {
            log.add("playback.lastWatched:" + key);
            return 0L;
        }

        public void save(String key, long position, Projection view, boolean manual) {
            log.add("playback.save:" + key + "@" + position);
            savedKeys.add(key);
        }

        public boolean restore(String key, Projection target) {
            log.add("playback.restore:" + key);
            if (restoreProjection != null) copy(restoreProjection, target);
            return restoreResult;
        }

        public String selectedSource(String entryKey) {
            log.add("playback.selectedSource.read");
            return sources.get(entryKey);
        }

        public void selectedSource(String entryKey, String url) {
            log.add("playback.selectedSource.write:" + url);
            sources.put(entryKey, url);
        }

        public void entryPosition(String entryKey, long position) {
            log.add("playback.entryPosition:" + entryKey + "@" + position);
        }
    }

    private static final class FakeFavorites implements FavoriteRepository {
        final Set<String> keys = new HashSet<>();

        public boolean favorite(String key) {
            return keys.contains(key);
        }

        public void favorite(String key, boolean value) {
            if (value) keys.add(key);
            else keys.remove(key);
        }
    }

    private static final class FakeMedia implements MediaDetailsRepository {
        final Log log;
        Detail result;
        Exception detailFailure;
        Exception favoriteFailure;
        final List<Boolean> favorites = new ArrayList<>();

        FakeMedia(Log log) {
            this.log = log;
        }

        public Detail detail(String url) throws Exception {
            log.add("media.detail");
            if (detailFailure != null) throw detailFailure;
            return result;
        }

        public void favorite(Detail detail, boolean value) throws Exception {
            log.add("media.favorite:" + value);
            if (favoriteFailure != null) throw favoriteFailure;
            favorites.add(value);
        }
    }

    private static final class Recorder implements PlaybackController.Listener {
        final Log log;
        boolean seeking;

        Recorder(Log log) {
            this.log = log;
        }

        public boolean alive() {
            return true;
        }

        public boolean isSeeking() {
            return seeking;
        }

        public void title(String value) {
            log.add("ui.title");
        }

        public void hint() {
            log.add("ui.hint");
        }

        public void settings() {
            log.add("ui.settings");
        }

        public void videoSize() {
            log.add("ui.videoSize");
        }

        public void render() {
            log.add("ui.render");
        }

        public void gyroBaseReset() {
            log.add("ui.gyroBase");
        }

        public void playButton() {
            log.add("ui.playButton");
        }

        public void time(long position, long duration) {
            log.add("ui.time");
        }

        public void progress(int value) {
            log.add("ui.progress");
        }

        public void showControls() {
            log.add("ui.showControls");
        }

        public void scheduleHide() {
            log.add("ui.scheduleHide");
        }

        public void loadFailed(Throwable failure) {
            log.add("ui.loadFailed");
        }

        public void playbackFailed(String phase, RuntimeException failure, String graphics) {
            log.add("ui.playbackFailed:" + phase);
        }

        public void engineFailure(String phase, RuntimeException failure) {
            log.add("ui.engineFailure:" + phase);
        }

        public void localFavoriteUpdated() {
            log.add("ui.localFavorite");
        }

        public void serverFavoriteConfirmed() {
            log.add("ui.serverConfirmed");
        }

        public void serverFavoriteFailed(Throwable failure) {
            log.add("ui.serverFailed");
        }
    }

    private static final class Queue implements Executor {
        final List<Runnable> tasks = new ArrayList<>();

        public void execute(Runnable task) {
            tasks.add(task);
        }

        void run() {
            List<Runnable> pending = new ArrayList<>(tasks);
            tasks.clear();
            for (Runnable task : pending) task.run();
        }
    }

    private static void copy(Projection from, Projection to) {
        to.kind = from.kind;
        to.layout = from.layout;
        to.capture = from.capture;
        to.eye = from.eye;
        to.yaw = from.yaw;
        to.pitch = from.pitch;
        to.viewFov = from.viewFov;
        to.centerX = from.centerX;
        to.centerY = from.centerY;
        to.radius = from.radius;
        to.rotation = from.rotation;
        to.mirror = from.mirror;
        to.halfPacked = from.halfPacked;
        to.known = from.known;
        to.reason = from.reason;
    }

    private static Source source(String url) {
        Source value = new Source();
        value.name = "File";
        value.url = url;
        return value;
    }

    private static Detail detail(String... urls) {
        Detail value = new Detail();
        value.title = "Scene";
        for (String url : urls) value.sources.add(source(url));
        return value;
    }

    private static PlaybackController controller(
            FakePort port,
            FakePlayback playback,
            FakeFavorites favorites,
            FakeMedia media,
            Recorder ui,
            Executor io) {
        return new PlaybackController(
                port, playback, favorites, media, "p1", io, Runnable::run, ui);
    }

    @Test
    public void selectReadsTheEngineSavesTheOldSourceThenSwitches() {
        Log log = new Log();
        FakePort port = new FakePort(log);
        port.position = 5000;
        port.playWhenReady = true;
        FakePlayback playback = new FakePlayback(log);
        Recorder ui = new Recorder(log);
        PlaybackController c = controller(port, playback, new FakeFavorites(), new FakeMedia(log), ui, Runnable::run);
        c.state().entryKey = ENTRY;
        c.state().detail = detail("https://s.test/deovr/file/1", "https://s.test/deovr/file/2");
        c.state().fileKey = FILE_1;
        c.state().loaded = true;
        c.state().active = true;

        c.select(1, true);

        assertTrue(log.first("port.position") < log.first("playback.save:"));
        assertTrue(log.first("port.playWhenReady") < log.first("playback.save:"));
        assertTrue(log.first("playback.save:") < log.first("playback.selectedSource.write:"));
        assertTrue(log.first("playback.selectedSource.write:") < log.first("port.prepare"));
        assertEquals("https://s.test/deovr/file/2", port.preparedUrls.get(0));
        assertEquals(5000L, (long) port.preparedPositions.get(0));
        assertTrue(port.preparedPlay.get(0));
        assertEquals(FILE_2, c.state().fileKey);
    }

    @Test
    public void keepKeepsTheCurrentPositionAndReadsTheFilePositionOtherwise() {
        Log log = new Log();
        FakePort port = new FakePort(log);
        port.position = 5000;
        FakePlayback playback = new FakePlayback(log);
        Recorder ui = new Recorder(log);
        PlaybackController c = controller(port, playback, new FakeFavorites(), new FakeMedia(log), ui, Runnable::run);
        c.state().entryKey = ENTRY;
        c.state().detail = detail("https://s.test/deovr/file/1", "https://s.test/deovr/file/2");
        c.state().fileKey = FILE_1;
        c.state().loaded = true;
        c.state().active = true;

        c.select(1, true);
        assertEquals(5000L, c.state().savedPosition);
        assertFalse(log.has("playback.position:" + FILE_2));

        playback.positions.put(FILE_2, 1234L);
        c.select(1, false);
        assertEquals(1234L, c.state().savedPosition);
        assertTrue(log.has("playback.position:" + FILE_2));
    }

    @Test
    public void aPausedEngineKeepsItsIntentAcrossStopAndStart() {
        Log log = new Log();
        FakePort port = new FakePort(log);
        port.position = 700;
        port.playWhenReady = false;
        port.duration = 3000;
        FakePlayback playback = new FakePlayback(log);
        Recorder ui = new Recorder(log);
        PlaybackController c = controller(port, playback, new FakeFavorites(), new FakeMedia(log), ui, Runnable::run);
        c.state().entryKey = ENTRY;
        c.state().fileKey = FILE_1;
        c.state().source = source("https://s.test/deovr/file/1");
        c.state().detail = detail("https://s.test/deovr/file/1");
        c.state().loaded = true;
        c.state().active = true;

        c.stop();

        assertFalse(c.state().active);
        assertFalse(c.state().wasPlaying);
        assertEquals(700L, c.state().savedPosition);
        assertFalse(port.hasEngine());
        assertTrue(log.has("playback.save:" + FILE_1));

        c.start();
        c.prepareIfLoaded();

        assertTrue(c.state().active);
        assertEquals(1, port.preparedUrls.size());
        assertEquals(700L, (long) port.preparedPositions.get(0));
        assertFalse(port.preparedPlay.get(0));
        assertTrue(log.has("ui.gyroBase"));
    }

    @Test
    public void automaticRestoresTheInferredFormatAndKeepsTheEngine() {
        Log log = new Log();
        FakePort port = new FakePort(log);
        FakePlayback playback = new FakePlayback(log);
        playback.restoreResult = true;
        Projection saved = new Projection();
        saved.kind = Projection.FISHEYE;
        saved.capture = 200;
        saved.layout = Projection.SBS;
        saved.eye = 1;
        saved.yaw = 1.5f;
        saved.pitch = .25f;
        saved.viewFov = 90;
        playback.restoreProjection = saved;
        Recorder ui = new Recorder(log);
        PlaybackController c = controller(port, playback, new FakeFavorites(), new FakeMedia(log), ui, Runnable::run);
        Detail loaded = detail("https://s.test/deovr/file/1");
        loaded.metadata = "180_sbs";
        loaded.stereo = "sbs";
        c.state().entryKey = ENTRY;
        c.state().detail = loaded;
        c.state().fileKey = FILE_1;
        c.state().loaded = true;

        c.select(0, true);

        assertTrue(c.state().manual);
        assertEquals(Projection.FISHEYE, c.state().projection.kind);

        c.automatic();

        assertFalse(c.state().manual);
        assertEquals(Projection.EQUIRECT, c.state().projection.kind);
        assertEquals(180, c.state().projection.capture);
        assertEquals(Projection.SBS, c.state().projection.layout);
        assertEquals(1, c.state().projection.eye);
        assertEquals(1.5f, c.state().projection.yaw, 0f);
        assertEquals(.25f, c.state().projection.pitch, 0f);
        assertEquals(90f, c.state().projection.viewFov, 0f);
        assertFalse(log.has("port.prepare"));
        assertTrue(log.has("playback.save:" + FILE_1));
    }

    @Test
    public void manualAndViewChangesNeverRebuildTheEngine() {
        Log log = new Log();
        FakePort port = new FakePort(log);
        FakePlayback playback = new FakePlayback(log);
        Recorder ui = new Recorder(log);
        PlaybackController c = controller(port, playback, new FakeFavorites(), new FakeMedia(log), ui, Runnable::run);
        c.state().entryKey = ENTRY;
        c.state().detail = detail("https://s.test/deovr/file/1");
        c.state().fileKey = FILE_1;
        c.state().source = source("https://s.test/deovr/file/1");
        c.state().loaded = true;
        c.state().active = true;

        Projection next = new Projection();
        next.kind = Projection.FISHEYE;
        next.capture = 190;
        next.known = true;
        c.manual(next);
        c.eye(1);
        c.packing(true);
        c.lens(.4f, .6f, 1.2f, 10, true);
        c.resetView();

        assertFalse(log.has("port.prepare"));
        assertTrue(c.state().manual);
        assertEquals(0f, c.state().projection.yaw, 0f);
        assertEquals(75f, c.state().projection.viewFov, 0f);
        assertEquals(1, c.state().projection.eye);
    }

    @Test
    public void saveWritesTheFileViewAndTheScenePosition() {
        Log log = new Log();
        FakePort port = new FakePort(log);
        port.position = 900;
        FakePlayback playback = new FakePlayback(log);
        Recorder ui = new Recorder(log);
        PlaybackController c = controller(port, playback, new FakeFavorites(), new FakeMedia(log), ui, Runnable::run);
        c.state().entryKey = ENTRY;
        c.state().fileKey = FILE_1;
        c.state().loaded = true;

        c.save();

        assertEquals(1, playback.savedKeys.size());
        assertEquals("playback.save:" + FILE_1 + "@900", log.events.get(log.first("playback.save:")));
        assertTrue(log.has("playback.entryPosition:" + ENTRY + "@900"));
    }

    @Test
    public void theTickerSavesEveryFifthTickAndLeavesTheDraggedPositionAlone() {
        Log log = new Log();
        FakePort port = new FakePort(log);
        port.duration = 10000;
        port.position = 2000;
        FakePlayback playback = new FakePlayback(log);
        Recorder ui = new Recorder(log);
        ui.seeking = true;
        PlaybackController c = controller(port, playback, new FakeFavorites(), new FakeMedia(log), ui, Runnable::run);
        c.state().entryKey = ENTRY;
        c.state().fileKey = FILE_1;
        c.state().loaded = true;

        for (int i = 0; i < 4; i++) c.tick();
        assertEquals(0, playback.savedKeys.size());
        assertFalse(log.has("ui.time"));
        assertFalse(log.has("ui.progress"));
        assertTrue(log.has("ui.playButton"));

        c.tick();
        assertEquals(1, playback.savedKeys.size());

        ui.seeking = false;
        log.events.clear();
        c.tick();
        assertTrue(log.has("ui.time"));
        assertTrue(log.has("ui.progress"));
        assertFalse(log.has("playback.save:"));
    }

    @Test
    public void noDetailDoesNotSelectOrPrepare() {
        Log log = new Log();
        FakePort port = new FakePort(log);
        Recorder ui = new Recorder(log);
        PlaybackController c = controller(port, new FakePlayback(log), new FakeFavorites(), new FakeMedia(log), ui, Runnable::run);
        c.state().active = true;

        c.select(0, true);
        c.automatic();

        assertFalse(log.has("port.prepare"));
        assertFalse(log.has("ui.title"));
        assertNull(c.state().source);
    }

    @Test
    public void noSourceFailsTheSelectionWithoutPreparing() {
        Log log = new Log();
        FakePort port = new FakePort(log);
        FakeMedia media = new FakeMedia(log);
        media.result = detail();
        Recorder ui = new Recorder(log);
        PlaybackController c = controller(port, new FakePlayback(log), new FakeFavorites(), media, ui, Runnable::run);
        c.state().active = true;

        c.open(ENTRY, "https://s.test/deovr/scene/1");

        assertTrue(log.has("ui.playbackFailed:media.select"));
        assertFalse(log.has("port.prepare"));
        assertFalse(c.state().loaded);
    }

    @Test
    public void aDetailLoadFailureIsReportedAndDestroyDropsLateResults() {
        Log log = new Log();
        FakePort port = new FakePort(log);
        FakeMedia media = new FakeMedia(log);
        media.detailFailure = new IOException("down");
        Recorder ui = new Recorder(log);
        Queue io = new Queue();
        PlaybackController c = controller(port, new FakePlayback(log), new FakeFavorites(), media, ui, io);
        c.open(ENTRY, "https://s.test/deovr/scene/1");
        io.run();

        assertTrue(log.has("ui.loadFailed"));
        assertFalse(log.has("ui.title"));
        assertFalse(log.has("port.prepare"));

        log.events.clear();
        media.detailFailure = null;
        media.result = detail("https://s.test/deovr/file/1");
        c.open(ENTRY, "https://s.test/deovr/scene/1");
        c.destroy();
        io.run();

        assertFalse(log.has("ui.loadFailed"));
        assertFalse(log.has("ui.title"));
        assertTrue(media.log.has("media.detail"));
    }

    @Test
    public void serverFavoriteWithoutPermissionMakesNoRequest() {
        Log log = new Log();
        FakeMedia media = new FakeMedia(log);
        Recorder ui = new Recorder(log);
        PlaybackController c = controller(new FakePort(log), new FakePlayback(log), new FakeFavorites(), media, ui, Runnable::run);
        Detail loaded = detail("https://s.test/deovr/file/1");
        loaded.writeFavorite = false;
        c.state().detail = loaded;

        c.serverFavorite(true);

        assertFalse(c.serverFavoriteAvailable());
        assertFalse(log.has("media.favorite"));
        assertFalse(log.has("ui.serverConfirmed"));
        assertFalse(log.has("ui.serverFailed"));
    }

    @Test
    public void serverFavoriteWithPermissionConfirmsOnlyAfterTheWriteReturns() {
        Log log = new Log();
        FakeMedia media = new FakeMedia(log);
        Recorder ui = new Recorder(log);
        Queue io = new Queue();
        PlaybackController c = controller(new FakePort(log), new FakePlayback(log), new FakeFavorites(), media, ui, io);
        Detail loaded = detail("https://s.test/deovr/file/1");
        loaded.writeFavorite = true;
        c.state().detail = loaded;

        c.serverFavorite(true);
        assertTrue(c.serverFavoriteAvailable());
        assertFalse(log.has("ui.serverConfirmed"));

        io.run();

        assertTrue(log.has("media.favorite:true"));
        assertTrue(log.has("ui.serverConfirmed"));
        assertTrue(log.first("media.favorite:true") < log.first("ui.serverConfirmed"));

        log.events.clear();
        media.favoriteFailure = new IOException("rejected");
        c.serverFavorite(true);
        io.run();

        assertTrue(log.has("ui.serverFailed"));
        assertFalse(log.has("ui.serverConfirmed"));
    }

    @Test
    public void localFavoriteTogglesTheSceneKeyAndNotifiesThePage() {
        Log log = new Log();
        FakeFavorites favorites = new FakeFavorites();
        Recorder ui = new Recorder(log);
        PlaybackController c = controller(new FakePort(log), new FakePlayback(log), favorites, new FakeMedia(log), ui, Runnable::run);
        c.state().entryKey = ENTRY;

        c.toggleLocalFavorite();

        assertTrue(favorites.favorite(ENTRY));
        assertTrue(log.has("ui.localFavorite"));

        c.toggleLocalFavorite();
        assertFalse(favorites.favorite(ENTRY));
        assertEquals(2, log.count("ui.localFavorite"));
    }
}
