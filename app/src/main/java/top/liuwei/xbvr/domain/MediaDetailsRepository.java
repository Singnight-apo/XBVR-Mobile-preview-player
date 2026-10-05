package top.liuwei.xbvr.domain;
import top.liuwei.xbvr.domain.Models.Detail;
public interface MediaDetailsRepository {
    Detail detail(String url) throws Exception;
    void favorite(Detail detail, boolean value) throws Exception;
}
