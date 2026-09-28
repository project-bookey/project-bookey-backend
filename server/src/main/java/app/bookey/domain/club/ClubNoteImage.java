package app.bookey.domain.club;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모임 노트북에 붙는 사진. 업로드 시에는 page_id 가 비어 있고(임시), 그 사진을 참조하는 문서가 저장될 때 채운다.
 * 문서에서 빠지면 다시 비우고, 24시간 안에 어디에도 붙지 않은 사진은 정리 배치가 지운다(post_images 와 같은 정책).
 */
@Getter
@Entity
@Table(name = "club_note_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClubNoteImage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "page_id")
    private Long pageId;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private int byteSize;

    private Integer width;

    private Integer height;

    @Builder
    private ClubNoteImage(Long clubId, Long userId, String storageKey, String url, String contentType,
                          int byteSize, Integer width, Integer height) {
        this.clubId = clubId;
        this.userId = userId;
        this.storageKey = storageKey;
        this.url = url;
        this.contentType = contentType;
        this.byteSize = byteSize;
        this.width = width;
        this.height = height;
    }

    public boolean belongsTo(Long clubId) {
        return this.clubId.equals(clubId);
    }

    /** 아직 어떤 페이지 문서에도 참조되지 않은 임시 업로드인지. */
    public boolean isDetached() {
        return pageId == null;
    }

    public void attach(Long pageId) {
        this.pageId = pageId;
    }

    public void detach() {
        this.pageId = null;
    }
}
