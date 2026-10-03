package app.bookey.api.faq;

import app.bookey.api.faq.dto.FaqDtos.FaqAdminView;
import app.bookey.api.faq.dto.FaqDtos.FaqUpsertRequest;
import app.bookey.api.faq.dto.FaqDtos.FaqView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.faq.Faq;
import app.bookey.domain.faq.FaqOrdering;
import app.bookey.domain.faq.FaqRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/** 자주 묻는 질문 — 앱은 노출 목록만 읽고, 어드민이 더하고 고치고 순서를 정한다. */
@Service
@RequiredArgsConstructor
public class FaqService {

    private final FaqRepository faqRepository;

    @Transactional(readOnly = true)
    public List<FaqView> visible() {
        return faqRepository.findAllByVisibleTrueOrderBySortOrderAscIdAsc().stream().map(FaqView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<FaqAdminView> adminList() {
        return faqRepository.findAllByOrderBySortOrderAscIdAsc().stream().map(FaqAdminView::from).toList();
    }

    /** 새 FAQ 는 맨 뒤에 붙인다. */
    @Transactional
    public FaqAdminView create(FaqUpsertRequest request) {
        Faq faq = faqRepository.save(Faq.builder()
                .category(request.category())
                .question(request.question().strip())
                .answer(request.answer().strip())
                .sortOrder(faqRepository.maxSortOrder() + 1)
                .visible(request.visible())
                .build());
        return FaqAdminView.from(faq);
    }

    @Transactional
    public FaqAdminView update(Long id, FaqUpsertRequest request) {
        Faq faq = find(id);
        faq.update(request.category(), request.question().strip(), request.answer().strip(), request.visible());
        return FaqAdminView.from(faq);
    }

    @Transactional
    public void delete(Long id) {
        faqRepository.delete(find(id));
    }

    @Transactional
    public List<FaqAdminView> reorder(List<Long> ids) {
        List<Faq> all = faqRepository.findAllByOrderBySortOrderAscIdAsc();
        FaqOrdering.apply(all, ids);
        return all.stream()
                .sorted(Comparator.comparingInt(Faq::getSortOrder))
                .map(FaqAdminView::from)
                .toList();
    }

    private Faq find(Long id) {
        return faqRepository.findById(id).orElseThrow(() -> ApiException.of(ErrorCode.FAQ_NOT_FOUND));
    }
}
