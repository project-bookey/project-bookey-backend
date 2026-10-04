package app.bookey.api.legal;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.legal.LegalDocument;
import jakarta.validation.constraints.NotNull;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 약관·정책 원문 — classpath:legal/{key}.txt 를 한 번 읽어 둔다(문구는 배포와 함께만 바뀐다). */
@Service
public class LegalDocumentService {

    public record LegalDocumentView(
            @NotNull String key,
            @NotNull String title,
            @NotNull String version,
            @NotNull LocalDate effectiveDate,
            /** 줄글 원문 — '제1조 (목적)'·'1. 회원 관리' 같은 줄이 소제목이다. */
            @NotNull String body
    ) {}

    private final Map<LegalDocument, String> bodies = new ConcurrentHashMap<>();

    /** 공개된 문서만 — 초안·없는 키는 NOT_FOUND. */
    public LegalDocumentView view(String key) {
        LegalDocument document = LegalDocument.fromKey(key)
                .filter(LegalDocument::isPublished)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        return new LegalDocumentView(document.getKey(), document.getTitle(), document.getVersion(),
                document.effectiveDate(), body(document));
    }

    public String body(LegalDocument document) {
        return bodies.computeIfAbsent(document, LegalDocumentService::read);
    }

    private static String read(LegalDocument document) {
        try (InputStream in = new ClassPathResource(document.resourcePath()).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException("법적 문서 원문을 읽지 못했습니다: " + document.resourcePath(), e);
        }
    }
}
