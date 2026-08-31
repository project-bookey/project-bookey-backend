package app.bookey.domain.book;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 페이지 수 크라우드 입력. 다수결로 books.total_pages 를 채운다(§12 리스크). */
@Getter
@Entity
@Table(name = "book_page_suggestions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookPageSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_pages", nullable = false)
    private int totalPages;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public BookPageSuggestion(Long bookId, Long userId, int totalPages) {
        this.bookId = bookId;
        this.userId = userId;
        this.totalPages = totalPages;
    }

    public void update(int totalPages) {
        this.totalPages = totalPages;
    }
}
