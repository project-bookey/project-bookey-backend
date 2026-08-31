package app.bookey.domain.post;

public enum PostVisibility {
    PUBLIC,
    LINK,
    PRIVATE;

    public boolean isReadableByAnonymous() {
        return this == PUBLIC;
    }
}
