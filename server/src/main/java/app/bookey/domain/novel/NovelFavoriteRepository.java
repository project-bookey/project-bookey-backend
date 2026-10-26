package app.bookey.domain.novel;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

/** 즐겨찾기 행은 작품이나 계정이 지워지면 함께 지워진다(외래 키 ON DELETE CASCADE). */
public interface NovelFavoriteRepository extends JpaRepository<NovelFavorite, Long> {
    Optional<NovelFavorite> findByUserIdAndNovelId(Long userId, Long novelId);
    List<NovelFavorite> findAllByUserIdAndNovelIdIn(Long userId, Collection<Long> novelIds);
    /**
     * 내가 즐겨찾기에 담은 작품 — 최근에 담은 것부터. 지금 볼 수 없는 작품(만든 사람이 탈퇴했거나,
     * 참가하지 않은 비공개 작품)은 뺀다. 판정은 NovelService.readable 과 같다.
     */
    @Query(value = """
        select n from NovelFavorite f join Novel n on n.id = f.novelId
        where f.userId = :userId and n.kind = :kind
        and n.ownerId not in (select u.id from User u where u.status = 'TERMINATED')
        and (n.isPublic = true or n.ownerId = :userId
             or n.id in (select m.novelId from NovelMember m where m.userId = :userId and m.status in ('ACTIVE', 'PENDING')))
        order by f.createdAt desc, f.id desc
        """, countQuery = """
        select count(f) from NovelFavorite f join Novel n on n.id = f.novelId
        where f.userId = :userId and n.kind = :kind
        and n.ownerId not in (select u.id from User u where u.status = 'TERMINATED')
        and (n.isPublic = true or n.ownerId = :userId
             or n.id in (select m.novelId from NovelMember m where m.userId = :userId and m.status in ('ACTIVE', 'PENDING')))
        """)
    Page<Novel> favorites(@Param("userId") Long userId, @Param("kind") NovelKind kind, Pageable pageable);
}
