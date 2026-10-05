package top.liuwei.xbvr;

import org.json.JSONObject;
import top.liuwei.xbvr.data.ProfileJsonMapper;
import top.liuwei.xbvr.data.XbvrApi;

/**
 * Temporary root facade over the data-layer client so the Activities keep their call sites until
 * T22 composes through AppServices. New code must use {@link XbvrApi}.
 */
@Deprecated
public final class Api extends XbvrApi {
    public Api(JSONObject profile) {
        super(ProfileJsonMapper.from(profile));
    }
}
