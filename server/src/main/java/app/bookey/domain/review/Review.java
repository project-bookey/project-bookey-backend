package app.bookey.domain.review;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Getter
@Entity
@Table(name = "reviews")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "reading_record_id")
    private Long readingRecordId;

    /** 완독 시에만 부여 가능 (§F6 리뷰 구성). */
    private Short rating;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] tags = new String[0];

    @Column(name = "has_spoiler", nullable = false)
    private boolean hasSpoiler;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_level", nullable = false, length = 20)
    private VerificationLevel verificationLevel = VerificationLevel.UNVERIFIED;

    /** 등급 산정 근거. 리뷰 작성 시점에 고정되는 불변 기록(§8.2). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "verification_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> verificationSnapshot;

    @Column(name = "helpful_count", nullable = false)
    private int helpfulCount;

    @Column(name = "report_count", nullable = false)
    private int reportCount;

    @Column(nullable = false, length = 10)
    private String status = "VISIBLE";

    @Builder
    private Review(Long userId, Long bookId, Long readingRecordId, Short rating, String body,
                   String[] tags, boolean hasSpoiler, VerificationLevel verificationLevel,
                   Map<String, Object> verificationSnapshot) {
        this.userId = userId;
        this.bookId = bookId;
        this.readingRecordId = readingRecordId;
        this.rating = rating;
        this.body = body;
        this.tags = tags == null ? new String[0] : tags;
        this.hasSpoiler = hasSpoiler;
        this.verificationLevel = verificationLevel == null ? VerificationLevel.UNVERIFIED : verificationLevel;
        this.verificationSnapshot = verificationSnapshot;
        this.status = "VISIBLE";
    }

    /** 본문 수정은 허용하되 검증 등급은 재산정하지 않는다(§8.2). */
    public void edit(String body, Short rating, String[] tags, Boolean hasSpoiler) {
        if (body != null && !body.isBlank()) {
            this.body = body;
        }
        if (rating != null) {
            this.rating = rating;
        }
        if (tags != null) {
            this.tags = tags;
        }
        if (hasSpoiler != null) {
            this.hasSpoiler = hasSpoiler;
        }
    }

    /** 관리자 재심사 (§F13 검증 심사). */
    public void overrideVerification(VerificationLevel level) {
        this.verificationLevel = level;
    }

    public void addHelpful() {
        this.helpfulCount++;
    }

    public void removeHelpful() {
        if (this.helpfulCount > 0) {
            this.helpfulCount--;
        }
    }

    public void addReport() {
        this.reportCount++;
    }

    public void hide() {
        this.status = "HIDDEN";
    }

    public void restore() {
        this.status = "VISIBLE";
    }

    public void softDelete() {
        this.status = "DELETED";
    }

    public boolean isVisible() {
        return "VISIBLE".equals(status);
    }
}
