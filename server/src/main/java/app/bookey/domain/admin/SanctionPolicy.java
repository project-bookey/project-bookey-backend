package app.bookey.domain.admin;

import app.bookey.domain.user.UserStatus;

import java.time.Instant;
import java.util.Collection;

/**
 * 제재 기록으로 회원 상태를 정한다. 살아 있는 제재 중 가장 무거운 것이 상태가 되고,
 * 경고(WARN)는 기록만 남길 뿐 상태를 바꾸지 않는다. 해제됐거나 기간이 끝난 제재는 세지 않는다.
 */
public final class SanctionPolicy {

    private SanctionPolicy() {
    }

    public static UserStatus statusFor(Collection<UserSanction> sanctions, Instant now) {
        UserStatus status = UserStatus.ACTIVE;
        for (UserSanction sanction : sanctions) {
            if (sanction.isActiveAt(now)) {
                status = heavier(status, statusOf(sanction.getType()));
            }
        }
        return status;
    }

    public static UserStatus statusOf(SanctionType type) {
        return switch (type) {
            case WARN -> UserStatus.ACTIVE;
            case WRITE_BAN -> UserStatus.WRITE_BANNED;
            case SUSPEND -> UserStatus.SUSPENDED;
            case TERMINATE -> UserStatus.TERMINATED;
        };
    }

    /** 새 제재가 이미 걸린 더 무거운 상태를 낮추지 않게 한다. */
    public static UserStatus heavier(UserStatus a, UserStatus b) {
        return a.severity() >= b.severity() ? a : b;
    }
}
