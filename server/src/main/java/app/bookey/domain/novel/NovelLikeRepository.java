package app.bookey.domain.novel;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

/** 탈퇴한 사람의 좋아요는 수에서 뺀다(행은 계정이 지워질 때 함께 지워진다). */
public interface NovelLikeRepository extends JpaRepository<NovelLike, Long> {
    Optional<NovelLike> findByUserIdAndNovelId(Long userId, Long novelId);
    @Query("""
        select count(l) from NovelLike l where l.novelId = :novelId
        and l.userId not in (select t.id from User t where t.status = 'TERMINATED')
        """)
    long countByNovelId(@Param("novelId") Long novelId);
    List<NovelLike> findAllByUserIdAndNovelIdIn(Long userId, Collection<Long> novelIds);
    /** 소설별 좋아요 수 — 목록 한 페이지를 한 번에 센다. */
    @Query("""
        select l.novelId as novelId, count(l) as likeCount from NovelLike l where l.novelId in :novelIds
        and l.userId not in (select t.id from User t where t.status = 'TERMINATED')
        group by l.novelId
        """)
    List<NovelLikeCount> countPerNovel(@Param("novelIds") Collection<Long> novelIds);
    interface NovelLikeCount {
        Long getNovelId();
        long getLikeCount();
    }
}
