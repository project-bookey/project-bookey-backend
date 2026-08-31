package app.bookey.domain.club;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClubRepository extends JpaRepository<Club, Long> {

    Optional<Club> findByJoinCode(String joinCode);

    boolean existsByJoinCode(String joinCode);

    @Query("""
            SELECT c FROM Club c
            WHERE c.visibility = 'PUBLIC' AND c.status IN ('RECRUITING', 'ACTIVE')
            ORDER BY c.createdAt DESC
            """)
    Page<Club> findPublicClubs(Pageable pageable);

    /** 기간이 끝났는데 아직 종료 처리되지 않은 모임 — 배치 대상. */
    @Query("SELECT c FROM Club c WHERE c.status IN ('RECRUITING','ACTIVE') AND c.endsAt < :today")
    List<Club> findExpired(@Param("today") LocalDate today);

    @Query("""
            SELECT c FROM Club c
            WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', COALESCE(:keyword, ''), '%'))
            ORDER BY c.createdAt DESC
            """)
    Page<Club> searchForAdmin(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            SELECT c FROM Club c
            WHERE c.status = :status
              AND LOWER(c.name) LIKE LOWER(CONCAT('%', COALESCE(:keyword, ''), '%'))
            ORDER BY c.createdAt DESC
            """)
    Page<Club> searchForAdminByStatus(@Param("keyword") String keyword,
                                      @Param("status") ClubStatus status,
                                      Pageable pageable);
}
