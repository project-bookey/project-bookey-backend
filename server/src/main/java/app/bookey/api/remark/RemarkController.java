package app.bookey.api.remark;

import app.bookey.api.auth.WriteBanGuarded;
import app.bookey.api.remark.dto.RemarkDtos.RemarkRequest;
import app.bookey.api.remark.dto.RemarkDtos.RemarkView;
import app.bookey.common.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Remark", description = "한 마디 — 완독·하차 때 남기는 한 줄")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RemarkController {

    private final RemarkService remarkService;

    @Operation(summary = "도서별 한 마디 — 최근에 쓴 순")
    @GetMapping("/books/{bookId}/remarks")
    public List<RemarkView> listByBook(@PathVariable Long bookId,
                                       @RequestParam(defaultValue = "20") int size) {
        return remarkService.listByBook(bookId, size);
    }

    @Operation(summary = "내 한 마디 — 이 읽기 기록에 남긴 것, 없으면 빈 응답")
    @GetMapping("/library/{recordId}/remark")
    public RemarkView mine(@AuthenticationPrincipal AuthUser user, @PathVariable Long recordId) {
        return remarkService.mine(user.id(), recordId);
    }

    @Operation(summary = "한 마디 남기기 — 완독·하차한 기록에만, 다시 쓰면 고쳐진다")
    @PutMapping("/library/{recordId}/remark")
    @WriteBanGuarded
    public RemarkView write(@AuthenticationPrincipal AuthUser user,
                            @PathVariable Long recordId,
                            @Valid @RequestBody RemarkRequest request) {
        return remarkService.write(user.id(), recordId, request.body());
    }

    @Operation(summary = "한 마디 지우기")
    @DeleteMapping("/library/{recordId}/remark")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long recordId) {
        remarkService.delete(user.id(), recordId);
        return ResponseEntity.noContent().build();
    }
}
