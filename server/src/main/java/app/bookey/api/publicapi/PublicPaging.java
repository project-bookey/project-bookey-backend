package app.bookey.api.publicapi;

import org.springframework.data.domain.PageRequest;

/**
 * 비회원 호출의 page·size 를 안전한 범위로 묶는다 — 음수 page·0 size 가 500 으로 새지 않게
 * (ReviewCommentController 와 같은 규칙). 비회원 목록은 한 쪽에 최대 50건이다.
 */
final class PublicPaging {

    static final int MAX_PAGE_SIZE = 50;

    private PublicPaging() {
    }

    static PageRequest of(int page, int size) {
        return of(page, size, MAX_PAGE_SIZE);
    }

    static PageRequest of(int page, int size, int maxSize) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, maxSize));
    }

    /** 목록 길이 하나만 받는 엔드포인트의 size — 1 아래·max 위로 가지 않게. */
    static int size(int size, int maxSize) {
        return Math.clamp(size, 1, maxSize);
    }
}
