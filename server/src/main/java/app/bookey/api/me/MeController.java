package app.bookey.api.me;

import app.bookey.api.auth.AuthService;
import app.bookey.api.auth.dto.AuthDtos.DeviceRegisterRequest;
import app.bookey.api.auth.dto.AuthDtos.MeResponse;
import app.bookey.api.legal.ConsentService;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthUser;
import app.bookey.domain.legal.ConsentKind;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
    private final ConsentService consentService;

    public record UpdateProfileRequest(@Size(max = 50) String nickname, String avatarUrl,
                                       @Pattern(regexp = "MALE|FEMALE|OTHER|PREFER_NOT_TO_SAY") String gender,
                                       java.time.LocalDate birthDate,
                                       @Size(max = 10, message = "{max}개까지 고를 수 있어요.") java.util.List<@Size(max = 30) String> preferredCategories) {}

    public record ConsentUpdateRequest(@NotNull Boolean agreed) {}

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
        if (request.nickname() != null && !request.nickname().isBlank()
                && userRepository.existsByNicknameIgnoreCaseAndIdNot(request.nickname().trim(), user.id())) {
            throw ApiException.of(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }
        // 성별·생년월일은 선택 정보 — 값을 새로 넣거나 바꿀 때는 선택 동의가 있어야 한다(같은 값을 다시 보내는 건 괜찮다).
        boolean changesGender = request.gender() != null && !request.gender().isBlank()
                && !request.gender().equals(entity.getGender());
        boolean changesBirthDate = request.birthDate() != null && !request.birthDate().equals(entity.getBirthDate());
        if ((changesGender || changesBirthDate)
                && !consentService.isAgreed(user.id(), ConsentKind.PROFILE_OPTIONAL)) {
            throw ApiException.of(ErrorCode.CONSENT_REQUIRED);
        }
        entity.updateProfile(request.nickname(), request.avatarUrl());
        entity.updateDemographics(request.gender(), request.birthDate());
        entity.updatePreferredCategories(
                request.preferredCategories() == null ? null : request.preferredCategories().toArray(String[]::new));
        return authService.toMe(entity);
    }

    @Operation(summary = "선택 동의 켜고 끄기 — MARKETING(광고성 정보 수신)·PROFILE_OPTIONAL(성별·생년월일, 철회하면 지운다)")
    @PutMapping("/consents/{kind}")
    @Transactional
    public MeResponse setConsent(@AuthenticationPrincipal AuthUser user,
                                 @PathVariable ConsentKind kind,
                                 @Valid @RequestBody ConsentUpdateRequest request) {
        consentService.set(user.id(), kind, request.agreed());
        return authService.me(user.id());
    }

    @Operation(summary = "프로필 사진 업로드")
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

    @Operation(summary = "계정 탈퇴 — 즉시 로그인 차단, 30일 뒤 연관 기록 영구 삭제")
    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal AuthUser user) {
        authService.deleteAccount(user.id());
        return ResponseEntity.noContent().build();
    }
}
