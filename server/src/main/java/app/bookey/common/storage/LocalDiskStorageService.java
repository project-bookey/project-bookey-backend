package app.bookey.common.storage;

import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 개발·단일 인스턴스 운영용 로컬 디스크 저장소.
 * 저장한 파일은 StaticUploadsConfig 가 /uploads/** 로 그대로 서빙한다.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "bookey.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalDiskStorageService implements StorageService {

    private static final String URL_PREFIX = "/uploads/";

    private final Path root;
    private final String configuredBase;
    private final String fallbackPort;

    @Autowired
    public LocalDiskStorageService(BookeyProperties properties, Environment environment) {
        this(properties.storage().local().dir(),
                properties.storage().local().publicBaseUrl(),
                environment.getProperty("server.port", "8080"));
    }

    /** 테스트용 — Spring 컨텍스트 없이 값만 넣는다. */
    LocalDiskStorageService(String dir, String publicBaseUrl, String fallbackPort) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        this.configuredBase = normalizeBase(publicBaseUrl);
        this.fallbackPort = fallbackPort;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 디렉터리를 만들지 못했습니다: " + root, e);
        }
        log.info("로컬 업로드 저장소: {}", root);
    }

    @Override
    public String store(String key, InputStream in, long size, String contentType) {
        Path target = resolveWithin(root, key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("업로드 파일 저장 실패: key={}", key, e);
            throw ApiException.of(ErrorCode.STORAGE_ERROR);
        }
        return publicUrl(publicBase(), key);
    }

    @Override
    public void delete(String key) {
        Path target = resolveWithin(root, key);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("업로드 파일 삭제 실패(무시): key={}", key, e);
        }
    }

    /** 설정값이 있으면 그대로, 없으면 요청 origin — 요청 밖(배치 등)에서는 localhost 로 폴백한다. */
    private String publicBase() {
        if (!configuredBase.isEmpty()) {
            return configuredBase;
        }
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        } catch (IllegalStateException e) {
            return "http://localhost:" + fallbackPort;
        }
    }

    // ── 순수 로직 (테스트 대상) ────────────────────────────────────────────

    /**
     * 설정된 공개 베이스를 origin 형태로 다듬는다.
     * 끝 슬래시를 없애고, 실수로 /uploads 까지 적어 둔 경우도 잘라 URL 이 중복되지 않게 한다.
     */
    static String normalizeBase(String publicBaseUrl) {
        if (publicBaseUrl == null) {
            return "";
        }
        String base = publicBaseUrl.trim();
        base = stripTrailingSlashes(base);
        if (base.endsWith("/uploads")) {
            base = stripTrailingSlashes(base.substring(0, base.length() - "/uploads".length()));
        }
        return base;
    }

    private static String stripTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    static String publicUrl(String base, String key) {
        return base + URL_PREFIX + key;
    }

    /** 키가 루트 밖을 가리키면(../ 등) 저장·삭제 모두 거부한다. */
    static Path resolveWithin(Path root, String key) {
        if (key == null || key.isBlank()) {
            throw ApiException.of(ErrorCode.STORAGE_ERROR);
        }
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw ApiException.of(ErrorCode.STORAGE_ERROR);
        }
        return target;
    }
}
