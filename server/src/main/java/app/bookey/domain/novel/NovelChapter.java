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
    /** 가지의 깊이. 뿌리 회차가 1이고, 가지를 뻗을 때마다 부모보다 1 크다. */
    @Column(name = "chapter_number", nullable = false) private int chapterNumber;
    /** 이 회차가 뻗어 나온 회차. 뿌리 회차만 비어 있다. */
    @Column(name = "parent_chapter_id") private Long parentChapterId;
    @Column(nullable = false, length = 120) private String title;
    @Column(nullable = false, columnDefinition = "text") private String body;
    public NovelChapter(Long novelId, Long authorId, Long parentChapterId, int chapterNumber, String title, String body) {
        this.novelId = novelId; this.authorId = authorId; this.parentChapterId = parentChapterId;
        this.chapterNumber = chapterNumber; this.title = title.strip(); this.body = body.strip();
    }
}
