package app.bookey.domain.club;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * 모임 공유 노트 — 모임(약속) 하나에 대형노트 한 권. 클럽 활성 멤버 누구나 함께 고친다.
 *
 * <p>document 는 앱이 소유한 JSON 이다. 서버는 요소 id 기준 연산({@link MeetingNoteOps})만 적용하고 요소 내용은 해석하지 않는다.
 * 연산이 적용될 때마다 version 이 하나 오른다 — 실시간 연결로 받은 연산 사이에 빈 번호가 있으면 앱이 노트를 다시 받는다.
 */
@Getter
@Entity
@Table(name = "club_meeting_notes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClubMeetingNote extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(name = "meeting_id", nullable = false, unique = true)
    private Long meetingId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> document = Map.of();

    @Column(nullable = false)
    private int version;

    @Column(name = "element_count", nullable = false)
    private int elementCount;

    @Column(name = "updated_by")
    private Long updatedBy;

    /** 마무리한 시각 — 모임을 연 사람(또는 호스트)이 마무리하면 노트는 읽기만 된다. */
    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    /** 문서를 갈아 끼우고 version 을 하나 올린다. 연산 적용·상한 검사는 호출 전에 끝났다. */
    public void replace(Map<String, Object> document, int elementCount, Long userId) {
        this.document = document;
        this.elementCount = elementCount;
        this.updatedBy = userId;
        this.version++;
    }

    public void close(Long userId, Instant at) {
        if (this.closedAt == null) {
            this.closedAt = at;
            this.closedBy = userId;
        }
    }

    public boolean isClosed() {
        return closedAt != null;
    }

    public boolean belongsTo(Long clubId) {
        return this.clubId.equals(clubId);
    }
}
