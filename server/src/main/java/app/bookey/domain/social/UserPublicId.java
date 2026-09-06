package app.bookey.domain.social;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 팔로우용 공개 코드 — 16자리. 유출 시 회전 가능(모임 초대 코드와 같은 철학, §8.5). */
@Getter
@Entity
@Table(name = "user_public_ids")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPublicId extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    public UserPublicId(Long userId, String code) {
        this.userId = userId;
        this.code = code;
    }

    public void rotate(String newCode, Instant now) {
        this.code = newCode;
        this.rotatedAt = now;
    }
}
