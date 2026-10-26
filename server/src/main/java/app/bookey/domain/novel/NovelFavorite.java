package app.bookey.domain.novel;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/** 소설 즐겨찾기 — 좋아요와 따로 담는다(2026-10-26 사용자 결정). user_id+novel_id 당 1건. */
@Getter @Entity @Table(name = "novel_favorites")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NovelFavorite extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "novel_id", nullable = false) private Long novelId;
    @Builder
    private NovelFavorite(Long userId, Long novelId) { this.userId = userId; this.novelId = novelId; }
}
