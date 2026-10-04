package app.bookey.api.legal;

import app.bookey.api.notification.NotificationService;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.legal.ConsentKind;
import app.bookey.domain.legal.LegalDocument;
import app.bookey.domain.legal.UserConsent;
import app.bookey.domain.legal.UserConsentRepository;
import app.bookey.domain.notification.NotificationType;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsentServiceTest {

    private final UserConsentRepository consentRepository = mock(UserConsentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final ConsentService service = new ConsentService(consentRepository, userRepository, notificationService);

    private void currently(ConsentKind kind, boolean agreed) {
        when(consentRepository.findTopByUserIdAndKindOrderByIdDesc(1L, kind))
                .thenReturn(Optional.of(new UserConsent(1L, kind, null, agreed, Instant.now())));
    }

    @Test
    @DisplayName("광고 수신 동의 — 지금 버전으로 한 줄 남기고 처리 결과를 CONSENT_RESULT 알림으로 보낸다")
    void marketingAgreeRecordsAndNotifies() {
        service.set(1L, ConsentKind.MARKETING, true);

        ArgumentCaptor<UserConsent> saved = ArgumentCaptor.forClass(UserConsent.class);
        verify(consentRepository).save(saved.capture());
        assertThat(saved.getValue().isAgreed()).isTrue();
        assertThat(saved.getValue().getVersion()).isEqualTo(LegalDocument.MARKETING.getVersion());
        ArgumentCaptor<NotificationService.NotificationRequest> sent =
                ArgumentCaptor.forClass(NotificationService.NotificationRequest.class);
        verify(notificationService).inApp(sent.capture());
        assertThat(sent.getValue().type()).isEqualTo(NotificationType.CONSENT_RESULT);
        assertThat(sent.getValue().title()).contains("동의");
    }

    @Test
    @DisplayName("이미 같은 상태면 아무것도 남기지 않고 알리지도 않는다")
    void sameStateIsNoop() {
        currently(ConsentKind.MARKETING, true);

        service.set(1L, ConsentKind.MARKETING, true);

        verify(consentRepository, never()).save(any());
        verify(notificationService, never()).inApp(any());
    }

    @Test
    @DisplayName("선택 정보 동의를 철회하면 버전 없는 철회 행을 남기고 성별·생년월일을 지운다")
    void withdrawProfileOptionalClearsDemographics() {
        currently(ConsentKind.PROFILE_OPTIONAL, true);
        User user = User.builder().handle("u1").email("u1@dev.local").nickname("u1").build();
        user.updateDemographics("FEMALE", LocalDate.of(1995, 1, 1));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.set(1L, ConsentKind.PROFILE_OPTIONAL, false);

        ArgumentCaptor<UserConsent> saved = ArgumentCaptor.forClass(UserConsent.class);
        verify(consentRepository).save(saved.capture());
        assertThat(saved.getValue().isAgreed()).isFalse();
        assertThat(saved.getValue().getVersion()).isNull();
        assertThat(user.getGender()).isNull();
        assertThat(user.getBirthDate()).isNull();
        verify(notificationService, never()).inApp(any());
    }

    @Test
    @DisplayName("필수 동의·YES24 는 앱에서 직접 바꿀 수 없다 — INVALID_REQUEST")
    void nonSelfServiceKindsAreRejected() {
        for (ConsentKind kind : List.of(ConsentKind.TERMS, ConsentKind.PRIVACY, ConsentKind.AGE_14, ConsentKind.THIRD_PARTY_YES24)) {
            assertThatThrownBy(() -> service.set(1L, kind, false))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_REQUEST);
        }
        verify(consentRepository, never()).save(any());
    }

    @Test
    @DisplayName("상태 — 종류별 마지막 행만, 기록이 없는 종류는 빠진다")
    void statesKeepLatestPerKind() {
        Instant first = Instant.parse("2026-10-01T00:00:00Z");
        Instant later = Instant.parse("2026-10-04T00:00:00Z");
        when(consentRepository.findAllByUserIdOrderByIdAsc(1L)).thenReturn(List.of(
                new UserConsent(1L, ConsentKind.TERMS, "2026-09-27", true, first),
                new UserConsent(1L, ConsentKind.MARKETING, "2026-10-04", true, first),
                new UserConsent(1L, ConsentKind.MARKETING, null, false, later)));

        var states = service.states(1L);

        assertThat(states).extracting(s -> s.kind()).containsExactly(ConsentKind.TERMS, ConsentKind.MARKETING);
        assertThat(states.get(1).agreed()).isFalse();
        assertThat(states.get(1).at()).isEqualTo(later);
    }

    @Test
    @DisplayName("동의 기록 없이 들어 있던 성별·생년월일도 선택 정보 철회로 지운다 — 이력은 남기지 않는다")
    void withdrawWithoutRecordStillClears() {
        User user = User.builder().handle("u1").email("u1@dev.local").nickname("u1").build();
        user.updateDemographics("MALE", LocalDate.of(1990, 5, 5));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.set(1L, ConsentKind.PROFILE_OPTIONAL, false);

        assertThat(user.getGender()).isNull();
        assertThat(user.getBirthDate()).isNull();
        verify(consentRepository, never()).save(any());
    }
}
