package app.bookey.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByHandle(String handle);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByHandle(String handle);

    boolean existsByEmailIgnoreCase(String email);

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
