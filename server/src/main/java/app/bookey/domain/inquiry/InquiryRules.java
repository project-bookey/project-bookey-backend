package app.bookey.domain.inquiry;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 고객문의 규칙 — 상한값과 검증·미리보기·정렬. 스프링 컨텍스트 없이 단위 테스트한다. */
public final class InquiryRules {

    /** 문의 하나에 붙일 수 있는 사진 장수. */
    public static final int MAX_IMAGES = 3;
    /** 본문 길이 — 자바 String 길이(UTF-16) 기준이라 앱의 글자 수 표시와 같다. */
    public static final int MAX_BODY = 2000;
    public static final int MAX_ANSWER = 5000;
    /** 답변을 기다리는 문의를 이만큼 쌓아 두면 새 문의를 받지 않는다 — 도배 방지(Redis 가 죽어도 DB 로 지킨다). */
    public static final int MAX_PENDING = 5;
    static final int PREVIEW_LENGTH = 80;

    private InquiryRules() {
    }

    /** null 을 빼고 중복은 첫 것만 남긴다. 상한을 넘으면 거절한다. */
    public static List<Long> normalizeImageIds(List<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = imageIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.size() > MAX_IMAGES) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "사진은 " + MAX_IMAGES + "장까지 붙일 수 있어요.");
        }
        return ids;
    }

    public static void requirePendingBelowLimit(long waiting) {
        if (waiting >= MAX_PENDING) {
            throw ApiException.of(ErrorCode.INQUIRY_PENDING_LIMIT);
        }
    }

    /**
     * 요청한 사진이 모두 있고, 전부 내 것이며, 다른 문의에 붙어 있지 않아야 한다.
     * 세 경우 모두 INQUIRY_IMAGE_NOT_FOUND — 없는 것·남의 것·이미 붙은 것을 구분해 알리지 않는다.
     * 문의는 고칠 수 없으니 독후감과 달리 '같은 글에 이미 붙은 사진' 예외가 없다.
     */
    public static void validateAttachments(Long userId, List<Long> requestedIds, List<InquiryImage> found) {
        Set<Long> foundIds = found.stream().map(InquiryImage::getId).collect(Collectors.toSet());
        if (!foundIds.containsAll(requestedIds)) {
            throw ApiException.of(ErrorCode.INQUIRY_IMAGE_NOT_FOUND);
        }
        for (InquiryImage image : found) {
            if (!image.isOwnedBy(userId) || !image.isDetached()) {
                throw ApiException.of(ErrorCode.INQUIRY_IMAGE_NOT_FOUND);
            }
        }
    }

    /** 목록용 한 줄 — 줄바꿈·연속 공백을 한 칸으로 접고 코드포인트 기준으로 자른다(이모지가 반으로 갈리지 않게). */
    public static String preview(String body) {
        if (body == null) {
            return "";
        }
        String flat = body.strip().replaceAll("\\s+", " ");
        if (flat.codePointCount(0, flat.length()) <= PREVIEW_LENGTH) {
            return flat;
        }
        return flat.substring(0, flat.offsetByCodePoints(0, PREVIEW_LENGTH)) + "…";
    }

    /** 어드민 정렬 — 답변 대기만 볼 때는 오래 기다린 것부터, 그 밖에는 최신순. */
    public static Sort adminSort(InquiryStatus filter) {
        return filter == InquiryStatus.WAITING
                ? Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))
                : Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    }

    /**
     * 앱이 붙여 보내는 기기 정보는 검증으로 거절하지 않고 칸 길이에 맞춰 자른다 — 그 때문에 문의가 막히면 안 된다.
     * VARCHAR(n) 은 글자(코드포인트) 수 기준이라 코드포인트로 자른다.
     */
    public static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.codePointCount(0, trimmed.length()) <= max
                ? trimmed
                : trimmed.substring(0, trimmed.offsetByCodePoints(0, max));
    }
}
