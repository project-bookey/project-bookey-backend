package app.bookey.domain.novel;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/** 소설 관심 장르 — 북키의 선호 카테고리(users.preferred_categories)와 따로 둔다. */
@Getter @Entity @Table(name = "novel_user_genres")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NovelUserGenre extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 30) private String genre;
    public NovelUserGenre(Long userId, String genre) { this.userId = userId; this.genre = genre; }
}
