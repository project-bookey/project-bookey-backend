package app.bookey.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByHandle(String handle);

    boolean existsByHandle(String handle);

    @Query("""
            SELECT u FROM User u
            WHERE (:keyword IS NULL
                   OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.handle)   LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.email)    LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR u.status = :status)
            """)
    Page<User> search(@Param("keyword") String keyword,
                      @Param("status") UserStatus status,
                      Pageable pageable);
}
