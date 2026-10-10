package app.bookey.api.book;

import app.bookey.api.book.client.Yes24Client;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.book.BookPageSuggestionRepository;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.curation.EditorPickRepository;
import app.bookey.domain.like.BookLikeRepository;
import app.bookey.domain.reading.ReadingRecordRepository;
import app.bookey.domain.review.ReviewRepository;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 비회원 검색(공개 웹) — IP 키 분당 상한, 크기 상한, 빈 검색어. */
class BookServiceTest {

    private final BookSearchService searchService = mock(BookSearchService.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final BookService service = new BookService(
            mock(BookRepository.class), mock(Yes24Client.class), mock(BookPageSuggestionRepository.class),
            mock(ReviewRepository.class), searchService, mock(ReadingRecordRepository.class),
            mock(EditorPickRepository.class), mock(BookLikeRepository.class), mock(UserRepository.class),
            rateLimiter);

    @Test
    @DisplayName("비회원 검색 — 같은 열람자 키가 분당 상한을 넘기면 RATE_LIMITED 이고 외부 검색은 부르지 않는다")
    void publicSearchIsRateLimitedPerViewerKey() {
        when(rateLimiter.tryAcquire("public:search:ip:abcd", BookService.PUBLIC_SEARCH_PER_MINUTE,
                Duration.ofMinutes(1))).thenReturn(false);

        assertThatThrownBy(() -> service.searchPublic("소설", 20, "ip:abcd"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED);
                    assertThat(e.getMessage()).isEqualTo("검색을 너무 자주 했어요. 잠시 후 다시 찾아 주세요.");
                });
        verify(searchService, never()).search(anyString(), anyInt());
    }

    @Test
    @DisplayName("비회원 검색 — 허용되면 검색어를 다듬고 크기를 20 으로 묶어 검색한다")
    void publicSearchClampsSize() {
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);
        when(searchService.search("소설", BookService.PUBLIC_SEARCH_MAX_SIZE)).thenReturn(List.of());

        assertThat(service.searchPublic("  소설 ", 50, "ip:abcd")).isEmpty();
        verify(searchService).search("소설", BookService.PUBLIC_SEARCH_MAX_SIZE);
        verify(rateLimiter).tryAcquire("public:search:ip:abcd", BookService.PUBLIC_SEARCH_PER_MINUTE,
                Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("비회원 검색 — 빈 검색어는 외부 API 도 리미터도 부르지 않고 빈 목록")
    void publicSearchIgnoresBlankKeyword() {
        assertThat(service.searchPublic("   ", 20, "ip:abcd")).isEmpty();
        assertThat(service.searchPublic(null, 20, "ip:abcd")).isEmpty();
        verify(searchService, never()).search(anyString(), anyInt());
        verify(rateLimiter, never()).tryAcquire(anyString(), anyInt(), any());
    }
}
