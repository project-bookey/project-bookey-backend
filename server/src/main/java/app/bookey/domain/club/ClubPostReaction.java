package app.bookey.domain.club;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "club_post_reactions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClubPostReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_post_id", nullable = false)
    private Long clubPostId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private ReactionKind kind;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public ClubPostReaction(Long clubPostId, Long userId, ReactionKind kind) {
        this.clubPostId = clubPostId;
        this.userId = userId;
        this.kind = kind;
    }
}
