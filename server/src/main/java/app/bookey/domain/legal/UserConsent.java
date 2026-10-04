package app.bookey.domain.legal;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 동의 이력 한 줄 — 동의·철회할 때마다 새 행을 쌓고 고치지 않는다. 지금 상태는 종류별 마지막 행이다.
 * 탈퇴해도 지우지 않는다(사용자 행이 익명화만 되므로 남는다) — 동의를 받았다는 입증 자료다.
 */
@Getter
@Entity
@Table(name = "user_consents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConsentKind kind;

    /** 동의할 때 보여 준 문서 버전 — 문서 없는 종류(AGE_14)와 철회 행은 null. */
    @Column(length = 20)
    private String version;

    @Column(nullable = false)
    private boolean agreed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UserConsent(Long userId, ConsentKind kind, String version, boolean agreed, Instant createdAt) {
        this.userId = userId;
        this.kind = kind;
        this.version = version;
        this.agreed = agreed;
        this.createdAt = createdAt;
    }
}
