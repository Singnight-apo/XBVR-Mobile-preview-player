package top.liuwei.xbvr.data;

import top.liuwei.xbvr.domain.MediaDetailsRepository;
import top.liuwei.xbvr.domain.Models.Detail;

/**
 * Thin adapter over the existing {@link XbvrApi} calls. Authorization, redirect handling, the
 * favourite-write permission check and the server confirmation re-read all stay inside XbvrApi;
 * this class only exposes them on the domain interface so the page coordinator never sees the
 * client. T22 replaces the page-side construction with the composition root.
 */
public final class DefaultMediaDetailsRepository implements MediaDetailsRepository {
    private final XbvrApi api;

    public DefaultMediaDetailsRepository(XbvrApi api) {
        this.api = api;
    }

    @Override
    public Detail detail(String url) throws Exception {
        return api.detail(url);
    }

    @Override
    public void favorite(Detail detail, boolean value) throws Exception {
        api.favorite(detail, value);
    }
}
