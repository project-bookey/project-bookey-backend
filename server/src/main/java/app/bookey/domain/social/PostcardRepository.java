package app.bookey.domain.social;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostcardRepository extends JpaRepository<Postcard, Long> {

    /** 같은 상대에게 답장 대기 중인 엽서가 이미 있는가 — 도배 방지. */
    boolean existsByFromUserIdAndToUserIdAndStatus(Long fromUserId, Long toUserId, PostcardStatus status);

    /** 받은 엽서 — 탈퇴한(계정이 종료된) 사람이 보낸 것은 뺀다. */
    @Query("""
            SELECT p FROM Postcard p
            WHERE p.toUserId = :toUserId
              AND p.fromUserId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')
            ORDER BY p.id DESC
            """)
    Page<Postcard> findAllByToUserIdOrderByIdDesc(@Param("toUserId") Long toUserId, Pageable pageable);

    /** 보낸 엽서 — 받은 사람이 탈퇴했으면 뺀다(그 사람의 답장이 담겨 있다). */
    @Query("""
            SELECT p FROM Postcard p
            WHERE p.fromUserId = :fromUserId
              AND p.toUserId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')
            ORDER BY p.id DESC
            """)
    Page<Postcard> findAllByFromUserIdOrderByIdDesc(@Param("fromUserId") Long fromUserId, Pageable pageable);

    long countByToUserIdAndStatus(Long toUserId, PostcardStatus status);

    /** 두 사람 사이에 답장이 오간 엽서가 있는가 — 방향은 묻지 않는다. 채팅을 여는 조건이다. */
    @Query("""
            SELECT COUNT(p) > 0 FROM Postcard p
            WHERE p.status = app.bookey.domain.social.PostcardStatus.REPLIED
              AND ((p.fromUserId = :a AND p.toUserId = :b) OR (p.fromUserId = :b AND p.toUserId = :a))
            """)
    boolean existsRepliedBetween(@Param("a") Long a, @Param("b") Long b);
}
