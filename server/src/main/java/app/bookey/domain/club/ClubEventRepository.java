package app.bookey.domain.club;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClubEventRepository extends JpaRepository<ClubEvent, Long> {

    Page<ClubEvent> findAllByClubIdOrderByCreatedAtDesc(Long clubId, Pageable pageable);
}
