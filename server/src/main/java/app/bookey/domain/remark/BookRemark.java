package app.bookey.domain.remark;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 한 마디 — 책을 덮으며 남기는 한 줄. 읽기 기록(회차)마다 하나. */
@Getter
@Entity
@Table(name = "book_remarks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookRemark extends BaseTimeEntity {

    /** 한 마디 길이 상한 — 도서 상세에서 두세 줄 안에 읽히는 한 문장. */
    public static final int MAX_LENGTH = 60;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "reading_record_id", nullable = false, unique = true)
    private Long readingRecordId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RemarkKind kind;

    @Column(nullable = false, length = MAX_LENGTH)
    private String body;

    @Column(name = "written_at", nullable = false)
    private Instant writtenAt;

    @Builder
    private BookRemark(Long userId, Long bookId, Long readingRecordId, RemarkKind kind, String body, Instant writtenAt) {
        this.userId = userId;
        this.bookId = bookId;
        this.readingRecordId = readingRecordId;
        this.kind = kind;
        this.body = body.strip();
        this.writtenAt = writtenAt;
    }

    /** 다시 쓰기 — 그때의 상태로 바뀌고, 최신순 맨 앞으로 온다. */
    public void rewrite(RemarkKind kind, String body, Instant at) {
        this.kind = kind;
        this.body = body.strip();
        this.writtenAt = at;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }
}
