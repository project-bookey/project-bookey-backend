package app.bookey.api.auth;

import app.bookey.common.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** {@link WriteBanGuarded} 가 붙은 핸들러 앞에서 쓰기정지 여부를 확인한다. */
@Component
@RequiredArgsConstructor
public class WriteBanInterceptor implements HandlerInterceptor {

    private final UserWriteGuard writeGuard;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method && method.hasMethodAnnotation(WriteBanGuarded.class)) {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof AuthUser user) {
                writeGuard.requireWritable(user.id());
            }
        }
        return true;
    }
}
