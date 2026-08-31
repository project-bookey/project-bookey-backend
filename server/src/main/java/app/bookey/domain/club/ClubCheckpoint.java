package app.bookey.domain.club;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 주차별 목표 (§12.2). */
@Getter
@Entity
@Table(name = "club_checkpoints")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClubCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_book_id", nullable = false)
    private Long clubBookId;

    @Column(nullable = false)
    private short seq;

    @Column(nullable = false, length = 60)
    private String title;

    @Column(name = "target_page", nullable = false)
    private int targetPage;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Builder
    private ClubCheckpoint(Long clubBookId, short seq, String title, int targetPage, Instant dueAt) {
        this.clubBookId = clubBookId;
        this.seq = seq;
        this.title = title;
        this.targetPage = targetPage;
        this.dueAt = dueAt;
    }

    public void markEvaluated() {
        this.evaluatedAt = Instant.now();
    }

    public boolean isDue(Instant now) {
        return evaluatedAt == null && dueAt.isBefore(now);
    }

    public void update(String title, Integer targetPage, Instant dueAt) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
        if (targetPage != null && targetPage > 0) {
            this.targetPage = targetPage;
        }
        if (dueAt != null) {
            this.dueAt = dueAt;
        }
    }
}
