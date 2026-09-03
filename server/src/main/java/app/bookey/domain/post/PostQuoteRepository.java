package app.bookey.domain.post;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PostQuoteRepository extends JpaRepository<PostQuote, Long> {

    /** 목록 배치 로딩용 — 독후감별 밑줄 연결을 순서대로 한 번에 읽는다. */
    List<PostQuote> findAllByPostIdInOrderBySortOrderAscIdAsc(Collection<Long> postIds);

    void deleteAllByPostId(Long postId);
}
