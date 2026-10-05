package top.liuwei.xbvr.domain;

/** Loads the library in the cached-directory-metadata order the UI has always displayed. */
public interface LibraryRepository {
    interface Request {
        void cancel();
    }

    interface Observer {
        void event(LibraryEvent event);
    }

    Request load(boolean useCache, Observer observer);
}
