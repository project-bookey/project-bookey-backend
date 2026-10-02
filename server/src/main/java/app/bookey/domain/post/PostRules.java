package app.bookey.domain.post;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;

import java.util.List;
import java.util.Set;

/**
 * 독후감 본문·공개 범위 규칙 — Spring 없이 단위 테스트하는 순수 규칙.
 *
 * <ul>
 *   <li>모임 밖 글은 PUBLIC·LINK·PRIVATE, 모임 글은 PUBLIC(모임+광장)·CLUB(모임만)</li>
 *   <li>본문 필수, 사진 10장까지</li>
 * </ul>
 */
public final class PostRules {

    public static final int MAX_IMAGES = 10;

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
     * 본문·사진 수를 검사한다. 작성 때는 전부, 수정 때는 보낸 값만(null = 유지) 넘긴다.
     *
     * @param creating 작성이면 true — 본문이 필수가 된다
     */
    public static void requireContent(boolean creating, String bodyMd, List<Long> imageIds) {
        if (creating || bodyMd != null) {
            if (bodyMd == null || bodyMd.isBlank()) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "본문을 입력해 주세요.");
            }
        }
        requireCount(imageIds, MAX_IMAGES, "사진은 " + MAX_IMAGES + "장까지 붙일 수 있습니다.");
    }

    private static void requireCount(List<Long> ids, int max, String message) {
        if (ids != null && ids.size() > max) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, message);
        }
    }
}
