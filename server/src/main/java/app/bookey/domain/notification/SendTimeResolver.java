package app.bookey.domain.notification;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 알림 보낼 시각 정하기. 개인 알림과 관리자 캠페인이 같은 규칙을 쓴다.
 *  - 회원의 방해 금지 시간이면 그 시간이 끝나는 정각으로 미룬다.
 *  - 광고성 정보(마케팅)는 21시~다음 날 8시(한국 시간)에 보내지 않는다(정보통신망법 제50조 ③).
 */
public final class SendTimeResolver {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 광고성 정보를 보낼 수 있는 시간 [08, 21). */
    static final int MARKETING_FROM_HOUR = 8;
    static final int MARKETING_UNTIL_HOUR = 21;

    private SendTimeResolver() {
    }

    public static ZoneId zoneOf(String timezone) {
        try {
            return timezone == null ? KST : ZoneId.of(timezone);
        } catch (Exception e) {
            return KST;
        }
    }

    /** start == end 면 방해 금지를 쓰지 않는 것. start > end 면 자정을 넘는 구간(예: 22~8시). */
    public static boolean isQuietHour(int hour, int start, int end) {
        if (start == end) {
            return false;
        }
        if (start < end) {
            return hour >= start && hour < end;
        }
        return hour >= start || hour < end;
    }

    /** 방해 금지 시간이면 그 끝 정각으로 미룬다. */
    public static ZonedDateTime afterQuietHours(ZonedDateTime now, int quietStart, int quietEnd) {
        if (!isQuietHour(now.getHour(), quietStart, quietEnd)) {
            return now;
        }
        ZonedDateTime candidate = now.withHour(quietEnd).withMinute(0).withSecond(0).withNano(0);
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    /** 광고성 정보 발송 가능 시간 밖이면 다음 08시(한국 시간)로 미룬다. */
    public static Instant intoMarketingWindow(Instant at) {
        ZonedDateTime kst = at.atZone(KST);
        if (kst.getHour() >= MARKETING_FROM_HOUR && kst.getHour() < MARKETING_UNTIL_HOUR) {
            return at;
        }
        ZonedDateTime next = kst.withHour(MARKETING_FROM_HOUR).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(kst)) {
            next = next.plusDays(1);
        }
        return next.toInstant();
    }

    /**
     * 캠페인 알림을 이 회원에게 보낼 시각. 방해 금지를 피하고, 광고면 야간도 피한다.
     * 두 규칙이 서로 밀어낼 수 있어(방해 금지가 끝난 시각이 야간) 몇 번 번갈아 맞춘다.
     */
    public static Instant campaignSendTime(Instant now, ZoneId zone, int quietStart, int quietEnd, boolean marketing) {
        Instant at = now;
        for (int i = 0; i < 3; i++) {
            Instant next = afterQuietHours(at.atZone(zone), quietStart, quietEnd).toInstant();
            if (marketing) {
                next = intoMarketingWindow(next);
            }
            if (next.equals(at)) {
                return at;
            }
            at = next;
        }
        return at;
    }
}
