package app.bookey.api.club;

import app.bookey.api.club.dto.ClubDtos.CreateClubRequest;
import app.bookey.api.club.dto.ClubDtos.JoinPublicRequest;
import app.bookey.api.club.dto.ClubDtos.JoinRequest;
import app.bookey.api.library.ProgressService;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.admin.OpsFlagRepository;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.club.*;
import app.bookey.domain.reading.ReadingRecordRepository;
import app.bookey.domain.reading.ReadingSessionRepository;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 모임 생성 정원 제한과, 정원을 건드리는 참가 경로의 행 잠금. */
class ClubServiceTest {

    private static final BookeyProperties.Club CLUB_POLICY =
            new BookeyProperties.Club(3, 6, 3, 4, Duration.ofHours(24), 3, 10);

    private final ClubRepository clubRepository = mock(ClubRepository.class);
    private final BookRepository bookRepository = mock(BookRepository.class);
    private final ClubService service = new ClubService(
            clubRepository,
            mock(ClubBookRepository.class),
            mock(ClubMemberRepository.class),
            mock(ClubCheckpointRepository.class),
            mock(ClubCheckpointProgressRepository.class),
            mock(ClubPostRepository.class),
            mock(ClubEventRepository.class),
            bookRepository,
            mock(ReadingRecordRepository.class),
            mock(ReadingSessionRepository.class),
            mock(UserRepository.class),
            mock(OpsFlagRepository.class),
            mock(ProgressService.class),
            mock(RateLimiter.class),
            new BookeyProperties(null, null, null, null, CLUB_POLICY, null, null, null, null, null));

    private static CreateClubRequest create(Integer memberLimit) {
        return new CreateClubRequest("월요일의 데미안", null, 7L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                ClubVisibility.CODE_ONLY, memberLimit, true, false, null);
    }

    private static ErrorCode codeOf(Throwable e) {
        return ((ApiException) e).getErrorCode();
    }

    @Test
    @DisplayName("무료 정원(3명)을 넘겨 모임을 만들 수 없다 — 책 조회 전에 거절한다")
    void createRejectsOverFreeLimit() {
        assertThatThrownBy(() -> service.create(1L, create(4)))
                .isInstanceOf(ApiException.class)
                .extracting(ClubServiceTest::codeOf)
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(bookRepository, never()).findById(any());
    }

    @Test
    @DisplayName("정원을 비우면 기본값(3명)으로 통과해 다음 단계로 간다")
    void createDefaultsWithinFreeLimit() {
        when(bookRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, create(null)))
                .extracting(ClubServiceTest::codeOf)
                .isEqualTo(ErrorCode.BOOK_NOT_FOUND);
    }

    @Test
    @DisplayName("코드 참가는 모임 행을 잠가 읽는다")
    void joinByCodeLocksClub() {
        when(clubRepository.findByJoinCodeForUpdate("ABC234")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.join(2L, new JoinRequest("abc234", true, true)))
                .extracting(ClubServiceTest::codeOf)
                .isEqualTo(ErrorCode.CLUB_CODE_INVALID);
        verify(clubRepository).findByJoinCodeForUpdate("ABC234");
        verify(clubRepository, never()).findByJoinCode(any());
    }

    @Test
    @DisplayName("공개 모임 참가도 모임 행을 잠가 읽는다")
    void joinPublicLocksClub() {
        when(clubRepository.findByIdForUpdate(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.joinPublic(2L, 10L, new JoinPublicRequest(true, true)))
                .extracting(ClubServiceTest::codeOf)
                .isEqualTo(ErrorCode.CLUB_NOT_FOUND);
        verify(clubRepository).findByIdForUpdate(10L);
        verify(clubRepository, never()).findById(any());
    }
}
