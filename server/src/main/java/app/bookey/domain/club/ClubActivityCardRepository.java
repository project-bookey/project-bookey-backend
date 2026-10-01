package app.bookey.domain.club;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface ClubActivityCardRepository extends JpaRepository<ClubActivityCard,Long>{Optional<ClubActivityCard> findBySessionId(Long id);List<ClubActivityCard> findTop50ByUserIdOrderByIdDesc(Long userId);}
