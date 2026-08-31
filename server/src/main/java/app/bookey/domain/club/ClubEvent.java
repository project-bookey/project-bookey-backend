package app.bookey.domain.club;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/** 모임 활동 피드. 낙오·최하위 정보는 여기에 남기지 않는다(§12.4). */
@Getter
@Entity
@Table(name = "club_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClubEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClubEventType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload = Map.of();

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public ClubEvent(Long clubId, Long userId, ClubEventType type, Map<String, Object> payload) {
        this.clubId = clubId;
        this.userId = userId;
        this.type = type;
        this.payload = payload == null ? Map.of() : payload;
    }
}
