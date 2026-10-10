package app.bookey.domain.novel;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter @Entity @Table(name = "novel_covers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NovelCover extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "novel_id") private Long novelId;
    @Column(name = "storage_key", nullable = false, length = 255) private String storageKey;
    @Column(nullable = false, columnDefinition = "text") private String url;
    private Integer width;
    private Integer height;
    public NovelCover(Long userId, String storageKey, String url, Integer width, Integer height) {
        this.userId = userId; this.storageKey = storageKey; this.url = url;
        this.width = width; this.height = height;
    }
    public void attach(Long novelId) { this.novelId = novelId; }
    public void detach() { this.novelId = null; }
}
