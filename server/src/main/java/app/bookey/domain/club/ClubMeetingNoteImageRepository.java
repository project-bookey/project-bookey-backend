package app.bookey.domain.club;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface ClubMeetingNoteImageRepository extends JpaRepository<ClubMeetingNoteImage, Long> {

    List<ClubMeetingNoteImage> findAllByNoteId(Long noteId);

    /** 어느 노트에도 붙지 않은 채 남은 사진 — 정리 배치용. detach 때 updated_at 이 갱신되므로 떼어진 시점부터 센다. */
    List<ClubMeetingNoteImage> findAllByNoteIdIsNullAndUpdatedAtBefore(Instant before);

    /** 아직 어디에도 붙지 않았을 때만 행을 지운다 — 조회와 삭제 사이에 노트가 그 사진을 참조한 경합을 막는다. */
    @Transactional
    @Modifying
    @Query("DELETE FROM ClubMeetingNoteImage i WHERE i.id = :id AND i.noteId IS NULL")
    int deleteIfDetached(@Param("id") Long id);
}
