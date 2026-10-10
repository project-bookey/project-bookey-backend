package app.bookey.api.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 쓰기정지 회원에게 막는 엔드포인트. 남에게 보이는 콘텐츠를 만들거나 고치는 경로에 붙인다.
 * 새 글쓰기 경로를 만들면 여기에 붙였는지 확인할 것 — 빠지면 쓰기정지가 뚫린다.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WriteBanGuarded {
}
