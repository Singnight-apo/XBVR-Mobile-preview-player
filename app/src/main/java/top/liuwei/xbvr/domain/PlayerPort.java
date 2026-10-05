package top.liuwei.xbvr.domain;
import java.util.List;
import top.liuwei.xbvr.domain.Models.Source;
import top.liuwei.xbvr.domain.Models.Subtitle;
public interface PlayerPort {
    void prepare(Source source, List<Subtitle> subtitles, long position, boolean play);
    boolean hasEngine();
    long position();
    long duration();
    boolean playWhenReady();
    boolean isPlaying();
    boolean ended();
    void play();
    void pause();
    void seekTo(long position);
    void speed(float value);
    List<TrackOption> tracks();
    void audioAuto();
    void subtitlesOff();
    void selectTrack(String token);
    void stop();
}
