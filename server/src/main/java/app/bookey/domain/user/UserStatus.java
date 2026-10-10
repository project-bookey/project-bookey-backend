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

    /** 제재 무게 — 숫자가 클수록 무겁다. 여러 제재가 겹치면 가장 무거운 상태가 남는다. */
    public int severity() {
        return switch (this) {
            case ACTIVE -> 0;
            case WRITE_BANNED -> 1;
            case SUSPENDED -> 2;
            case TERMINATED -> 3;
        };
    }
}
