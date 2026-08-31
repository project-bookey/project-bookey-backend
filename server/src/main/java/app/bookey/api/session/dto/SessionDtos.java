package app.bookey.api.session.dto;

import app.bookey.domain.reading.SessionSource;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SessionDtos {

    private SessionDtos() {}

    public record StartRequest(
            @NotNull Long readingRecordId,
            @Min(0) Integer startPage,
            /** 오프라인 복원용 — 앱이 죽어도 시작 시각으로 복원한다(§9 기술 리스크). */
            Instant startedAt,
            UUID clientUuid
    ) {}

    public record EndRequest(
            @Min(0) Integer endPage,
            Instant endedAt,
            @DecimalMin("0.0") @DecimalMax("1.0") Double foregroundRatio,
            @Min(0) Integer interactionCount,
            @Size(max = 2000) String memo
    ) {}

    public record ManualRequest(
            @NotNull Long readingRecordId,
            @NotNull Instant startedAt,
            @NotNull @Min(60) @Max(86400) Integer durationSec,
            @Min(0) Integer startPage,
            @Min(0) Integer endPage,
            @Size(max = 2000) String memo,
            UUID clientUuid
    ) {}

    public record SessionView(
            @NotNull Long id,
            @NotNull Long readingRecordId,
            @NotNull Instant startedAt,
            Instant endedAt,
            int durationSec,
            Integer startPage,
            Integer endPage,
            Integer readPages,
            @NotNull SessionSource source,
            String memo,
            List<String> abuseFlags,
            boolean countedForVerification
    ) {}

    /** 세션 종료 응답 — 진척 변화와 모임 반영 결과를 함께 준다. */
    public record SessionEndResult(
            @NotNull SessionView session,
            int currentPage,
            Double completionRate,
            @NotNull String lagLevel,
            boolean bookFinished,
            @NotNull List<ClubProgressEcho> clubs
    ) {}

    public record ClubProgressEcho(Long clubId, String clubName, int rank, int memberCount) {}

    public record DailyStat(@NotNull String date, long durationSec, long pages, int sessionCount) {}

    public record StatsSummary(
            long totalDurationSec,
            long todayDurationSec,
            long weekDurationSec,
            int currentStreakDays,
            int longestStreakDays,
            List<DailyStat> daily
    ) {}
}
