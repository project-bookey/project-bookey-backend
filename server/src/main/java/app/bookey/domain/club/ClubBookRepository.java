package app.bookey.domain.club;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClubBookRepository extends JpaRepository<ClubBook, Long> {

    List<ClubBook> findAllByClubIdOrderBySeqAsc(Long clubId);

    Optional<ClubBook> findFirstByClubIdOrderBySeqAsc(Long clubId);

    List<ClubBook> findAllByClubIdIn(List<Long> clubIds);

    Optional<ClubBook> findFirstByClubIdAndBookId(Long clubId, Long bookId);

    /** 다음 seq 를 매기려고 — 가장 나중에 붙은 책. */
    Optional<ClubBook> findFirstByClubIdOrderBySeqDesc(Long clubId);
}
