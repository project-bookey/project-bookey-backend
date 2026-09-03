package app.bookey.domain.post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    /** 목록 배치 로딩용 — 독후감별 사진을 순서대로 한 번에 읽는다. */
    List<PostImage> findAllByPostIdInOrderBySortOrderAscIdAsc(Collection<Long> postIds);

    /** 독후감에 붙지 못하고 남은 임시 업로드 — 정리 배치용. */
    List<PostImage> findAllByPostIdIsNullAndCreatedAtBefore(Instant before);

    /** 독후감의 사진 연결을 모두 끊는다(사진 자체는 남긴다). */
    @Modifying
    @Query("UPDATE PostImage i SET i.postId = null, i.sortOrder = 0 WHERE i.postId = :postId")
    int detachAllByPostId(@Param("postId") Long postId);
}
