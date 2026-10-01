package app.bookey.api.club.dto;

import app.bookey.api.club.dto.ClubCommunityDtos.MeetingAttendeeView;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 모임 공유 노트 DTO. */
public final class ClubMeetingNoteDtos {

    private ClubMeetingNoteDtos() {
    }

    @Schema(description = "모임 공유 노트 — 모임 하나에 대형노트 한 권. 아직 아무도 쓰지 않았으면 id 는 null, version 0, 빈 문서")
    public record MeetingNoteView(
            Long id,
            @NotNull Long clubId,
            @NotNull Long meetingId,
            String meetingTitle,
            Instant meetingStartsAt,
            @Schema(description = "대형노트 문서 {v, paper, kind:'large', elements[]} — 앱이 소유한 JSON")
            Map<String, Object> document,
            int version,
            int elementCount,
            @Schema(description = "노트에 손댄 멤버 — 처음 손댄 순")
            List<MeetingAttendeeView> contributors,
            Instant updatedAt,
            @Schema(description = "끝난 클럽·취소된 모임이면 true — 읽기만 된다")
            boolean readOnly) {
    }

    @Schema(description = "노트 연산 — 요소 id 기준 upsert({t:'upsert', el}) · delete({t:'delete', id}). 같은 연산을 다시 보내도 결과가 같다")
    public record ApplyMeetingNoteOpsRequest(
            @NotNull @Size(min = 1, max = 200) List<Map<String, Object>> ops,
            @Schema(description = "보낸 기기 식별자 — 실시간 방송에서 자기 연산을 알아보는 데 쓴다")
            @Size(max = 64) String clientId) {
    }

    public record MeetingNoteOpsResult(int version) {
    }

    public record MeetingNoteImageView(@NotNull Long id, @NotNull String url, Integer width, Integer height) {
    }
}
