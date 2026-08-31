package app.bookey.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.userId = :userId AND t.revokedAt IS NULL")
    void revokeAllByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
