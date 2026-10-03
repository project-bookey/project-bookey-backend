package app.bookey.domain.inquiry;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** 고객문의. 어드민 목록은 필터 조합마다 메서드를 두고 정렬은 Pageable 에 담아 넘긴다(신고 큐와 같은 방식). */
public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    Page<Inquiry> findAllByUserId(Long userId, Pageable pageable);

    Page<Inquiry> findAllBy(Pageable pageable);

    Page<Inquiry> findAllByStatus(InquiryStatus status, Pageable pageable);

    Page<Inquiry> findAllByCategory(InquiryCategory category, Pageable pageable);

    Page<Inquiry> findAllByStatusAndCategory(InquiryStatus status, InquiryCategory category, Pageable pageable);

    long countByStatus(InquiryStatus status);

    long countByUserIdAndStatus(Long userId, InquiryStatus status);

    /** 답변 저장용 — 같은 문의에 동시에 들어온 답변을 한 줄로 세운다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inquiry i WHERE i.id = :id")
    Optional<Inquiry> findByIdForUpdate(@Param("id") Long id);

    /** 탈퇴 — 문의를 모두 지운다. 붙어 있던 사진은 FK 로 떨어져 정리 배치가 회수한다. */
    void deleteAllByUserId(Long userId);
}
