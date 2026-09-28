package app.bookey.domain.club;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface ClubNoteImageRepository extends JpaRepository<ClubNoteImage, Long> {

    List<ClubNoteImage> findAllByPageId(Long pageId);

    /**
     * 어느 페이지에도 붙지 않은 채 남은 사진 — 정리 배치용.
     * 문서에서 뺀 사진은 detach 때 updated_at 이 갱신되므로, 떼어진 시점부터 24시간을 센다(created_at 이 아니라).
     */
    List<ClubNoteImage> findAllByPageIdIsNullAndUpdatedAtBefore(Instant before);

    /**
     * 페이지를 지울 때 사진 연결을 모두 끊는다(사진 자체는 남긴다 — 정리 배치가 24시간 뒤 회수).
     * 벌크 UPDATE 는 @LastModifiedDate 를 타지 않으므로 감사 시각을 직접 맞춘다.
     * flushAutomatically 로 앞선 변경을 먼저 내보내고, clearAutomatically 는 쓰지 않는다
     * — 영속성 컨텍스트를 비우면 바로 뒤의 페이지 삭제가 준영속 엔티티를 지우려다 실패한다(PostImageRepository 와 같은 이유).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE ClubNoteImage i SET i.pageId = null, i.updatedAt = CURRENT_TIMESTAMP WHERE i.pageId = :pageId")
    int detachAllByPageId(@Param("pageId") Long pageId);

    /**
     * 아직 어디에도 붙지 않았을 때만 행을 지운다 — 지운 행 수(0 또는 1).
     * 정리 배치가 고아를 조회한 뒤 지우기 전에 문서가 그 사진을 참조할 수 있으므로,
     * page_id IS NULL 을 삭제 조건에 함께 넣어 그 경합에서 방금 붙은 사진을 지우지 않게 한다.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM ClubNoteImage i WHERE i.id = :id AND i.pageId IS NULL")
    int deleteIfDetached(@Param("id") Long id);
}
