package app.bookey.domain.social;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostcardRepository extends JpaRepository<Postcard, Long> {

    /** 같은 상대에게 답장 대기 중인 엽서가 이미 있는가 — 도배 방지. */
    boolean existsByFromUserIdAndToUserIdAndStatus(Long fromUserId, Long toUserId, PostcardStatus status);

    Page<Postcard> findAllByToUserIdOrderByIdDesc(Long toUserId, Pageable pageable);

    Page<Postcard> findAllByFromUserIdOrderByIdDesc(Long fromUserId, Pageable pageable);

    long countByToUserIdAndStatus(Long toUserId, PostcardStatus status);
}
