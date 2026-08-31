package app.bookey.admin.support;

import app.bookey.common.security.AuthAdmin;
import app.bookey.domain.admin.AdminAuditLog;
import app.bookey.domain.admin.AdminAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * 관리자 감사 로그 (§F13).
 * 변경뿐 아니라 <b>개인정보 열람도</b> 기록 대상이다.
 */
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final AdminAuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuthAdmin admin, String action, String targetType, Long targetId,
                    String reason, Map<String, Object> before, Map<String, Object> after) {
        HttpServletRequest request = currentRequest();
        auditLogRepository.save(AdminAuditLog.builder()
                .adminId(admin.id())
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .reason(reason)
                .beforeData(before)
                .afterData(after)
                .ip(request == null ? null : clientIp(request))
                .userAgent(request == null ? null : truncate(request.getHeader("User-Agent")))
                .build());
    }

    public void logView(AuthAdmin admin, String targetType, Long targetId) {
        log(admin, "VIEW_" + targetType, targetType, targetId, null, null, null);
    }

    private HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 300 ? value.substring(0, 300) : value;
    }
}
