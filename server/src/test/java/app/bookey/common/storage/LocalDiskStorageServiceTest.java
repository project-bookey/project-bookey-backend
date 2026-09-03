package app.bookey.common.storage;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spring 컨텍스트 없이 값만 주입해 검증한다. */
class LocalDiskStorageServiceTest {

    private static final String KEY = "posts/1/2026/09/abc.png";

    private static InputStream content(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private LocalDiskStorageService service(Path dir, String publicBaseUrl) {
        return new LocalDiskStorageService(dir.toString(), publicBaseUrl, "8098");
    }

    @Test
    @DisplayName("저장하면 키 경로에 파일이 생기고 공개 URL 을 돌려준다")
    void storesFileAndReturnsUrl(@TempDir Path dir) {
        LocalDiskStorageService service = service(dir, "http://cdn.example");

        String url = service.store(KEY, content("사진"), 6, "image/png");

        assertThat(url).isEqualTo("http://cdn.example/uploads/" + KEY);
        assertThat(dir.resolve(KEY)).exists();
        assertThat(dir.resolve(KEY)).hasContent("사진");
    }

    @Test
    @DisplayName("공개 베이스 URL 의 끝 슬래시는 무시한다")
    void trimsTrailingSlashFromBase(@TempDir Path dir) {
        String url = service(dir, "http://cdn.example/").store(KEY, content("x"), 1, "image/png");

        assertThat(url).isEqualTo("http://cdn.example/uploads/" + KEY);
    }

    @Test
    @DisplayName("공개 베이스 URL 에 /uploads 가 이미 있어도 경로를 중복해 붙이지 않는다")
    void doesNotDuplicateUploadsSegment(@TempDir Path dir) {
        assertThat(service(dir, "http://cdn.example/uploads").store(KEY, content("x"), 1, "image/png"))
                .isEqualTo("http://cdn.example/uploads/" + KEY);
        assertThat(service(dir, "http://cdn.example/uploads/").store(KEY, content("x"), 1, "image/png"))
                .isEqualTo("http://cdn.example/uploads/" + KEY);
    }

    @Test
    @DisplayName("공개 베이스 URL 이 비어 있고 요청 컨텍스트도 없으면 localhost 포트로 폴백한다")
    void fallsBackToLocalhostWhenBaseIsBlank(@TempDir Path dir) {
        assertThat(service(dir, "").store(KEY, content("x"), 1, "image/png"))
                .isEqualTo("http://localhost:8098/uploads/" + KEY);
        assertThat(service(dir, null).store(KEY, content("x"), 1, "image/png"))
                .isEqualTo("http://localhost:8098/uploads/" + KEY);
    }

    @Test
    @DisplayName("루트 밖으로 나가는 키는 저장하지 않고 STORAGE_ERROR 를 던진다")
    void rejectsPathTraversalKey(@TempDir Path dir) {
        LocalDiskStorageService service = service(dir, "http://cdn.example");

        assertThatThrownBy(() -> service.store("../evil.png", content("x"), 1, "image/png"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.STORAGE_ERROR);
        assertThatThrownBy(() -> service.store("posts/../../evil.png", content("x"), 1, "image/png"))
                .isInstanceOf(ApiException.class);
        assertThat(dir.getParent().resolve("evil.png")).doesNotExist();
    }

    @Test
    @DisplayName("절대경로 키는 저장·삭제 모두 거부한다 — 업로드 루트 밖을 가리킨다")
    void rejectsAbsolutePathKey(@TempDir Path dir) {
        LocalDiskStorageService service = service(dir, "http://cdn.example");

        assertThatThrownBy(() -> service.store("/etc/x", content("x"), 1, "image/png"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.STORAGE_ERROR);
        assertThatThrownBy(() -> service.delete("/etc/x"))
                .isInstanceOf(ApiException.class);

        // POSIX 에서 "C:\x" 는 백슬래시가 들어간 평범한 파일 이름이라 루트 안에 들어온다 — 절대경로인 곳에서만 검증한다
        if (Path.of("C:\\x").isAbsolute()) {
            assertThatThrownBy(() -> service.store("C:\\x", content("x"), 1, "image/png"))
                    .isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> service.delete("C:\\x"))
                    .isInstanceOf(ApiException.class);
        }
    }

    @Test
    @DisplayName("루트 밖으로 나가는 키는 삭제도 거부한다")
    void rejectsPathTraversalOnDelete(@TempDir Path dir) {
        LocalDiskStorageService service = service(dir, "http://cdn.example");

        assertThatThrownBy(() -> service.delete("../evil.png"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("저장한 파일은 지우고, 없는 키를 지워도 조용히 넘어간다")
    void deletesQuietly(@TempDir Path dir) throws Exception {
        LocalDiskStorageService service = service(dir, "http://cdn.example");
        service.store(KEY, content("x"), 1, "image/png");

        service.delete(KEY);
        assertThat(dir.resolve(KEY)).doesNotExist();

        service.delete("posts/1/2026/09/없는파일.png");   // 예외 없이 끝나야 한다
        assertThat(Files.exists(dir)).isTrue();
    }

    @Test
    @DisplayName("설정한 디렉터리가 없으면 만들어 둔다")
    void createsRootDirectory(@TempDir Path dir) {
        Path root = dir.resolve("uploads/nested");

        service(root, "http://cdn.example");

        assertThat(Files.isDirectory(root)).isTrue();
    }
}
