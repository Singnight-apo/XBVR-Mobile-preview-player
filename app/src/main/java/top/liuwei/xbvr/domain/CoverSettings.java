package top.liuwei.xbvr.domain;

/** Per-server poster-wall cover settings. */
public interface CoverSettings {
    int mode(String profileId);

    void mode(String profileId, int mode);

    float inferredRatio(String profileId);

    void inferredRatio(String profileId, float ratio);

    void clearInferredRatio(String profileId);
}
