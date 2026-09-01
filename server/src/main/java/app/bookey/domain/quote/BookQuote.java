package app.bookey.domain.quote;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 오려둔 문장(밑줄) — 책의 한 구절을 인용해 남긴다. */
@Getter
@Entity
@Table(name = "book_quotes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookQuote extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "reading_record_id")
    private Long readingRecordId;

    @Column(nullable = false, length = 500)
    private String content;

    private Integer page;

    @Builder
    private BookQuote(Long userId, Long bookId, Long readingRecordId, String content, Integer page) {
        this.userId = userId;
        this.bookId = bookId;
        this.readingRecordId = readingRecordId;
        this.content = content;
        this.page = page;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }
}
