package app.bookey.domain.post;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** 독후감 (§F7). 기본 공개 범위는 비공개. */
@Getter
@Entity
@Table(name = "posts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "book_id")
    private Long bookId;

    @Column(name = "reading_record_id")
    private Long readingRecordId;

    @Column(nullable = false, length = 120)
    private String slug;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "body_md", nullable = false, columnDefinition = "text")
    private String bodyMd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PostVisibility visibility = PostVisibility.PRIVATE;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] tags = new String[0];

    @Column(name = "series_id")
    private Long seriesId;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Builder
    private Post(Long userId, Long bookId, Long readingRecordId, String slug, String title,
                 String bodyMd, PostVisibility visibility, String[] tags) {
        this.userId = userId;
        this.bookId = bookId;
        this.readingRecordId = readingRecordId;
        this.slug = slug;
        this.title = title;
        this.bodyMd = bodyMd;
        this.visibility = visibility == null ? PostVisibility.PRIVATE : visibility;
        this.tags = tags == null ? new String[0] : tags;
        if (this.visibility != PostVisibility.PRIVATE) {
            this.publishedAt = Instant.now();
        }
    }

    public void edit(String title, String bodyMd, String[] tags) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
        if (bodyMd != null) {
            this.bodyMd = bodyMd;
        }
        if (tags != null) {
            this.tags = tags;
        }
    }

    public void changeVisibility(PostVisibility visibility) {
        this.visibility = visibility;
        if (visibility != PostVisibility.PRIVATE && publishedAt == null) {
            this.publishedAt = Instant.now();
        }
    }

    public void increaseView() {
        this.viewCount++;
    }

    /**
     * 책을 바꾼다 — null 은 무시한다(연결한 책을 지울 수는 없다).
     * 책이 실제로 바뀌면 독서 기록 연결은 끊는다 — 이전 책의 기록이 새 책에 남으면 안 된다.
     */
    public void changeBook(Long bookId) {
        if (bookId == null || bookId.equals(this.bookId)) {
            return;
        }
        this.bookId = bookId;
        this.readingRecordId = null;
    }

    public boolean hasBook() {
        return bookId != null;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    /** 비공개는 작성자만, 공개·링크 공개는 누구나(비로그인 포함) 읽을 수 있다. */
    public boolean isReadableBy(Long viewerId) {
        return isOwnedBy(viewerId) || visibility != PostVisibility.PRIVATE;
    }
}
