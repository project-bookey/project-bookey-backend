package app.bookey.domain.novel;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter @Entity @Table(name = "novel_drafts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NovelDraft extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "novel_id", nullable = false) private Long novelId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "turn_number", nullable = false) private long turnNumber;
    @Column(nullable = false, length = 120) private String title;
    @Column(nullable = false, columnDefinition = "text") private String body;
    public NovelDraft(Long novelId, Long userId) { this.novelId = novelId; this.userId = userId; }
    public void save(long turnNumber, String title, String body) {
        this.turnNumber = turnNumber; this.title = title; this.body = body;
    }
}
