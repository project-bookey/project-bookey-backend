package app.bookey.admin.support;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * TOTP (RFC 6238) 검증 — 관리자 2단계 인증 (§F13 보안 요구사항).
 * 시계 오차를 감안해 앞뒤 1스텝(±30초)까지 허용한다.
 */
@Component
public class TotpVerifier {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int WINDOW = 1;

    public boolean verify(String base32Secret, String code) {
        if (base32Secret == null || code == null || code.length() != DIGITS) {
            return false;
        }
        byte[] key = decodeBase32(base32Secret);
        long counter = Instant.now().getEpochSecond() / STEP_SECONDS;
        for (int offset = -WINDOW; offset <= WINDOW; offset++) {
            if (generate(key, counter + offset).equals(code)) {
                return true;
            }
        }
        return false;
    }

    public String generateSecret() {
        byte[] bytes = new byte[20];
        new SecureRandom().nextBytes(bytes);
        StringBuilder secret = new StringBuilder();
        for (byte b : bytes) {
            secret.append(BASE32.charAt((b & 0xFF) % BASE32.length()));
        }
        return secret.toString();
    }

    private String generate(byte[] key, long counter) {
        try {
            byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP generation failed", e);
        }
    }

    private byte[] decodeBase32(String encoded) {
        String clean = encoded.replace("=", "").toUpperCase();
        int bits = 0;
        int value = 0;
        int index = 0;
        byte[] out = new byte[clean.length() * 5 / 8];
        for (char c : clean.toCharArray()) {
            int position = BASE32.indexOf(c);
            if (position < 0) {
                continue;
            }
            value = (value << 5) | position;
            bits += 5;
            if (bits >= 8) {
                out[index++] = (byte) ((value >>> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out;
    }
}
