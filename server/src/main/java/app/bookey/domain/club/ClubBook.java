package app.bookey.domain.club;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/** 모임 선정 도서. MVP 는 1권(seq=1), 시즌제 확장 대비. */
@Getter
@Entity
@Table(name = "club_books")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClubBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(nullable = false)
    private short seq = 1;

    @Column(name = "target_finish_date")
    private LocalDate targetFinishDate;

    @Column(name = "total_pages_snapshot")
    private Integer totalPagesSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Builder
    private ClubBook(Long clubId, Long bookId, short seq, LocalDate targetFinishDate,
                     Integer totalPagesSnapshot) {
        this.clubId = clubId;
        this.bookId = bookId;
        this.seq = seq == 0 ? 1 : seq;
        this.targetFinishDate = targetFinishDate;
        this.totalPagesSnapshot = totalPagesSnapshot;
    }

    public void updateTotalPages(Integer totalPages) {
        this.totalPagesSnapshot = totalPages;
    }

    public void updateTargetDate(LocalDate targetFinishDate) {
        this.targetFinishDate = targetFinishDate;
    }
}
