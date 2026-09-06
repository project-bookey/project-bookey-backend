package app.bookey.domain.social;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserPublicIdRepository extends JpaRepository<UserPublicId, Long> {

    Optional<UserPublicId> findByUserId(Long userId);

    Optional<UserPublicId> findByCode(String code);

    boolean existsByCode(String code);
}
