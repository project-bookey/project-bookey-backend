package app.bookey.domain.novel;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/** 작품을 시작할 때 줄거리를 가져올 수 있는 원작. */
@Getter @Entity @Table(name = "novel_base_works")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NovelBaseWork extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 120) private String title;
    @Column(nullable = false, length = 30) private String genre;
    @Column(nullable = false, length = 500) private String synopsis;
    @Column(nullable = false, length = 120) private String source;
    @Column(nullable = false) private boolean active;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    public NovelBaseWork(String title, String genre, String synopsis, String source) {
        this.title = title; this.genre = genre; this.synopsis = synopsis; this.source = source; this.active = true;
    }
}
