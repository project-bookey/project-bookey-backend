package app.bookey.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** API 전역 에러 코드. 클라이언트는 code 문자열로 분기한다. */
@Getter
public enum ErrorCode {

    // 공통
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "이미 처리된 요청입니다."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다."),

    // 인증
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 유효하지 않습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    USER_SUSPENDED(HttpStatus.FORBIDDEN, "이용이 제한된 계정입니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    SOCIAL_ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT, "이미 다른 계정에 연결된 소셜 계정입니다."),
    WRITE_BANNED(HttpStatus.FORBIDDEN, "글쓰기가 제한된 계정입니다."),

    // 도서 / 서재
    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "도서를 찾을 수 없습니다."),
    TOTAL_PAGES_REQUIRED(HttpStatus.BAD_REQUEST, "총 페이지 수가 필요합니다."),
    RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "독서 기록을 찾을 수 없습니다."),
    ALREADY_IN_LIBRARY(HttpStatus.CONFLICT, "이미 서재에 있는 책입니다."),

    // 세션
    SESSION_ALREADY_OPEN(HttpStatus.CONFLICT, "이미 진행 중인 독서 세션이 있습니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "독서 세션을 찾을 수 없습니다."),
    SESSION_ALREADY_CLOSED(HttpStatus.CONFLICT, "이미 종료된 세션입니다."),
    INVALID_PAGE_RANGE(HttpStatus.BAD_REQUEST, "페이지 범위가 올바르지 않습니다."),

    // 리뷰
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 작성한 리뷰가 있습니다."),

    // 모임
    CLUB_NOT_FOUND(HttpStatus.NOT_FOUND, "모임을 찾을 수 없습니다."),
    CLUB_CODE_INVALID(HttpStatus.NOT_FOUND, "유효하지 않은 초대 코드입니다."),
    CLUB_FULL(HttpStatus.CONFLICT, "모임 정원이 가득 찼습니다."),
    CLUB_ENDED(HttpStatus.CONFLICT, "이미 종료된 모임입니다."),
    CLUB_ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참가한 모임입니다."),
    CLUB_NOT_MEMBER(HttpStatus.FORBIDDEN, "모임 멤버만 이용할 수 있습니다."),
    CLUB_NOT_HOST(HttpStatus.FORBIDDEN, "호스트만 할 수 있는 작업입니다."),
    CLUB_KICKED(HttpStatus.FORBIDDEN, "다시 참가할 수 없는 모임입니다."),
    CLUB_HOST_CANNOT_LEAVE(HttpStatus.CONFLICT, "호스트는 권한을 넘긴 뒤 나갈 수 있습니다."),
    NUDGE_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "이미 찔렀어요. 24시간 뒤에 다시 보낼 수 있습니다."),
    NUDGE_DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "오늘 보낼 수 있는 찌르기를 모두 썼습니다."),
    NUDGE_BLOCKED(HttpStatus.FORBIDDEN, "상대가 찌르기를 받지 않도록 설정했습니다."),
    SPOILER_BLOCKED(HttpStatus.FORBIDDEN, "아직 읽지 않은 범위의 글입니다."),

    // 관리자
    ADMIN_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    ADMIN_TOTP_REQUIRED(HttpStatus.UNAUTHORIZED, "2단계 인증이 필요합니다."),
    ADMIN_TOTP_INVALID(HttpStatus.UNAUTHORIZED, "인증 코드가 올바르지 않습니다."),
    ADMIN_FORBIDDEN(HttpStatus.FORBIDDEN, "이 작업에 필요한 관리자 권한이 없습니다."),
    ADMIN_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "처리 사유를 입력해야 합니다."),

    // 홈 콘텐츠 (배너 / 에디터 픽)
    BANNER_NOT_FOUND(HttpStatus.NOT_FOUND, "배너를 찾을 수 없습니다."),
    EDITOR_PICK_NOT_FOUND(HttpStatus.NOT_FOUND, "에디터 픽을 찾을 수 없습니다."),
    EDITOR_PICK_DUPLICATE(HttpStatus.CONFLICT, "이미 추천 목록에 있는 책입니다."),

    // 챌린지
    CHALLENGE_NOT_FOUND(HttpStatus.NOT_FOUND, "챌린지를 찾을 수 없습니다."),
    CHALLENGE_ALREADY_ACTIVE(HttpStatus.CONFLICT, "이 책에는 진행 중인 챌린지가 이미 있습니다."),
    CHALLENGE_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "이미 종료된 챌린지입니다."),
    CHALLENGE_REQUIRES_PAGES(HttpStatus.BAD_REQUEST, "총 쪽수가 있는 책에만 챌린지를 걸 수 있습니다."),
    CHALLENGE_INVALID_RECORD(HttpStatus.BAD_REQUEST, "읽는 중인 책에만 챌린지를 걸 수 있습니다."),

    // 오려둔 문장(밑줄)
    QUOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "문장을 찾을 수 없습니다."),
    QUOTE_COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
