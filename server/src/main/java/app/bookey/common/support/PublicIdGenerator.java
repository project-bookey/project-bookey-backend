package app.bookey.common.support;

import java.security.SecureRandom;

/**
 * 팔로우용 공개 코드 생성기 (§14.3) — 16자리.
 * 모임 초대 코드(JoinCodeGenerator)와 같은 32진 알파벳(혼동 문자 제외), 길이만 16.
 */
public final class PublicIdGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PublicIdGenerator() {}

    public static String generate() {
        char[] out = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            out[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(out);
    }

    /** 소문자·공백·하이픈을 섞어 입력해도 받아준다. */
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
        String alphabet = new String(ALPHABET);
        for (char c : code.toCharArray()) {
            if (alphabet.indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }
}
