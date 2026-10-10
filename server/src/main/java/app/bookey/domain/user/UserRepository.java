package app.bookey.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.Instant;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    /** 같은 사람(CI)의 중복 가입 검사 — 본인인증 가입 경로. */
    boolean existsByCi(String ci);

    Optional<User> findByHandle(String handle);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByHandle(String handle);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByNicknameIgnoreCase(String nickname);

    boolean existsByNicknameIgnoreCaseAndIdNot(String nickname, Long id);

    List<User> findAllByStatusAndDeletionRequestedAtLessThanEqual(UserStatus status, Instant cutoff);

    /** 쓰기정지 확인처럼 상태만 필요할 때 — 회원 행 전체를 읽지 않는다. */
    @Query("select u.status from User u where u.id = :id")
    Optional<UserStatus> findStatusById(@Param("id") Long id);

    /** 제재로 묶인 회원(탈퇴 신청자 제외) — 만료된 제재를 풀어 주는 잡이 훑는다. */
    List<User> findAllByStatusInAndDeletionRequestedAtIsNull(List<UserStatus> statuses);

    long countByStatusNot(UserStatus status);

    /** 탈퇴를 요청한 계정 — 아직 30일이 지나지 않아 행이 남아 있는 사람까지. */
    List<User> findAllByStatusAndDeletionRequestedAtIsNotNull(UserStatus status);

    boolean existsByIdAndStatus(Long id, UserStatus status);

    /**
     * 탈퇴했거나 운영팀이 계정을 종료한 사람인가 — 그 사람의 글·기록·메시지는 다른 사람에게 보이지 않는다.
     * 목록 쿼리는 같은 규칙을 {@code x.userId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')} 로 건다.
     */
    default boolean isTerminated(Long userId) {
        return userId != null && existsByIdAndStatus(userId, UserStatus.TERMINATED);
    }

    /**
     * 관리자 회원 검색.
     *
     * <p>PostgreSQL 은 바인딩 값이 null 이면 파라미터 타입을 bytea 로 추론해 {@code lower(?)} 가 깨진다.
     * 그래서 "조건이 없을 때"를 파라미터 null 로 표현하지 않고, 상태 유무에 따라 메서드를 나눈다.
     * 키워드는 빈 문자열로 대체하면 {@code '%%'} 가 되어 전체 검색이 된다.
     */
    @Query("""
            SELECT u FROM User u
            WHERE LOWER(u.nickname) LIKE LOWER(CONCAT('%', COALESCE(:keyword, ''), '%'))
               OR LOWER(u.handle)   LIKE LOWER(CONCAT('%', COALESCE(:keyword, ''), '%'))
               OR LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', COALESCE(:keyword, ''), '%'))
            """)
    Page<User> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            SELECT u FROM User u
            WHERE u.status = :status
              AND (LOWER(u.nickname) LIKE LOWER(CONCAT('%', COALESCE(:keyword, ''), '%'))
                OR LOWER(u.handle)   LIKE LOWER(CONCAT('%', COALESCE(:keyword, ''), '%'))
                OR LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', COALESCE(:keyword, ''), '%')))
            """)
    Page<User> searchByKeywordAndStatus(@Param("keyword") String keyword,
                                        @Param("status") UserStatus status,
                                        Pageable pageable);
}
