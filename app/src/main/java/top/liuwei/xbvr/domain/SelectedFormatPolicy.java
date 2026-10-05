package top.liuwei.xbvr.domain;

import top.liuwei.xbvr.domain.Models.Detail;
import top.liuwei.xbvr.domain.Models.Source;

/** Format inference for the currently selected media file. */
public final class SelectedFormatPolicy {
    private SelectedFormatPolicy() {}

    public static Projection infer(Detail detail, Source source) {
        boolean single = detail.sources.size() == 1;
        String metadata = source.projection.isBlank() ? (single ? detail.metadata : "") : source.projection;
        String stereo = source.stereo.isBlank() ? (single ? detail.stereo : "") : source.stereo;
        return FormatInference.infer(metadata, source.filename.isBlank() ? detail.title : source.filename,
                stereo, source.fov > 0 ? source.fov : single ? detail.fov : 0);
    }
}
