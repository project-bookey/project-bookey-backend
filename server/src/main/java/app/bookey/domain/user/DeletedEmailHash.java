package app.bookey.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "deleted_email_hashes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeletedEmailHash {
    @Id
    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DeletedEmailHash(String emailHash, Instant createdAt) {
        this.emailHash = emailHash;
        this.createdAt = createdAt;
    }
}
