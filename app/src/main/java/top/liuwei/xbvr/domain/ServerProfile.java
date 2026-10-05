package top.liuwei.xbvr.domain;

/** Immutable server configuration. Deliberately has no toString that includes account or password. */
public final class ServerProfile {
    public final String id, base, user, password, basicUser, basicPassword;

    public ServerProfile(
            String id, String base, String user, String password, String basicUser, String basicPassword) {
        this.id = id;
        this.base = base;
        this.user = user;
        this.password = password;
        this.basicUser = basicUser;
        this.basicPassword = basicPassword;
    }
}
