package app.bookey.domain.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "user_devices")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DevicePlatform platform;

    @Column(name = "push_token", nullable = false)
    private String pushToken;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = true;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public UserDevice(Long userId, DevicePlatform platform, String pushToken) {
        this.userId = userId;
        this.platform = platform;
        this.pushToken = pushToken;
        this.pushEnabled = true;
        this.lastSeenAt = Instant.now();
    }

    public void touch(Long userId, boolean pushEnabled) {
        this.userId = userId;
        this.pushEnabled = pushEnabled;
        this.lastSeenAt = Instant.now();
    }
}
