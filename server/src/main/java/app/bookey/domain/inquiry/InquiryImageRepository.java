package app.bookey.domain.inquiry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface InquiryImageRepository extends JpaRepository<InquiryImage, Long> {

    List<InquiryImage> findAllByInquiryIdOrderBySortOrderAscIdAsc(Long inquiryId);

    /** 어드민 목록의 첨부 수 — 한 페이지 문의의 사진을 한 번에 읽는다. */
    List<InquiryImage> findAllByInquiryIdIn(Collection<Long> inquiryIds);

    /** 어느 문의에도 붙지 않은 채 남은 사진 — 정리 배치용. */
    List<InquiryImage> findAllByInquiryIdIsNullAndUpdatedAtBefore(Instant before);

    /**
     * 내 것이고 아직 어디에도 붙지 않았을 때만 붙인다 — 붙인 행 수(0/1)를 돌려준다.
     * 같은 사진으로 문의 두 건이 동시에 들어오거나 정리 배치가 그 사이 행을 지웠으면 0 이 나와 거절할 수 있다
     * (조회 후 엔티티를 고쳐 UPDATE 하면 조건이 빠져 나중 요청이 덮어쓰거나 500 이 난다).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE InquiryImage i SET i.inquiryId = :inquiryId, i.sortOrder = :sortOrder "
            + "WHERE i.id = :id AND i.userId = :userId AND i.inquiryId IS NULL")
    int attachIfDetached(@Param("id") Long id, @Param("userId") Long userId,
                         @Param("inquiryId") Long inquiryId, @Param("sortOrder") short sortOrder);

    /** 아직 어디에도 붙지 않았을 때만 행을 지운다 — 조회와 삭제 사이에 문의가 그 사진을 붙인 경합을 막는다. */
    @Transactional
    @Modifying
    @Query("DELETE FROM InquiryImage i WHERE i.id = :id AND i.inquiryId IS NULL")
    int deleteIfDetached(@Param("id") Long id);
}
