package app.bookey.domain.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "user_identities")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_uid", nullable = false, length = 255)
    private String providerUid;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public UserIdentity(Long userId, AuthProvider provider, String providerUid) {
        this.userId = userId;
        this.provider = provider;
        this.providerUid = providerUid;
    }
}
