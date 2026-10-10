package app.bookey.api.auth;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.user.UserRepository;
import app.bookey.domain.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 쓰기정지(WRITE_BAN) 회원이 글·댓글·채팅·엽서·프로필 같은 남에게 보이는 것을 만들지 못하게 막는다.
 * 컨트롤러는 {@link WriteBanGuarded} 를 붙이면 {@link WriteBanInterceptor} 가 이 검사를 대신 해 준다.
 * 독서 기록·좋아요·팔로우·차단·신고·결제·문의(이의 제기 경로, 약관 제10조 ②)는 막지 않는다.
 */
@Component
@RequiredArgsConstructor
public class UserWriteGuard {

    private final UserRepository userRepository;

    public void requireWritable(Long userId) {
        UserStatus status = userRepository.findStatusById(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!status.canWrite()) {
            throw ApiException.of(ErrorCode.WRITE_BANNED);
        }
    }
}
