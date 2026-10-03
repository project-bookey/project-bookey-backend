package app.bookey.api.faq;

import app.bookey.api.faq.dto.FaqDtos.FaqUpsertRequest;
import app.bookey.api.faq.dto.FaqDtos.FaqView;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.faq.Faq;
import app.bookey.domain.faq.FaqRepository;
import app.bookey.domain.inquiry.InquiryCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FaqServiceTest {

    private final FaqRepository faqRepository = mock(FaqRepository.class);
    private final FaqService service = new FaqService(faqRepository);

    @Test
    @DisplayName("새 FAQ 는 맨 뒤(가장 큰 순서 + 1)에 붙고, 비어 있으면 0번이다")
    void createAppendsAtEnd() {
        when(faqRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(faqRepository.maxSortOrder()).thenReturn(-1, 4);

        service.create(new FaqUpsertRequest(InquiryCategory.ACCOUNT, " 로그인이 안 돼요 ", " 비밀번호 찾기를 써 보세요 ", true));
        service.create(new FaqUpsertRequest(InquiryCategory.USAGE, "Q", "A", false));

        ArgumentCaptor<Faq> saved = ArgumentCaptor.forClass(Faq.class);
        verify(faqRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getSortOrder()).isZero();
        assertThat(saved.getAllValues().get(0).getQuestion()).isEqualTo("로그인이 안 돼요");
        assertThat(saved.getAllValues().get(1).getSortOrder()).isEqualTo(5);
        assertThat(saved.getAllValues().get(1).isVisible()).isFalse();
    }

    @Test
    @DisplayName("앱 목록은 노출 중인 FAQ 만, 분류 라벨과 함께 내려준다")
    void visibleCarriesCategoryLabel() {
        Faq faq = Faq.builder().category(InquiryCategory.PAYMENT).question("환불되나요?").answer("네").visible(true).build();
        when(faqRepository.findAllByVisibleTrueOrderBySortOrderAscIdAsc()).thenReturn(List.of(faq));

        List<FaqView> views = service.visible();

        assertThat(views).singleElement().satisfies(view -> {
            assertThat(view.categoryLabel()).isEqualTo("결제·구독");
            assertThat(view.question()).isEqualTo("환불되나요?");
        });
    }

    @Test
    @DisplayName("없는 FAQ 를 고치면 FAQ_NOT_FOUND")
    void updateMissing() {
        when(faqRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(9L, new FaqUpsertRequest(InquiryCategory.ETC, "Q", "A", true)))
                .extracting("errorCode").isEqualTo(ErrorCode.FAQ_NOT_FOUND);
    }
}
