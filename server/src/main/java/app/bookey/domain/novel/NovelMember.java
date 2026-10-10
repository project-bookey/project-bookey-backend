package app.bookey.domain.novel;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter @Entity @Table(name = "novel_members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NovelMember extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "novel_id", nullable = false) private Long novelId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 10) private String status;
    public NovelMember(Long novelId, Long userId, boolean approved) { this.novelId = novelId; this.userId = userId; this.status = approved ? "ACTIVE" : "PENDING"; }
    public void leave() { status = "LEFT"; }
    public void apply() { status = "PENDING"; }
    public void approve() { status = "ACTIVE"; }
    public void reject() { status = "REJECTED"; }
}
