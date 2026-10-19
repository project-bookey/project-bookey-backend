package app.bookey.domain.novel;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface NovelSunlightWalletRepository extends JpaRepository<NovelSunlightWallet, Long> {
    /** 쓰고 채우는 길은 행을 잠가 동시에 빠져 잔액이 음수가 되지 않게 한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from NovelSunlightWallet w where w.userId = :userId")
    Optional<NovelSunlightWallet> lockByUserId(@Param("userId") Long userId);
}
