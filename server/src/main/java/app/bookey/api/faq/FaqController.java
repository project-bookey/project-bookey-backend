package app.bookey.api.faq;

import app.bookey.api.faq.dto.FaqDtos.FaqView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 로그인 전에도 읽을 수 있다(SecurityConfig permitAll) — '로그인이 안 돼요' 같은 질문을 보여 주려고. */
@Tag(name = "FAQ", description = "자주 묻는 질문")
@RestController
@RequestMapping("/api/v1/faqs")
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;

    @Operation(summary = "FAQ 목록 — 노출 중인 것만, 정렬 순")
    @GetMapping
    public List<FaqView> list() {
        return faqService.visible();
    }
}
