package app.bookey.domain.club;

import lombok.Getter;

/**
 * 찌르기 프리셋 (§12.4).
 * 자유 입력을 허용하지 않아 괴롭힘 가능성을 원천 차단한다.
 */
@Getter
public enum NudgeMessage {
    READ_TOGETHER("같이 읽어요 📖"),
    CHECKPOINT_SOON("체크포인트 얼마 안 남았어요"),
    WAITING("기다리고 있어요");

    private final String text;

    NudgeMessage(String text) {
        this.text = text;
    }
}
