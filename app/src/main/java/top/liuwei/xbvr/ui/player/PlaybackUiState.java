package top.liuwei.xbvr.ui.player;

import top.liuwei.xbvr.domain.Models.Detail;
import top.liuwei.xbvr.domain.Models.Source;
import top.liuwei.xbvr.domain.Projection;

/**
 * Mutable state of the player page. Pure Java: it holds no Surface, Sensor or Media3 object; the
 * surface, the sensors, the dialog construction and the {@code R.string} mapping stay in the
 * activity. {@code fileKey} is the selected source's playback key, {@code entryKey} the scene key.
 */
public final class PlaybackUiState {
    public Detail detail;
    public Source source;
    public int selected;
    public Projection projection = new Projection();
    public boolean manual;
    public String entryKey;
    public String fileKey;
    public long savedPosition;
    public boolean wasPlaying = true;
    public boolean active;
    public boolean loaded;
    public boolean hdr;
    public boolean rendererFailed;
    public String failurePhase;
    public Throwable failure;
}
