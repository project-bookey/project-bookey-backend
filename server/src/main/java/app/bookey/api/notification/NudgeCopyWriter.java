package app.bookey.api.notification;

import app.bookey.domain.reading.LagLevel;
import app.bookey.domain.user.NotifyTone;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 재촉 문구 생성 (§F5 재촉 톤).
 *
 * <p>같은 레벨이 반복될 때 문구가 변주되도록 variant 를 받는다.
 * L4 에서는 어떤 톤이든 "압박"이 아니라 "정리 제안"으로 바뀐다 —
 * 무한 재촉은 앱 삭제를 부르기 때문이다.
 */
@Component
public class NudgeCopyWriter {

    public record Copy(String title, String body) {}

    public Copy write(NotifyTone tone, LagLevel level, String bookTitle, int remainingPages,
                      long daysSinceRead, LocalDate estimatedFinish, LocalDate targetDate,
                      int variant) {
        if (level == LagLevel.L4_NEGLECTED) {
            return cleanupCopy(bookTitle, daysSinceRead);
        }
        return switch (tone) {
            case GENTLE, SILENT -> gentle(level, bookTitle, remainingPages, daysSinceRead, variant);
            case FACT -> fact(level, bookTitle, daysSinceRead, estimatedFinish, targetDate);
            case SPARTA -> sparta(level, bookTitle, daysSinceRead, variant);
            case TSUNDERE -> tsundere(level, bookTitle, remainingPages, daysSinceRead, variant);
        };
    }

    /** L4 — 하차/일시정지/목표 연장 중 하나를 고르게 만든다. */
    private Copy cleanupCopy(String bookTitle, long days) {
        return new Copy("이 책, 어떻게 할까요?",
                "『" + bookTitle + "』 " + days + "일째 멈춰 있어요. 잠시 멈춤 · 목표 연장 · 하차 중에서 골라 주세요. 하차도 기록입니다.");
    }

    private Copy gentle(LagLevel level, String title, int remaining, long days, int variant) {
        String[] bodies = {
                "『" + title + "』 " + remaining + "쪽 남았어요. 오늘 10분이면 한 걸음 나아가요 :)",
                "『" + title + "』, 지난 " + days + "일 동안 기다리고 있었어요. 딱 한 챕터만 어때요?",
                "잠깐 쉬어가도 괜찮아요. 그래도 『" + title + "』 " + remaining + "쪽이 남아 있어요."
        };
        return new Copy("오늘 조금만 읽어볼까요", bodies[Math.floorMod(variant, bodies.length)]);
    }

    private Copy fact(LagLevel level, String title, long days,
                      LocalDate estimated, LocalDate target) {
        StringBuilder body = new StringBuilder(days + "일 미독.");
        if (estimated != null && target != null) {
            body.append(" 이 페이스면 완독 예상일이 ").append(target).append(" → ").append(estimated)
                    .append("로 밀립니다.");
        } else if (estimated != null) {
            body.append(" 현재 페이스 기준 완독 예상일은 ").append(estimated).append("입니다.");
        }
        return new Copy("『" + title + "』 진척 리포트", body.toString());
    }

    private Copy sparta(LagLevel level, String title, long days, int variant) {
        String[] bodies = {
                days + "일째 안 읽음. 책이 당신을 노려보고 있습니다.",
                "변명은 됐고. 『" + title + "』 지금 펴세요.",
                days + "일. 읽겠다고 한 사람은 당신입니다."
        };
        return new Copy("『" + title + "』", bodies[Math.floorMod(variant, bodies.length)]);
    }

    private Copy tsundere(LagLevel level, String title, int remaining, long days, int variant) {
        String[] bodies = {
                "뭐, 안 읽어도 상관없는데. 남은 " + remaining + "쪽이 좀 불쌍하긴 하네.",
                "딱히 기다린 건 아니야. " + days + "일이나 지난 것도 몰랐고.",
                "『" + title + "』… 아니 그냥, 거기 그대로 있길래."
        };
        return new Copy("별일 아닌데", bodies[Math.floorMod(variant, bodies.length)]);
    }

    /** L3 마이크로 미션 — "딱 5분만" (§F5). */
    public Copy microMission(String bookTitle) {
        return new Copy("딱 5분만", "『" + bookTitle + "』 5분 타이머. 시작 버튼만 누르면 끝나요.");
    }

    /** 완독 임박 — 잔여 10% 이하 */
    public Copy almostDone(String bookTitle, int remainingPages) {
        return new Copy("이제 " + remainingPages + "쪽!",
                "『" + bookTitle + "』 오늘 끝낼 수 있어요.");
    }
}
