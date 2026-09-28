package app.bookey.domain.club;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * 모임 노트북의 한 페이지. 모임당 노트북은 한 권이라 별도 노트북 엔티티 없이 club_id 로 묶고 seq 로 정렬한다.
 *
 * <p>document 는 앱이 소유한 JSON — 서버는 해석하지 않고 그대로 저장·반환한다(요소 수·크기만 검사).
 * 저장은 문서 전체 덮어쓰기라 version 으로 낙관적 잠금을 건다. JPA @Version 이 아니라 서비스가 직접 비교한다 —
 * 충돌을 409 CLUB_NOTE_CONFLICT 로 돌려주고 앱이 최신을 받아 병합하게 하기 위해서다.
 */
@Getter
@Entity
@Table(name = "club_note_pages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClubNotePage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(nullable = false)
    private int seq;

    @Column(length = 60)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> document = Map.of();

    @Column(nullable = false)
    private int version;

    @Column(name = "element_count", nullable = false)
    private int elementCount;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    /** 빈 문서·version 0 으로 시작한다. */
    public static ClubNotePage create(Long clubId, int seq, String title, Long userId) {
        ClubNotePage page = new ClubNotePage();
        page.clubId = clubId;
        page.seq = seq;
        page.title = title;
        page.createdBy = userId;
        page.updatedBy = userId;
        return page;
    }

    /** 문서 전체를 덮어쓰고 version 을 하나 올린다. 호출 전에 version 비교는 서비스가 끝냈다. */
    public void overwrite(String title, Map<String, Object> document, int elementCount, Long userId) {
        this.title = title;
        this.document = document == null ? Map.of() : document;
        this.elementCount = elementCount;
        this.updatedBy = userId;
        this.version++;
    }

    public boolean belongsTo(Long clubId) {
        return this.clubId.equals(clubId);
    }

    public boolean isCreatedBy(Long userId) {
        return createdBy != null && createdBy.equals(userId);
    }
}
