package app.bookey.domain.user;

public enum UserStatus {
    ACTIVE,
    WRITE_BANNED,
    SUSPENDED,
    TERMINATED;

    public boolean canWrite() {
        return this == ACTIVE;
    }

    public boolean canLogin() {
        return this == ACTIVE || this == WRITE_BANNED;
    }
}
