package app.bookey.domain.club;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 모임 정원 규칙 — 참가 시 정원 검사, 자리 늘리기 상한. */
class ClubTest {

    private static Club club(int memberLimit) {
        return Club.builder()
                .ownerId(1L)
                .name("월요일의 데미안")
                .joinCode("ABC234")
                .memberLimit((short) memberLimit)
                .startsAt(LocalDate.of(2026, 9, 1))
                .endsAt(LocalDate.of(2026, 9, 30))
                .allowNudge(true)
                .build();
    }

    private static ErrorCode codeOf(Throwable e) {
        return ((ApiException) e).getErrorCode();
    }

    @Test
    @DisplayName("정원이 차면 더 참가할 수 없다")
    void joinStopsAtLimit() {
        Club club = club(3);
        club.joinMember();
        club.joinMember();
        club.joinMember();

        assertThat(club.isFull()).isTrue();
        assertThatThrownBy(club::joinMember)
                .isInstanceOf(ApiException.class)
                .extracting(ClubTest::codeOf)
                .isEqualTo(ErrorCode.CLUB_FULL);
    }

    @Test
    @DisplayName("자리를 늘리면 늘어난 자리 수를 돌려주고, 가득 찼던 모임에 다시 참가할 수 있다")
    void expandReturnsAddedSeats() {
        Club club = club(3);
        club.joinMember();
        club.joinMember();
        club.joinMember();

        assertThat(club.expandMemberLimit(6, 6)).isEqualTo(3);
        assertThat(club.getMemberLimit()).isEqualTo((short) 6);
        club.joinMember();
        assertThat(club.getMemberCount()).isEqualTo((short) 4);
    }

    @Test
    @DisplayName("지금 정원 이하로는 늘릴 수 없다")
    void expandRejectsNotLarger() {
        Club club = club(4);

        assertThatThrownBy(() -> club.expandMemberLimit(4, 6))
                .extracting(ClubTest::codeOf)
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> club.expandMemberLimit(3, 6))
                .extracting(ClubTest::codeOf)
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(club.getMemberLimit()).isEqualTo((short) 4);
    }

    @Test
    @DisplayName("최대 정원을 넘겨 늘릴 수 없다")
    void expandRejectsOverMax() {
        Club club = club(3);

        assertThatThrownBy(() -> club.expandMemberLimit(7, 6))
                .extracting(ClubTest::codeOf)
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(club.getMemberLimit()).isEqualTo((short) 3);
    }

    @Test
    @DisplayName("끝난 모임은 자리를 늘릴 수 없다 — 늘린 자리는 종료와 함께 사라진다")
    void expandRejectsEndedClub() {
        Club club = club(3);
        club.end();

        assertThatThrownBy(() -> club.expandMemberLimit(4, 6))
                .extracting(ClubTest::codeOf)
                .isEqualTo(ErrorCode.CLUB_ENDED);
    }
}
