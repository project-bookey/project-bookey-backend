package app.bookey.domain.club;

public enum ClubStatus {
    RECRUITING,
    ACTIVE,
    ENDED,
    ARCHIVED;

    public boolean isJoinable() {
        return this == RECRUITING || this == ACTIVE;
    }

    public boolean isOver() {
        return this == ENDED || this == ARCHIVED;
    }
}
