package app.bookey.api.social;

import app.bookey.api.social.dto.ChatDtos.ChatMessageView;
import app.bookey.api.social.dto.ChatDtos.ChatMessagesView;
import app.bookey.api.social.dto.ChatDtos.ChatSummaryView;
import app.bookey.api.social.dto.ChatDtos.OpenChatRequest;
import app.bookey.api.social.dto.ChatDtos.SendMessageRequest;
import app.bookey.common.security.AuthUser;
import app.bookey.common.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Chat", description = "1:1 채팅 — 맞팔로우끼리만 (§14.3)")
@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "채팅방 열기 — 맞팔로우인 상대만, 이미 있으면 그 방")
    @PostMapping
    public ChatSummaryView open(@AuthenticationPrincipal AuthUser user,
                                @Valid @RequestBody OpenChatRequest request) {
        return chatService.open(user.id(), request.userId());
    }

    @Operation(summary = "내 채팅 목록 — 마지막 메시지·안읽음 수 포함")
    @GetMapping
    public PageResponse<ChatSummaryView> list(@AuthenticationPrincipal AuthUser user,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return chatService.list(user.id(), PageRequest.of(page, size));
    }

    @Operation(summary = "메시지 커서 페이지(최신순) — 첫 페이지를 열면 읽음 처리된다")
    @GetMapping("/{chatId}/messages")
    public ChatMessagesView messages(@AuthenticationPrincipal AuthUser user,
                                     @PathVariable Long chatId,
                                     @RequestParam(required = false) Long beforeId) {
        return chatService.messages(user.id(), chatId, beforeId);
    }

    @Operation(summary = "메시지 보내기 — 언팔로우된 상대에게는 보낼 수 없다")
    @PostMapping("/{chatId}/messages")
    public ChatMessageView send(@AuthenticationPrincipal AuthUser user,
                                @PathVariable Long chatId,
                                @Valid @RequestBody SendMessageRequest request) {
        return chatService.send(user.id(), chatId, request);
    }
}
