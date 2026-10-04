package app.bookey.domain.legal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    /** 이력 전체(오래된 순) — 종류별 마지막 행이 지금 상태다. */
    List<UserConsent> findAllByUserIdOrderByIdAsc(Long userId);

    Optional<UserConsent> findTopByUserIdAndKindOrderByIdDesc(Long userId, ConsentKind kind);
}
