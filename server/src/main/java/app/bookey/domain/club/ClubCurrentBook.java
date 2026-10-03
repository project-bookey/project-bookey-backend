package app.bookey.domain.club;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 모임의 '지금 읽는 책' 고르기 — 모임은 기간 없이 이어지고 책은 만남마다 고르므로,
 * 책을 고른 만남 중 오늘 이후 가장 가까운 만남의 책을 따른다. 만남 날 하루 동안은 그 만남이 '다가오는' 쪽이다.
 * 다가오는 만남이 없으면 가장 최근에 지난 만남의 책, 책을 고른 만남이 하나도 없으면 null(지금 책을 그대로 둔다).
 * 취소된 만남은 보지 않는다.
 */
public final class ClubCurrentBook {

    private ClubCurrentBook() {}

    /** 만남 하나 — 판정에 필요한 값만. */
    public record Slot(Instant startsAt, boolean open, Long bookId) {

        public static Slot of(ClubMeeting meeting) {
            return new Slot(meeting.getStartsAt(), meeting.isOpen(), meeting.getBookId());
        }
    }

    public static Long pick(List<Slot> meetings, LocalDate today, ZoneId zone) {
        Instant startOfToday = today.atStartOfDay(zone).toInstant();
        List<Slot> withBook = meetings.stream()
                .filter(Slot::open)
                .filter(s -> s.bookId() != null && s.startsAt() != null)
                .toList();
        return withBook.stream()
                .filter(s -> !s.startsAt().isBefore(startOfToday))
                .min(Comparator.comparing(Slot::startsAt))
                .or(() -> withBook.stream().max(Comparator.comparing(Slot::startsAt)))
                .map(Slot::bookId)
                .filter(Objects::nonNull)
                .orElse(null);
    }
}
