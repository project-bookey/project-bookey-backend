package app.bookey.domain.social;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** 마이페이지 방문 기록 — 방문자·호스트·KST 날짜당 1건. 수는 전체 공개, 목록은 구독 전용(§14.2). */
@Getter
@Entity
@Table(name = "profile_visits")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileVisit extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "visitor_id", nullable = false)
    private Long visitorId;

    @Column(name = "host_id", nullable = false)
    private Long hostId;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    public ProfileVisit(Long visitorId, Long hostId, LocalDate visitDate) {
        this.visitorId = visitorId;
        this.hostId = hostId;
        this.visitDate = visitDate;
    }
}
