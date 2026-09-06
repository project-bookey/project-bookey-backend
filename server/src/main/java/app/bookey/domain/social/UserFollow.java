package app.bookey.domain.social;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 팔로우 한 방향. 맞팔로우는 양방향 두 행이다. follower+followee 당 1건. */
@Getter
@Entity
@Table(name = "user_follows")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFollow extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "follower_id", nullable = false)
    private Long followerId;

    @Column(name = "followee_id", nullable = false)
    private Long followeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FollowSource source;

    @Builder
    private UserFollow(Long followerId, Long followeeId, FollowSource source) {
        this.followerId = followerId;
        this.followeeId = followeeId;
        this.source = source;
    }
}
