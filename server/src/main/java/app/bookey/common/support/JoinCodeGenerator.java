package app.bookey.common.support;

import java.security.SecureRandom;

/**
 * 모임 초대 코드 생성기 (§8.5).
 * 혼동 문자(O/0/I/1)를 제외한 32진 알파벳 6자리 = 약 10.7억 조합.
 */
public final class JoinCodeGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private JoinCodeGenerator() {}

    public static String generate() {
        char[] out = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            out[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(out);
    }

    /** 사용자가 소문자·공백·하이픈을 섞어 입력해도 받아준다. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    public static boolean isValidFormat(String code) {
        if (code == null || code.length() != LENGTH) {
            return false;
        }
        for (char c : code.toCharArray()) {
            if (new String(ALPHABET).indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }
}
