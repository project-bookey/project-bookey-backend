package app.bookey.domain.admin;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "user_sanctions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSanction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private SanctionType type;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt = Instant.now();

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public UserSanction(Long userId, Long adminId, SanctionType type, String reason, Instant endsAt) {
        this.userId = userId;
        this.adminId = adminId;
        this.type = type;
        this.reason = reason;
        this.startsAt = Instant.now();
        this.endsAt = endsAt;
    }

    public void release() {
        this.releasedAt = Instant.now();
    }

    public boolean isActive() {
        return isActiveAt(Instant.now());
    }

    /** 해제되지 않았고 기간이 남았으면 살아 있다. 기간이 없는(null) 제재는 해제할 때까지 이어진다. */
    public boolean isActiveAt(Instant now) {
        return releasedAt == null && (endsAt == null || endsAt.isAfter(now));
    }

    public boolean isReleased() {
        return releasedAt != null;
    }

    public boolean belongsTo(Long userId) {
        return this.userId.equals(userId);
    }
}
