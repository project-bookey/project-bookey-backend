package app.bookey.api.inquiry;

import app.bookey.api.inquiry.dto.InquiryDtos.CreateInquiryRequest;
import app.bookey.api.inquiry.dto.InquiryDtos.InquiryCategoryView;
import app.bookey.api.inquiry.dto.InquiryDtos.InquirySummaryView;
import app.bookey.api.inquiry.dto.InquiryDtos.InquiryView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.PageResponse;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.inquiry.Inquiry;
import app.bookey.domain.inquiry.InquiryCategory;
import app.bookey.domain.inquiry.InquiryImage;
import app.bookey.domain.inquiry.InquiryImageRepository;
import app.bookey.domain.inquiry.InquiryRepository;
import app.bookey.domain.inquiry.InquiryRules;
import app.bookey.domain.inquiry.InquiryStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 고객문의(1:1) — 사용자 쪽. 답변은 어드민({@code AdminInquiryService})이 단다. */
@Service
@RequiredArgsConstructor
public class InquiryService {

    /** 도배 방지 — 1시간에 5건. 대기 중 상한({@link InquiryRules#MAX_PENDING})은 DB 로 따로 지킨다. */
    static final int CREATE_RATE_LIMIT = 5;
    static final int MAX_PAGE_SIZE = 50;

    private final InquiryRepository inquiryRepository;
    private final InquiryImageRepository imageRepository;
    private final RateLimiter rateLimiter;

    public List<InquiryCategoryView> categories() {
        return Arrays.stream(InquiryCategory.values()).map(InquiryCategoryView::from).toList();
    }

    @Transactional
    public InquiryView create(Long userId, CreateInquiryRequest request) {
        rateLimiter.require("inquiry:create:" + userId, CREATE_RATE_LIMIT, Duration.ofHours(1));
        List<Long> imageIds = InquiryRules.normalizeImageIds(request.imageIds());
        InquiryRules.requirePendingBelowLimit(inquiryRepository.countByUserIdAndStatus(userId, InquiryStatus.WAITING));
        List<InquiryImage> found = imageIds.isEmpty() ? List.of() : imageRepository.findAllById(imageIds);
        InquiryRules.validateAttachments(userId, imageIds, found);

        Inquiry inquiry = inquiryRepository.save(Inquiry.builder()
                .userId(userId)
                .category(request.category())
                .body(request.body().strip())
                .appVersion(InquiryRules.clip(request.appVersion(), 30))
                .platform(InquiryRules.clip(request.platform(), 20))
                .osVersion(InquiryRules.clip(request.osVersion(), 30))
                .deviceModel(InquiryRules.clip(request.deviceModel(), 100))
                .build());

        // 요청 순서대로 붙인다 — 첫 사진이 맨 앞에 보인다. 조건부 UPDATE 라 그사이 다른 문의에 붙었거나
        // 정리 배치가 지운 사진이면 0 행이 나오고, 거절하면 문의 저장까지 함께 되돌린다.
        Map<Long, InquiryImage> byId = found.stream().collect(Collectors.toMap(InquiryImage::getId, Function.identity()));
        List<InquiryImage> attached = new ArrayList<>(imageIds.size());
        for (int order = 0; order < imageIds.size(); order++) {
            Long imageId = imageIds.get(order);
            if (imageRepository.attachIfDetached(imageId, userId, inquiry.getId(), (short) order) != 1) {
                throw ApiException.of(ErrorCode.INQUIRY_IMAGE_NOT_FOUND);
            }
            attached.add(byId.get(imageId));
        }
        return InquiryView.from(inquiry, attached);
    }

    @Transactional(readOnly = true)
    public PageResponse<InquirySummaryView> myList(Long userId, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("id")));
        return PageResponse.of(inquiryRepository.findAllByUserId(userId, pageable), InquirySummaryView::from);
    }

    @Transactional(readOnly = true)
    public InquiryView detail(Long userId, Long inquiryId) {
        Inquiry inquiry = owned(userId, inquiryId);
        return InquiryView.from(inquiry, imageRepository.findAllByInquiryIdOrderBySortOrderAscIdAsc(inquiry.getId()));
    }

    /** 답변 전후 상관없이 실제로 지운다 — 본문과 사진은 사용자의 개인정보다. 사진은 FK 로 떨어져 정리 배치가 회수한다. */
    @Transactional
    public void delete(Long userId, Long inquiryId) {
        inquiryRepository.delete(owned(userId, inquiryId));
    }

    /** 남의 문의는 없는 것과 같게 다룬다 — 있는지조차 알리지 않는다. */
    private Inquiry owned(Long userId, Long inquiryId) {
        return inquiryRepository.findById(inquiryId)
                .filter(inquiry -> inquiry.isOwnedBy(userId))
                .orElseThrow(() -> ApiException.of(ErrorCode.INQUIRY_NOT_FOUND));
    }
}
