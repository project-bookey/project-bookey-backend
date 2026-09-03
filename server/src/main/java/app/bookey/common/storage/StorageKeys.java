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
