package app.bookey.domain.club;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** "그래도 보기" 감사 로그 (§8.5). */
@Getter
@Entity
@Table(name = "club_post_reveals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClubPostReveal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_post_id", nullable = false)
    private Long clubPostId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "page_at_reveal", nullable = false)
    private int pageAtReveal;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public ClubPostReveal(Long clubPostId, Long userId, int pageAtReveal) {
        this.clubPostId = clubPostId;
        this.userId = userId;
        this.pageAtReveal = pageAtReveal;
    }
}
