package app.bookey.api.club.dto;
import app.bookey.api.book.dto.BookDtos.BookSummary; import jakarta.validation.constraints.*; import java.time.Instant; import java.util.List;
public final class ClubCommunityDtos { private ClubCommunityDtos(){}
 public record ChatState(boolean unlocked,int unlockCost,int bookmarkBalance,long unreadCount,long totalMessageCount,Instant lastActivityAt){}
 public record UnlockResult(boolean unlocked,int bookmarkBalance){}
 public record GiftChatRequest(@NotNull Long userId){}
 public record ChatGiftCandidate(Long userId,String nickname,boolean unlocked){}
 public record SendChatRequest(@NotBlank @Size(max=1000) String body){}
 public record ChatMessageView(Long id,Long senderId,String senderNickname,String body,Instant createdAt,boolean mine){}
 public record ChatMessagesView(List<ChatMessageView> messages,Long nextBeforeId){}
 public record UpsertMeetingRequest(@NotBlank @Size(max=100) String title,@Size(max=1000) String description,@NotNull Instant startsAt,Instant endsAt,@NotBlank @Size(max=150) String placeName,@NotBlank @Size(max=300) String address,@DecimalMin("-90") @DecimalMax("90") Double latitude,@DecimalMin("-180") @DecimalMax("180") Double longitude,@Size(max=2000) String mapUrl,Instant responseDeadline,
  /** 이 만남에서 읽을 책(선택). */ Long bookId){}
 public record MeetingAttendeeView(Long userId,String nickname,String avatarUrl){}
 public record MeetingView(Long id,Long clubId,String title,String description,Instant startsAt,Instant endsAt,String placeName,String address,Double latitude,Double longitude,String mapUrl,Instant responseDeadline,String status,long attendeeCount,boolean attending,boolean host,List<String> attendeeNicknames,List<MeetingAttendeeView> attendees,
  /** 이 만남에서 읽을 책 — 고르지 않았으면 null. */ BookSummary book){}
 public record ActivitySessionView(Long id,Long meetingId,Instant startedAt,Instant endedAt,Integer durationSec){}
 /** 함께 독서 기록 카드 — 노트 스티커로 붙일 때 이 값들을 스냅숏으로 담는다. */
 public record ActivityCardView(Long id,Long clubId,String clubName,String meetingTitle,String nickname,int durationSec,Instant endedAt){}
}
