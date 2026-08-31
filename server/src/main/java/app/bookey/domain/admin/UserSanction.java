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
        return releasedAt == null && (endsAt == null || endsAt.isAfter(Instant.now()));
    }
}
