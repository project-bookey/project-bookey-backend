package app.bookey.api.me;

import app.bookey.api.auth.AuthService;
import app.bookey.api.auth.dto.AuthDtos.DeviceRegisterRequest;
import app.bookey.api.auth.dto.AuthDtos.MeResponse;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthUser;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Me", description = "내 프로필 · 디바이스")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final AuthService authService;
    private final AvatarService avatarService;
    private final UserRepository userRepository;

    public record UpdateProfileRequest(@Size(max = 50) String nickname, String avatarUrl,
                                       @Size(max = 10) java.util.List<@Size(max = 30) String> preferredCategories) {}

    @Operation(summary = "내 정보")
    @GetMapping
    public MeResponse me(@AuthenticationPrincipal AuthUser user) {
        return authService.me(user.id());
    }

    @Operation(summary = "프로필 수정")
    @PatchMapping
    @Transactional
    public MeResponse update(@AuthenticationPrincipal AuthUser user,
                             @Valid @RequestBody UpdateProfileRequest request) {
        User entity = userRepository.findById(user.id())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        entity.updateProfile(request.nickname(), request.avatarUrl());
        entity.updatePreferredCategories(
                request.preferredCategories() == null ? null : request.preferredCategories().toArray(String[]::new));
        return AuthService.toMe(entity);
    }

    @Operation(summary = "프로필 사진 업로드 — 온보딩 필수 단계")
    @PostMapping(value = "/avatar", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public MeResponse uploadAvatar(@AuthenticationPrincipal AuthUser user,
                                   @RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
        return avatarService.upload(user.id(), file);
    }

    @Operation(summary = "푸시 디바이스 등록")
    @PostMapping("/devices")
    public ResponseEntity<Void> registerDevice(@AuthenticationPrincipal AuthUser user,
                                               @Valid @RequestBody DeviceRegisterRequest request) {
        authService.registerDevice(user.id(), request);
        return ResponseEntity.noContent().build();
    }
}
