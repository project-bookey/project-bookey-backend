package app.bookey.domain.post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    /** 목록 배치 로딩용 — 독후감별 사진을 순서대로 한 번에 읽는다. */
    List<PostImage> findAllByPostIdInOrderBySortOrderAscIdAsc(Collection<Long> postIds);

    /** 독후감에 붙지 못하고 남은 임시 업로드 — 정리 배치용. */
    List<PostImage> findAllByPostIdIsNullAndCreatedAtBefore(Instant before);

    /**
     * 독후감의 사진 연결을 모두 끊는다(사진 자체는 남긴다).
     * 벌크 UPDATE 는 @LastModifiedDate 를 타지 않으므로 감사 시각을 직접 맞춘다(정리 배치는 created_at 기준).
     * flushAutomatically 로 앞선 변경을 먼저 내보내고, clearAutomatically 는 쓰지 않는다
     * — 영속성 컨텍스트를 비우면 바로 뒤의 post 삭제가 준영속 엔티티를 지우려다 실패한다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE PostImage i SET i.postId = null, i.sortOrder = 0, i.updatedAt = CURRENT_TIMESTAMP WHERE i.postId = :postId")
    int detachAllByPostId(@Param("postId") Long postId);

    /**
     * 아직 어떤 독후감에도 붙지 않은 상태일 때만 행을 지운다 — 지운 행 수(0 또는 1)를 돌려준다.
     * 정리 배치가 고아를 조회한 뒤 지우기 전에 사용자가 그 사진을 독후감에 붙일 수 있으므로,
     * post_id IS NULL 을 삭제 조건에 함께 넣어 그 경합에서 방금 붙인 사진을 지우지 않게 한다.
     * 잡에는 트랜잭션이 없으므로 여기서 한 건씩 커밋한다 — 다른 건의 실패가 앞선 삭제를 되돌리지 않는다.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM PostImage i WHERE i.id = :id AND i.postId IS NULL")
    int deleteIfDetached(@Param("id") Long id);
}
