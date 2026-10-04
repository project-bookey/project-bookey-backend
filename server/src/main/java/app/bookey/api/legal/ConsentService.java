package app.bookey.api.legal;

import app.bookey.api.auth.dto.AuthDtos.ConsentStateView;
import app.bookey.api.auth.dto.AuthDtos.SignupConsent;
import app.bookey.api.notification.NotificationService;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.legal.ConsentKind;
import app.bookey.domain.legal.UserConsent;
import app.bookey.domain.legal.UserConsentRepository;
import app.bookey.domain.notification.NotificationType;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 동의 기록 — 가입 때의 필수·선택 동의와 가입 뒤 선택 동의 켜고 끄기를 user_consents 에 이력으로 쌓는다.
 * 지금 상태는 종류별 마지막 행이다.
 */
@Service
@RequiredArgsConstructor
public class ConsentService {

    private static final DateTimeFormatter KOREAN_DATE =
            DateTimeFormatter.ofPattern("yyyy년 M월 d일").withZone(ZoneId.of("Asia/Seoul"));

    private final UserConsentRepository consentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * 가입 동의 검증 — 필수 셋(약관·개인정보 수집·이용·만 14세 이상)이 모두 있고 문서는 지금 버전이어야 한다.
     * 광고 수신에 동의했다면 그 문서도 지금 버전이어야 한다. 계정을 만들기 전에 부른다.
     */
    public void requireSignupConsent(SignupConsent consent) {
        if (consent == null
                || !Boolean.TRUE.equals(consent.termsAgreed())
                || !ConsentKind.TERMS.currentVersion().equals(consent.termsVersion())
                || !Boolean.TRUE.equals(consent.privacyAgreed())
                || !ConsentKind.PRIVACY.currentVersion().equals(consent.privacyVersion())
                || !Boolean.TRUE.equals(consent.ageConfirmed())
                || (Boolean.TRUE.equals(consent.marketingAgreed())
                    && !ConsentKind.MARKETING.currentVersion().equals(consent.marketingVersion()))) {
            throw ApiException.of(ErrorCode.LEGAL_CONSENT_REQUIRED);
        }
    }

    /** 가입 직후 — 필수 셋과(동의했다면) 광고 수신을 같은 시각으로 남긴다. requireSignupConsent 를 통과한 값만 온다. */
    @Transactional
    public void recordSignup(Long userId, SignupConsent consent, Instant at) {
        save(userId, ConsentKind.TERMS, true, at);
        save(userId, ConsentKind.PRIVACY, true, at);
        save(userId, ConsentKind.AGE_14, true, at);
        if (Boolean.TRUE.equals(consent.marketingAgreed())) {
            save(userId, ConsentKind.MARKETING, true, at);
            notifyMarketingResult(userId, true, at);
        }
    }

    /**
     * 선택 동의 켜고 끄기 — 회원이 직접 바꿀 수 있는 종류만. 지금 상태와 같으면 이력을 남기지 않는다.
     * 선택 정보 동의를 철회하면 성별·생년월일을 지우고, 광고 수신은 바뀔 때마다 처리 결과를 알린다.
     */
    @Transactional
    public void set(Long userId, ConsentKind kind, boolean agreed) {
        if (!kind.isSelfService()) {
            throw ApiException.of(ErrorCode.INVALID_REQUEST);
        }
        // 선택 정보 철회는 기록이 없던 값(이 동의가 생기기 전에 넣은 성별·생년월일)도 지운다.
        if (kind == ConsentKind.PROFILE_OPTIONAL && !agreed) {
            userRepository.findById(userId).ifPresent(User::clearDemographics);
        }
        if (isAgreed(userId, kind) == agreed) {
            return;
        }
        Instant now = Instant.now();
        save(userId, kind, agreed, now);
        if (kind == ConsentKind.MARKETING) {
            notifyMarketingResult(userId, agreed, now);
        }
    }

    @Transactional(readOnly = true)
    public boolean isAgreed(Long userId, ConsentKind kind) {
        return consentRepository.findTopByUserIdAndKindOrderByIdDesc(userId, kind)
                .map(UserConsent::isAgreed)
                .orElse(false);
    }

    /** 종류별 지금 상태 — 기록이 한 번도 없는 종류는 빠진다. */
    @Transactional(readOnly = true)
    public List<ConsentStateView> states(Long userId) {
        Map<ConsentKind, UserConsent> latest = new EnumMap<>(ConsentKind.class);
        for (UserConsent consent : consentRepository.findAllByUserIdOrderByIdAsc(userId)) {
            latest.put(consent.getKind(), consent);
        }
        return latest.values().stream()
                .map(c -> new ConsentStateView(c.getKind(), c.isAgreed(), c.getVersion(), c.getCreatedAt()))
                .toList();
    }

    private void save(Long userId, ConsentKind kind, boolean agreed, Instant at) {
        consentRepository.save(new UserConsent(userId, kind, agreed ? kind.currentVersion() : null, agreed, at));
    }

    /** 정보통신망법 제50조 ⑧ — 광고성 정보 수신 동의·철회의 처리 결과(보낸 곳·일자·내용)를 알린다. */
    private void notifyMarketingResult(Long userId, boolean agreed, Instant at) {
        notificationService.inApp(new NotificationService.NotificationRequest(
                userId, NotificationType.CONSENT_RESULT, null, null, null,
                agreed ? "광고성 정보 수신에 동의했어요" : "광고성 정보 수신 동의를 철회했어요",
                "Bookey · " + KOREAN_DATE.format(at) + " 처리했어요. 설정의 알림에서 언제든 바꿀 수 있어요.",
                Map.of("kind", ConsentKind.MARKETING.name(), "agreed", agreed), null));
    }
}
