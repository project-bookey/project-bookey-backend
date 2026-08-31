package app.bookey.domain.club;

/** 모임 역할 (§12.1). */
public enum ClubRole {
    HOST,
    MODERATOR,
    MEMBER;

    public boolean canManageClub() {
        return this == HOST;
    }

    public boolean canModerate() {
        return this == HOST || this == MODERATOR;
    }
}
