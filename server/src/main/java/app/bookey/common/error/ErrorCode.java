package app.bookey.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** API 전역 에러 코드. 클라이언트는 code 문자열로 분기한다. */
@Getter
public enum ErrorCode {

    // 공통
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "입력한 내용을 다시 확인해 주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "이 작업을 할 권한이 없어요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "이미 처리된 요청입니다."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "너무 자주 눌렀어요. 잠시 후 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 문제가 생겼어요. 조금 뒤 다시 시도해 주세요."),

    // 인증
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "로그인 정보가 맞지 않아요. 다시 로그인해 주세요."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일이나 비밀번호가 맞지 않아요."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "로그인이 만료됐어요. 다시 로그인해 주세요."),
    USER_SUSPENDED(HttpStatus.FORBIDDEN, "이용이 제한된 계정이에요."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입한 이메일이에요."),
    EMAIL_REJOIN_BLOCKED(HttpStatus.CONFLICT, "탈퇴한 이메일로는 다시 가입할 수 없어요."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "다른 사람이 쓰고 있는 닉네임이에요."),
    CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "선택 정보 수집·이용에 동의해야 저장할 수 있어요."),
    EMAIL_NOT_REGISTERED(HttpStatus.NOT_FOUND, "가입하지 않은 이메일이에요."),
    EMAIL_CODE_INVALID(HttpStatus.BAD_REQUEST, "인증 코드가 맞지 않아요."),
    EMAIL_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증 코드를 쓸 수 있는 시간이 지났어요. 새 코드를 받아 주세요."),
    IDENTITY_VERIFICATION_REQUIRED(HttpStatus.BAD_REQUEST, "본인인증이 필요해요."),
    IDENTITY_VERIFICATION_FAILED(HttpStatus.BAD_REQUEST, "본인인증을 마치지 못했어요. 다시 시도해 주세요."),
    IDENTITY_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이 본인인증 정보로 가입한 계정이 이미 있어요."),
    LEGAL_CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "필수 항목(만 14세 이상, 이용약관, 개인정보 수집·이용)에 동의해 주세요."),
    SOCIAL_SIGNUP_DISABLED(HttpStatus.FORBIDDEN, "소셜 계정으로는 가입할 수 없어요. 이메일로 가입한 뒤 소셜 계정을 연동해 주세요."),
    SOCIAL_ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT, "이미 다른 계정에 연동된 소셜 계정이에요."),
    SOCIAL_ACCOUNT_NOT_LINKED(HttpStatus.NOT_FOUND, "연동하지 않은 소셜 계정이에요."),
    LAST_LOGIN_METHOD(HttpStatus.CONFLICT, "마지막 로그인 수단이라 해제할 수 없습니다. 다른 소셜 계정을 먼저 연동해 주세요."),
    WRITE_BANNED(HttpStatus.FORBIDDEN, "글쓰기가 제한된 계정이에요."),

    // 소셜 — 팔로우 · 엽서 · 지갑 · 구독 (§14)
    FOLLOW_SELF(HttpStatus.BAD_REQUEST, "나 자신은 팔로우할 수 없어요."),
    POSTCARD_NOT_FOUND(HttpStatus.NOT_FOUND, "엽서를 찾을 수 없어요."),
    POSTCARD_SELF(HttpStatus.BAD_REQUEST, "나에게는 엽서를 보낼 수 없어요."),
    POSTCARD_BODY_TOO_LONG(HttpStatus.BAD_REQUEST, "엽서에는 16글자까지 적을 수 있어요."),
    POSTCARD_ALREADY_SENT(HttpStatus.CONFLICT, "이 사람에게 보낸 엽서가 아직 답장을 기다리고 있어요."),
    POSTCARD_ALREADY_REPLIED(HttpStatus.CONFLICT, "이미 답장한 엽서예요."),
    INSUFFICIENT_POSTCARD(HttpStatus.CONFLICT, "오늘 무료 엽서를 다 썼고, 가진 엽서도 없어요."),
    INSUFFICIENT_STAMP(HttpStatus.CONFLICT, "우표가 모자라요."),
    INSUFFICIENT_BOOKMARK(HttpStatus.CONFLICT, "책갈피가 모자라요."),
    SUBSCRIPTION_REQUIRED(HttpStatus.FORBIDDEN, "구독 회원만 이용할 수 있는 기능이에요."),
    PAYMENT_NOT_CONFIGURED(HttpStatus.BAD_REQUEST, "지금은 결제할 수 없어요. 잠시 후 다시 시도해 주세요."),
    CHAT_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없어요."),
    CHAT_NOT_ALLOWED(HttpStatus.FORBIDDEN, "엽서와 답장을 주고받은 사람과만 채팅할 수 있어요."),

    // 도서 / 서재
    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "책을 찾을 수 없어요."),
    TOTAL_PAGES_REQUIRED(HttpStatus.BAD_REQUEST, "이 책의 전체 쪽수를 먼저 적어 주세요."),
    RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "독서 기록을 찾을 수 없어요."),
    ALREADY_IN_LIBRARY(HttpStatus.CONFLICT, "이미 서재에 있는 책이에요."),
    REMARK_NOT_CLOSED(HttpStatus.CONFLICT, "다 읽었거나 하차한 책에만 한 줄평을 남길 수 있어요."),

    // 세션
    SESSION_ALREADY_OPEN(HttpStatus.CONFLICT, "이미 시간을 재고 있는 책이 있어요. 그 독서를 먼저 마쳐 주세요."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "독서 기록을 찾을 수 없어요."),
    SESSION_ALREADY_CLOSED(HttpStatus.CONFLICT, "이미 끝난 독서예요."),
    INVALID_PAGE_RANGE(HttpStatus.BAD_REQUEST, "쪽 번호를 다시 확인해 주세요."),

    // 리뷰
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없어요."),
    REVIEW_COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없어요."),

    // 모임
    CLUB_NOT_FOUND(HttpStatus.NOT_FOUND, "클럽을 찾을 수 없어요."),
    CLUB_CODE_INVALID(HttpStatus.NOT_FOUND, "없는 초대 코드예요. 다시 확인해 주세요."),
    CLUB_FULL(HttpStatus.CONFLICT, "클럽 정원이 다 찼어요."),
    CLUB_ENDED(HttpStatus.CONFLICT, "이미 끝난 클럽이에요."),
    CLUB_ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참가한 클럽이에요."),
    CLUB_NOT_MEMBER(HttpStatus.FORBIDDEN, "클럽 멤버만 쓸 수 있어요."),
    CLUB_NOT_HOST(HttpStatus.FORBIDDEN, "호스트만 할 수 있어요."),
    CLUB_KICKED(HttpStatus.FORBIDDEN, "다시 참가할 수 없는 클럽이에요."),
    CLUB_HOST_CANNOT_LEAVE(HttpStatus.CONFLICT, "호스트는 다른 멤버에게 호스트를 넘긴 뒤에 나갈 수 있어요."),
    CLUB_CHAT_LOCKED(HttpStatus.PAYMENT_REQUIRED, "책갈피 2개로 클럽 채팅을 먼저 열어 주세요."),
    CLUB_MEETING_NOT_FOUND(HttpStatus.NOT_FOUND, "모임을 찾을 수 없어요."),
    MEETING_FULL(HttpStatus.CONFLICT, "모임 정원이 찼어요."),
    MEETING_NOT_ATTENDING(HttpStatus.FORBIDDEN, "모임에 참여한 사람만 같이 읽을 수 있어요. 먼저 참여해 주세요."),
    MEETING_NOTE_READ_ONLY(HttpStatus.CONFLICT, "마무리했거나 끝난 모임의 노트라서 고칠 수 없어요."),
    MEETING_NOTE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "노트가 꽉 차서 더 붙일 수 없어요."),
    NUDGE_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "이미 찔렀어요. 24시간 뒤에 다시 보낼 수 있어요."),
    NUDGE_DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "오늘 보낼 수 있는 찌르기를 다 썼어요."),
    NUDGE_BLOCKED(HttpStatus.FORBIDDEN, "이 멤버는 찌르기를 받지 않아요."),
    SPOILER_BLOCKED(HttpStatus.FORBIDDEN, "아직 읽지 않은 부분의 글이에요."),

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

    // 고객문의 · FAQ
    INQUIRY_NOT_FOUND(HttpStatus.NOT_FOUND, "문의를 찾을 수 없어요."),
    INQUIRY_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "첨부 사진을 찾을 수 없어요."),
    INQUIRY_PENDING_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "답변을 기다리는 문의가 많아요. 답변을 받은 뒤 다시 남겨 주세요."),
    INQUIRY_ALREADY_ANSWERED(HttpStatus.CONFLICT, "이미 답변한 문의입니다."),
    INQUIRY_NOT_ANSWERED(HttpStatus.CONFLICT, "아직 답변하지 않은 문의입니다."),
    FAQ_NOT_FOUND(HttpStatus.NOT_FOUND, "FAQ를 찾을 수 없습니다."),

    // 댓글
    COMMENT_REPLY_DEPTH(HttpStatus.BAD_REQUEST, "답글에는 다시 답글을 달 수 없어요."),

    // 독후감
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "독후감을 찾을 수 없어요."),
    POST_COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없어요."),
    POST_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "사진을 찾을 수 없어요."),
    IMAGE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "사진은 10MB까지 올릴 수 있어요."),
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "JPG·PNG·WebP 사진만 올릴 수 있어요."),
    STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "사진을 저장하지 못했어요. 다시 시도해 주세요."),
    STORAGE_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "지금은 사진을 올릴 수 없어요.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
