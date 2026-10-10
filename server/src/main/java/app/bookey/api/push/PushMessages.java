package app.bookey.api.push;

import app.bookey.domain.push.PushCampaignKind;

/**
 * 회원에게 보일 캠페인 문구. 광고성 정보는 서버가 강제로 표시를 붙인다(정보통신망법 제50조 ④) —
 * 관리자 화면에서 빠뜨려도 나가지 않게.
 */
public final class PushMessages {

    static final String AD_PREFIX = "(광고) ";
    static final String OPT_OUT = "\n수신 거부: 앱 설정 > 알림 > 광고성 정보 수신 끄기";

    private PushMessages() {
    }

    public static String title(PushCampaignKind kind, String title) {
        String trimmed = title.trim();
        if (kind != PushCampaignKind.MARKETING || trimmed.startsWith("(광고)")) {
            return trimmed;
        }
        return AD_PREFIX + trimmed;
    }

    public static String body(PushCampaignKind kind, String body) {
        String trimmed = body.trim();
        return kind == PushCampaignKind.MARKETING ? trimmed + OPT_OUT : trimmed;
    }
}
