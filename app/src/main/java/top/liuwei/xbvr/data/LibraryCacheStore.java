package top.liuwei.xbvr.data;

/** Read/write seam for the directory cache so the loader can be tested without a Context. */
public interface LibraryCacheStore {
    String read(String id);

    void write(String id, String json) throws Exception;
}
