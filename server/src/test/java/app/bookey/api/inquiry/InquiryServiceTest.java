package app.bookey.api.inquiry;

import app.bookey.api.inquiry.dto.InquiryDtos.CreateInquiryRequest;
import app.bookey.api.inquiry.dto.InquiryDtos.InquiryView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.inquiry.Inquiry;
import app.bookey.domain.inquiry.InquiryCategory;
import app.bookey.domain.inquiry.InquiryImage;
import app.bookey.domain.inquiry.InquiryImageRepository;
import app.bookey.domain.inquiry.InquiryRepository;
import app.bookey.domain.inquiry.InquiryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 리포지토리는 Mockito, RateLimiter 는 호출만 기록하는 페이크(PostImageServiceTest 선례). */
class InquiryServiceTest {

    private static final long USER_ID = 10L;

    private static final class RecordingRateLimiter extends RateLimiter {
        final List<String> keys = new ArrayList<>();
        int limit;
        Duration window;

        RecordingRateLimiter() {
            super(null);
        }

        @Override
        public boolean tryAcquire(String key, int limit, Duration window) {
            keys.add(key);
            this.limit = limit;
            this.window = window;
            return true;
        }
    }

    private final InquiryRepository inquiryRepository = mock(InquiryRepository.class);
    private final InquiryImageRepository imageRepository = mock(InquiryImageRepository.class);
    private final RecordingRateLimiter rateLimiter = new RecordingRateLimiter();
    private final InquiryService service = new InquiryService(inquiryRepository, imageRepository, rateLimiter);

    private static CreateInquiryRequest request(List<Long> imageIds) {
        return new CreateInquiryRequest(InquiryCategory.BUG, "  타이머가 멈춰요  ", imageIds,
                "1.2.0", "IOS", "18.1", "iPhone 15");
    }

    private static InquiryImage image(long id, long userId) {
        InquiryImage image = InquiryImage.builder()
                .userId(userId).storageKey("k" + id).url("http://x/k" + id).contentType("image/png").byteSize(1)
                .build();
        set(image, "id", id);
        return image;
    }

    private static Inquiry inquiry(long id, long userId) {
        Inquiry inquiry = Inquiry.builder().userId(userId).category(InquiryCategory.USAGE).body("문의").build();
        set(inquiry, "id", id);
        set(inquiry, "createdAt", Instant.parse("2026-10-03T00:00:00Z"));
        return inquiry;
    }

    /** id 는 엔티티 자신에, createdAt 은 BaseTimeEntity 에 있어 상위 클래스까지 올라가며 찾는다. */
    private static void set(Object target, String field, Object value) {
        try {
            Class<?> type = target.getClass();
            while (type != null) {
                try {
                    Field f = type.getDeclaredField(field);
                    f.setAccessible(true);
                    f.set(target, value);
                    return;
                } catch (NoSuchFieldException e) {
                    type = type.getSuperclass();
                }
            }
            throw new NoSuchFieldException(field);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void stubSaveAssigningId(long id) {
        when(inquiryRepository.save(any())).thenAnswer(invocation -> {
            Inquiry inquiry = invocation.getArgument(0);
            set(inquiry, "id", id);
            set(inquiry, "createdAt", Instant.parse("2026-10-03T00:00:00Z"));
            return inquiry;
        });
    }

    @Test
    @DisplayName("문의를 남기면 본문을 다듬고 기기 정보를 저장하며, 사진을 요청 순서대로 붙인다")
    void createAttachesImagesInOrder() {
        InquiryImage first = image(1L, USER_ID);
        InquiryImage second = image(2L, USER_ID);
        when(imageRepository.findAllById(List.of(2L, 1L))).thenReturn(List.of(first, second));
        when(imageRepository.attachIfDetached(any(), any(), any(), anyShort())).thenReturn(1);
        stubSaveAssigningId(50L);

        InquiryView view = service.create(USER_ID, request(List.of(2L, 1L)));

        ArgumentCaptor<Inquiry> saved = ArgumentCaptor.forClass(Inquiry.class);
        verify(inquiryRepository).save(saved.capture());
        assertThat(saved.getValue().getBody()).isEqualTo("타이머가 멈춰요");
        assertThat(saved.getValue().getAppVersion()).isEqualTo("1.2.0");
        assertThat(saved.getValue().getPlatform()).isEqualTo("IOS");
        assertThat(saved.getValue().getDeviceModel()).isEqualTo("iPhone 15");
        verify(imageRepository).attachIfDetached(2L, USER_ID, 50L, (short) 0);
        verify(imageRepository).attachIfDetached(1L, USER_ID, 50L, (short) 1);
        assertThat(view.images()).extracting("id").containsExactly(2L, 1L);
        assertThat(view.status()).isEqualTo(InquiryStatus.WAITING);
        assertThat(view.categoryLabel()).isEqualTo("오류 신고");
    }

    @Test
    @DisplayName("문의 남기기는 사용자별로 1시간에 5건까지다")
    void createIsRateLimited() {
        stubSaveAssigningId(50L);

        service.create(USER_ID, request(null));

        assertThat(rateLimiter.keys).containsExactly("inquiry:create:" + USER_ID);
        assertThat(rateLimiter.limit).isEqualTo(5);
        assertThat(rateLimiter.window).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("답변 대기 문의가 5건이면 저장하지 않고 INQUIRY_PENDING_LIMIT")
    void createRejectsWhenTooManyPending() {
        when(inquiryRepository.countByUserIdAndStatus(USER_ID, InquiryStatus.WAITING)).thenReturn(5L);

        assertThatThrownBy(() -> service.create(USER_ID, request(null)))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INQUIRY_PENDING_LIMIT);
        verify(inquiryRepository, never()).save(any());
    }

    @Test
    @DisplayName("남의 사진을 붙이려 하면 저장하지 않고 INQUIRY_IMAGE_NOT_FOUND")
    void createRejectsForeignImage() {
        when(imageRepository.findAllById(List.of(1L))).thenReturn(List.of(image(1L, 99L)));

        assertThatThrownBy(() -> service.create(USER_ID, request(List.of(1L))))
                .extracting("errorCode").isEqualTo(ErrorCode.INQUIRY_IMAGE_NOT_FOUND);
        verify(inquiryRepository, never()).save(any());
    }

    @Test
    @DisplayName("검증 뒤 그사이 다른 문의에 붙었거나 정리된 사진이면(붙인 행 0) INQUIRY_IMAGE_NOT_FOUND — 덮어쓰지 않는다")
    void createRejectsImageAttachedConcurrently() {
        when(imageRepository.findAllById(List.of(1L))).thenReturn(List.of(image(1L, USER_ID)));
        when(imageRepository.attachIfDetached(any(), any(), any(), anyShort())).thenReturn(0);
        stubSaveAssigningId(50L);

        assertThatThrownBy(() -> service.create(USER_ID, request(List.of(1L))))
                .extracting("errorCode").isEqualTo(ErrorCode.INQUIRY_IMAGE_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 문의는 보기·지우기 모두 INQUIRY_NOT_FOUND — 있는지조차 알리지 않는다")
    void foreignInquiryIsNotFound() {
        when(inquiryRepository.findById(5L)).thenReturn(Optional.of(inquiry(5L, 99L)));

        assertThatThrownBy(() -> service.detail(USER_ID, 5L))
                .extracting("errorCode").isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
        assertThatThrownBy(() -> service.delete(USER_ID, 5L))
                .extracting("errorCode").isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
        verify(inquiryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("답변을 받은 문의도 지울 수 있다")
    void answeredInquiryCanBeDeleted() {
        Inquiry inquiry = inquiry(5L, USER_ID);
        inquiry.answer(1L, "답변", Instant.parse("2026-10-03T01:00:00Z"));
        when(inquiryRepository.findById(5L)).thenReturn(Optional.of(inquiry));

        service.delete(USER_ID, 5L);

        verify(inquiryRepository).delete(inquiry);
    }

    @Test
    @DisplayName("유형은 선언 순서대로 내려가고 맨 앞이 '이용 문의'다")
    void categoriesInDeclaredOrder() {
        assertThat(service.categories()).extracting("code").first().isEqualTo(InquiryCategory.USAGE);
        assertThat(service.categories()).extracting("label").contains("이용 문의", "오류 신고", "기타");
    }
}
