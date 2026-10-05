package app.bookey.domain.social;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 차단 한 방향 — blocker 가 blocked 를 막았다. blocker+blocked 당 1건.
 * 막힌 사람은 막은 사람에게 엽서·채팅을 보낼 수 없고, 막은 사람의 엽서함·채팅 목록에서 둘 사이의 것이 빠진다.
 */
@Getter
@Entity
@Table(name = "user_blocks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlock extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blocker_id", nullable = false)
    private Long blockerId;

    @Column(name = "blocked_id", nullable = false)
    private Long blockedId;

    private UserBlock(Long blockerId, Long blockedId) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
    }

    public static UserBlock of(Long blockerId, Long blockedId) {
        return new UserBlock(blockerId, blockedId);
    }
}
