package app.bookey.domain.novel;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface NovelMemberRepository extends JpaRepository<NovelMember, Long> {
    Optional<NovelMember> findByNovelIdAndUserId(Long novelId, Long userId);
    @Query("""
        select m from NovelMember m where m.novelId = :id and m.status = 'ACTIVE'
        and m.userId not in (select u.id from User u where u.status = 'TERMINATED')
        order by m.id
        """)
    List<NovelMember> activeMembers(@Param("id") Long id);
    List<NovelMember> findByNovelIdAndStatusOrderByIdAsc(Long novelId, String status);
}
