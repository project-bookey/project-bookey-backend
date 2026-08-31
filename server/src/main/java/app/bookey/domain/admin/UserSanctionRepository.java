package app.bookey.domain.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserSanctionRepository extends JpaRepository<UserSanction, Long> {

    List<UserSanction> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    List<UserSanction> findAllByUserIdAndReleasedAtIsNull(Long userId);
}
