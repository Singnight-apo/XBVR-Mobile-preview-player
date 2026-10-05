package top.liuwei.xbvr.domain;

/**
 * One selectable audio or text track of the current playback session. The token is only valid for
 * the tracks snapshot that produced it; the media implementation owns the token-to-track mapping
 * and no TrackGroup ever reaches Domain.
 */
public final class TrackOption {
    public enum Kind {
        AUDIO,
        TEXT
    }

    public final String token;
    public final Kind kind;
    public final String language;
    public final String mimeType;
    public final boolean supported;
    public final boolean selected;

    public TrackOption(
            String token,
            Kind kind,
            String language,
            String mimeType,
            boolean supported,
            boolean selected) {
        this.token = token;
        this.kind = kind;
        this.language = language;
        this.mimeType = mimeType;
        this.supported = supported;
        this.selected = selected;
    }
}
