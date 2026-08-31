package app.bookey.domain.admin;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/** 모든 관리자 행위 기록. 회원 개인정보 열람도 로그 대상(§F13). */
@Getter
@Entity
@Table(name = "admin_audit_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(name = "target_type", length = 20)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(length = 500)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_data", columnDefinition = "jsonb")
    private Map<String, Object> beforeData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_data", columnDefinition = "jsonb")
    private Map<String, Object> afterData;

    @Column(length = 45)
    private String ip;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Builder
    private AdminAuditLog(Long adminId, String action, String targetType, Long targetId,
                          String reason, Map<String, Object> beforeData, Map<String, Object> afterData,
                          String ip, String userAgent) {
        this.adminId = adminId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.beforeData = beforeData;
        this.afterData = afterData;
        this.ip = ip;
        this.userAgent = userAgent;
    }
}
