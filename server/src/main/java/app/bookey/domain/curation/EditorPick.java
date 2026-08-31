package app.bookey.domain.curation;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 에디터 픽 — 운영자가 고른 추천 도서. book_id 당 1건. */
@Getter
@Entity
@Table(name = "editor_picks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EditorPick extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(length = 200)
    private String note;

    @Builder
    private EditorPick(Long bookId, int sortOrder, String note) {
        this.bookId = bookId;
        this.sortOrder = sortOrder;
        this.note = note;
    }

    public void update(int sortOrder, String note) {
        this.sortOrder = sortOrder;
        this.note = note;
    }
}
