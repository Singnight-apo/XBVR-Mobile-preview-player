package top.liuwei.xbvr.domain;

import java.util.List;

/** Persisted server configurations, including which one is active. */
public interface ProfileRepository {
    List<ServerProfile> all() throws Exception;

    ServerProfile current() throws Exception;

    ServerProfile find(String id) throws Exception;

    void save(ServerProfile value) throws Exception;

    void remove(String id) throws Exception;

    void select(String id);
}
