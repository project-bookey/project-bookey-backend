package app.bookey.common.storage;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 저장소 키 규칙. 클라이언트가 보낸 파일명은 절대 쓰지 않는다(경로 탐색·덮어쓰기 방지).
 * 연·월 폴더는 UTC 기준으로 끊는다 — 로컬·GCS 어디서 봐도 같은 경로가 나오게 하기 위해서다.
 */
public final class StorageKeys {

    private static final DateTimeFormatter YEAR_MONTH =
            DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneOffset.UTC);

    private StorageKeys() {
    }

    /** posts/{userId}/{yyyy}/{MM}/{uuid}.{ext} */
    public static String forPostImage(long userId, Instant now, String extension) {
        return "posts/" + userId + "/" + YEAR_MONTH.format(now) + "/"
                + UUID.randomUUID() + "." + safeExtension(extension);
    }

    /** inquiries/{userId}/{yyyy}/{MM}/{uuid}.{ext} — 고객문의 첨부 사진. */
    public static String forInquiryImage(long userId, Instant now, String extension) {
        return "inquiries/" + userId + "/" + YEAR_MONTH.format(now) + "/"
                + UUID.randomUUID() + "." + safeExtension(extension);
    }

    /** clubs/{clubId}/{userId}/{yyyy}/{MM}/{uuid}.{ext} — 읽기로그 조각 사진. */
    public static String forClubLog(long clubId, long userId, Instant now, String extension) {
        return "clubs/" + clubId + "/" + userId + "/" + YEAR_MONTH.format(now) + "/"
                + UUID.randomUUID() + "." + safeExtension(extension);
    }

    /** clubs/{clubId}/meeting-notes/{meetingId}/{userId}/{yyyy}/{MM}/{uuid}.{ext} — 모임 공유 노트 사진. */
    public static String forMeetingNote(long clubId, long meetingId, long userId, Instant now, String extension) {
        return "clubs/" + clubId + "/meeting-notes/" + meetingId + "/" + userId + "/" + YEAR_MONTH.format(now) + "/"
                + UUID.randomUUID() + "." + safeExtension(extension);
    }

    /** clubs/{clubId}/background/{uuid}.{ext} — 클럽 머리 배경 사진. */
    public static String forClubBackground(long clubId, String extension) {
        return "clubs/" + clubId + "/background/" + UUID.randomUUID() + "." + safeExtension(extension);
    }

    /** avatars/{userId}/{uuid}.{ext} — 프로필 사진. */
    public static String forAvatar(long userId, String extension) {
        return "avatars/" + userId + "/" + UUID.randomUUID() + "." + safeExtension(extension);
    }

    private static final java.util.regex.Pattern AVATAR_KEY =
            java.util.regex.Pattern.compile("avatars/(\\d+)/[A-Za-z0-9-]+\\.[A-Za-z0-9]+");

    /**
     * 프로필 사진 URL → 저장소 키. 우리 저장소에 올린 그 회원의 사진(avatars/{userId}/{uuid}.{ext})이 아니면 null —
     * 소셜 프로필 사진 URL 이나 손으로 넣은 URL 로 다른 파일을 지우지 않게 모양을 엄격히 본다.
     */
    public static String avatarKeyOf(long userId, String url) {
        if (url == null) {
            return null;
        }
        int at = url.indexOf("avatars/" + userId + "/");
        if (at < 0) {
            return null;
        }
        String key = url.substring(at);
        int query = key.indexOf('?');
        if (query >= 0) {
            key = key.substring(0, query);
        }
        return AVATAR_KEY.matcher(key).matches() ? key : null;
    }

    /** 확장자는 스니퍼가 준 값만 오지만, 경로가 될 수 있는 문자는 여기서 한 번 더 막는다. */
    private static String safeExtension(String extension) {
        if (extension == null || extension.isBlank()
                || extension.indexOf('.') >= 0
                || extension.indexOf('/') >= 0
                || extension.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("확장자가 올바르지 않습니다: " + extension);
        }
        return extension;
    }
}
