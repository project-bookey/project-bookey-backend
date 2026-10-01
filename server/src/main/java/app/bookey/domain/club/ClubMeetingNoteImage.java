package app.bookey.domain.club;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모임 공유 노트에 붙는 사진. 업로드 시에는 note_id 가 비어 있고(임시), 그 사진을 참조하는 요소가 노트에 들어오면 채운다.
 * 문서에서 빠지면 다시 비우고, 24시간 안에 어디에도 붙지 않은 사진은 정리 배치가 지운다(post_images 와 같은 정책).
 */
@Getter
@Entity
@Table(name = "club_meeting_note_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClubMeetingNoteImage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "note_id")
    private Long noteId;

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
    private ClubMeetingNoteImage(Long clubId, Long meetingId, Long userId, String storageKey, String url,
                                 String contentType, int byteSize, Integer width, Integer height) {
        this.clubId = clubId;
        this.meetingId = meetingId;
        this.userId = userId;
        this.storageKey = storageKey;
        this.url = url;
        this.contentType = contentType;
        this.byteSize = byteSize;
        this.width = width;
        this.height = height;
    }

    /** 같은 모임에 올린 사진인지 — 다른 모임 노트의 사진을 끌어다 붙이지 못하게 한다. */
    public boolean belongsTo(Long meetingId) {
        return this.meetingId.equals(meetingId);
    }

    /** 아직 어떤 노트에도 참조되지 않은 임시 업로드인지. */
    public boolean isDetached() {
        return noteId == null;
    }

    public void attach(Long noteId) {
        this.noteId = noteId;
    }

    public void detach() {
        this.noteId = null;
    }
}
