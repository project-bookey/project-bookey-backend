package app.bookey.domain.post;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 독후감 형식·공개 범위 규칙 — Spring 없이 단위 테스트하는 순수 규칙.
 *
 * <ul>
 *   <li>모임 밖 글은 PUBLIC·LINK·PRIVATE, 모임 글은 PUBLIC(모임+광장)·CLUB(모임만)</li>
 *   <li>TEXT: 본문 필수, 문서 없음, 사진 10장까지</li>
 *   <li>NOTE: 문서 필수({@code pages} 배열 1~6장, 직렬화 1MB 이하), 본문은 빈 문자열 허용, 사진 30장까지</li>
 *   <li>오려둔 문장은 형식과 관계없이 10개까지</li>
 * </ul>
 * 문서 내용은 앱이 소유한 스키마라 해석하지 않는다.
 */
public final class PostRules {

    public static final int MAX_NOTE_PAGES = 6;
    public static final int MAX_DOCUMENT_BYTES = 1024 * 1024;
    public static final int MAX_TEXT_IMAGES = 10;
    public static final int MAX_NOTE_IMAGES = 30;
    public static final int MAX_QUOTES = 10;

    private static final Set<PostVisibility> CLUB_VISIBILITIES = Set.of(PostVisibility.PUBLIC, PostVisibility.CLUB);
    private static final Set<PostVisibility> PLAIN_VISIBILITIES =
            Set.of(PostVisibility.PUBLIC, PostVisibility.LINK, PostVisibility.PRIVATE);

    private PostRules() {
    }

    /** 모임 글이면 PUBLIC·CLUB, 아니면 PUBLIC·LINK·PRIVATE. null 은 호출자가 걸러낸다(수정 때 null = 유지). */
    public static void requireVisibility(boolean clubPost, PostVisibility visibility) {
        if (visibility == null) {
            return;
        }
        if (clubPost && !CLUB_VISIBILITIES.contains(visibility)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "모임 독후감은 '모임+광장 공개'나 '모임만 공개'로만 올릴 수 있습니다.");
        }
        if (!clubPost && !PLAIN_VISIBILITIES.contains(visibility)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "모임 공개는 모임 안에서 쓴 독후감에만 쓸 수 있습니다.");
        }
    }

    /**
     * 본문·문서·첨부 수를 형식에 맞춰 검사한다. 작성 때는 전부, 수정 때는 보낸 값만(null = 유지) 넘긴다.
     *
     * @param creating 작성이면 true — NOTE 문서가 필수가 된다
     */
    public static void requireContent(PostFormat format, boolean creating, String bodyMd,
                                      Map<String, Object> document, List<Long> imageIds, List<Long> quoteIds) {
        if (format == PostFormat.NOTE) {
            if (document == null) {
                if (creating) {
                    throw new ApiException(ErrorCode.INVALID_REQUEST, "노트 독후감에는 노트 문서가 필요합니다.");
                }
            } else {
                requireDocumentShape(document);
            }
            requireCount(imageIds, MAX_NOTE_IMAGES, "노트 독후감의 사진은 " + MAX_NOTE_IMAGES + "장까지 붙일 수 있습니다.");
        } else {
            if (creating || bodyMd != null) {
                if (bodyMd == null || bodyMd.isBlank()) {
                    throw new ApiException(ErrorCode.INVALID_REQUEST, "본문을 입력해 주세요.");
                }
            }
            if (document != null) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "글 독후감에는 노트 문서를 붙일 수 없습니다.");
            }
            requireCount(imageIds, MAX_TEXT_IMAGES, "사진은 " + MAX_TEXT_IMAGES + "장까지 붙일 수 있습니다.");
        }
        requireCount(quoteIds, MAX_QUOTES, "오려둔 문장은 " + MAX_QUOTES + "개까지 붙일 수 있습니다.");
    }

    /** 문서는 객체이고 pages 배열이 1~6장이어야 한다. 페이지 내부는 보지 않는다. */
    public static void requireDocumentShape(Map<String, Object> document) {
        if (!(document.get("pages") instanceof List<?> pages)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "노트 문서에 pages 배열이 필요합니다.");
        }
        if (pages.isEmpty() || pages.size() > MAX_NOTE_PAGES) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "노트는 1~" + MAX_NOTE_PAGES + "페이지여야 합니다.");
        }
    }

    /** 직렬화한 문서 크기(바이트)가 1MB 를 넘으면 거절한다. */
    public static void requireDocumentSize(int bytes) {
        if (bytes > MAX_DOCUMENT_BYTES) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "노트 내용이 너무 큽니다(1MB 이하). 요소를 줄여 주세요.");
        }
    }

    private static void requireCount(List<Long> ids, int max, String message) {
        if (ids != null && ids.size() > max) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, message);
        }
    }
}
