package app.bookey.domain.novel;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/** 소설 좋아요 — 책·독후감 좋아요와 따로 센다. user_id+novel_id 당 1건. */
@Getter @Entity @Table(name = "novel_likes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NovelLike extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "novel_id", nullable = false) private Long novelId;
    @Builder
    private NovelLike(Long userId, Long novelId) { this.userId = userId; this.novelId = novelId; }
}
