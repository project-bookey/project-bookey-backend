package app.bookey.domain.curation;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/** 에디터 픽 — 운영자가 고른 추천 도서. book_id 당 1건. */
@Entity
@Table(name = "editor_picks")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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

    public void update(int sortOrder, String note) {
        this.sortOrder = sortOrder;
        this.note = note;
    }
}
