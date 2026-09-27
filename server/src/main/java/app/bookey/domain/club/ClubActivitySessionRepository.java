package app.bookey.domain.club;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface ClubActivitySessionRepository extends JpaRepository<ClubActivitySession,Long>{Optional<ClubActivitySession> findByClubIdAndUserIdAndEndedAtIsNull(Long c,Long u);}
