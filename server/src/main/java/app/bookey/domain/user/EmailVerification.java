package app.bookey.domain.user;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 이메일 인증 코드(가입·비밀번호 재설정). 코드 원문은 저장하지 않고 SHA-256 해시만 보관한다. */
@Getter
@Entity
@Table(name = "email_verifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailCodePurpose purpose;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "attempt_count", nullable = false)
    private short attemptCount;

    public EmailVerification(String email, EmailCodePurpose purpose, String codeHash, Instant expiresAt) {
        this.email = email;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean hasAttemptsLeft(int maxAttempts) {
        return attemptCount < maxAttempts;
    }

    /** 검증 실패 1회 누적 — 상한 초과 시 코드는 무효로 취급한다(무작위 대입 방어). */
    public void recordFailedAttempt() {
        attemptCount++;
    }

    public void consume(Instant now) {
        this.consumedAt = now;
    }
}
