package app.bookey.api.legal;

import app.bookey.api.legal.LegalDocumentService.LegalDocumentView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 약관·정책 원문 — 로그인 전에도 읽는다(/api/v1/public/** permitAll).
 * 앱 가입 화면과 웹 정책 페이지(/legal/index.html)가 같은 원문을 쓴다.
 */
@Tag(name = "Legal", description = "약관·정책 원문")
@RestController
@RequestMapping("/api/v1/public/legal")
@RequiredArgsConstructor
public class LegalController {

    private final LegalDocumentService documentService;

    @Operation(summary = "약관·정책 원문 — key: terms, privacy-consent, profile-optional, marketing, privacy-policy, refund")
    @GetMapping("/{key}")
    public LegalDocumentView document(@PathVariable String key) {
        return documentService.view(key);
    }
}
