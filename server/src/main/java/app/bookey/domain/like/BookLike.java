package app.bookey.domain.like;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 도서 좋아요 — 서재와 무관한 가벼운 반응. user_id+book_id 당 1건. */
@Getter
@Entity
@Table(name = "book_likes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookLike extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Builder
    private BookLike(Long userId, Long bookId) {
        this.userId = userId;
        this.bookId = bookId;
    }
}
