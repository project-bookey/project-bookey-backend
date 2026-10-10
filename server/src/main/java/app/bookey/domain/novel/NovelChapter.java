package app.bookey.domain.novel;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter @Entity @Table(name = "novel_chapters")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NovelChapter extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "novel_id", nullable = false) private Long novelId;
    @Column(name = "author_id") private Long authorId;
    @Column(name = "chapter_number", nullable = false) private int chapterNumber;
    @Column(nullable = false, length = 120) private String title;
    @Column(nullable = false, columnDefinition = "text") private String body;
    public NovelChapter(Long novelId, Long authorId, int chapterNumber, String title, String body) {
        this.novelId = novelId; this.authorId = authorId; this.chapterNumber = chapterNumber;
        this.title = title.strip(); this.body = body.strip();
    }
}
