package app.bookey.api.push.dto;

import app.bookey.domain.push.PushCampaignKind;
import app.bookey.domain.push.PushCampaignStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class PushCampaignDtos {

    private PushCampaignDtos() {}

    public record PushCampaignRow(
            @NotNull Long id,
            @NotNull PushCampaignKind kind,
            @NotNull String title,
            @NotNull String body,
            String linkUrl,
            @NotNull PushCampaignStatus status,
            @NotNull Instant scheduledAt,
            Instant startedAt,
            Instant finishedAt,
            /** 지금까지 알림을 만든 대상자 수. */
            int targetCount,
            @NotNull Long createdBy,
            String createdByName,
            @NotNull Instant createdAt
    ) {}

    /** 캠페인 상세 — 발송·대기·열람 수. 대기는 방해 금지·야간이라 미뤄 둔 알림이다. */
    public record PushCampaignView(
            @NotNull PushCampaignRow campaign,
            /** 회원에게 실제로 보일 제목·본문(광고면 '(광고)'·수신 거부 안내가 붙는다). */
            @NotNull String finalTitle,
            @NotNull String finalBody,
            long sentCount,
            long pendingCount,
            long openedCount
    ) {}

    public record PushAudienceView(long eligibleUsers, long withPushDevice) {}

    /**
     * scheduledAt 이 비어 있으면 바로 보낸다. 광고는 수신 동의자에게만, 21–08시에는 다음 날 아침으로 미룬다.
     * linkUrl 은 https:// 또는 앱 화면 경로(/로 시작).
     */
    public record PushCampaignRequest(
            @NotNull PushCampaignKind kind,
            @NotBlank @Size(max = 60) String title,
            @NotBlank @Size(max = 300) String body,
            @Size(max = 500) String linkUrl,
            Instant scheduledAt,
            @NotBlank @Size(max = 500) String reason
    ) {}

    /** 테스트 발송 결과 — 알림을 만든 회원 수(로그인할 수 없는 회원은 빠진다). */
    public record PushTestResult(int delivered) {}

    /** 테스트 발송 — 지정한 회원(최대 5명)에게만, 제목에 [테스트] 를 붙여 바로 보낸다. */
    public record PushTestRequest(
            @NotNull PushCampaignKind kind,
            @NotBlank @Size(max = 60) String title,
            @NotBlank @Size(max = 300) String body,
            @Size(max = 500) String linkUrl,
            @NotEmpty @Size(max = 5) List<Long> userIds
    ) {}
}
