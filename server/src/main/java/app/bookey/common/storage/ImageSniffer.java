package app.bookey.common.storage;

/**
 * 파일 앞 바이트의 매직넘버로 이미지 형식을 판별한다.
 * 클라이언트가 보낸 Content-Type·확장자는 믿지 않는다 — 여기서 나온 값만 저장·응답에 쓴다.
 * 서버는 재인코딩·리사이즈를 하지 않으므로 크기(width/height)는 헤더에서 읽히면 채우고 아니면 비운다.
 */
public final class ImageSniffer {

    /** 판별 결과. 크기를 읽지 못하면 width·height 는 null 이다. */
    public record ImageType(String contentType, String extension, Integer width, Integer height) {}

    private ImageSniffer() {
    }

    /**
     * 미지원 형식이면 null.
     *
     * @param head 파일 앞 최대 64KB
     */
    public static ImageType sniff(byte[] head) {
        if (head == null) {
            return null;
        }
        if (isJpeg(head)) {
            return jpeg(head);
        }
        if (isPng(head)) {
            return png(head);
        }
        if (isWebp(head)) {
            return new ImageType("image/webp", "webp", null, null);
        }
        return null;
    }

    // ── JPEG ────────────────────────────────────────────────────────────────

    private static boolean isJpeg(byte[] head) {
        return head.length >= 3 && u(head, 0) == 0xFF && u(head, 1) == 0xD8 && u(head, 2) == 0xFF;
    }

    /** SOI 다음 세그먼트를 순회해 SOF0/SOF1/SOF2 에서 높이·너비(big-endian)를 읽는다. */
    private static ImageType jpeg(byte[] head) {
        int i = 2;
        while (i + 1 < head.length) {
            if (u(head, i) != 0xFF) {           // 세그먼트 경계가 아니면 더 볼 것이 없다
                break;
            }
            int marker = u(head, i + 1);
            if (marker == 0xFF) {               // 패딩 바이트
                i++;
                continue;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD8)) {   // 길이가 없는 마커
                i += 2;
                continue;
            }
            if (marker == 0xD9 || marker == 0xDA) {   // EOI / SOS — 뒤는 엔트로피 데이터
                break;
            }
            if (i + 3 >= head.length) {
                break;
            }
            int length = (u(head, i + 2) << 8) | u(head, i + 3);
            if (length < 2) {
                break;
            }
            if (marker == 0xC0 || marker == 0xC1 || marker == 0xC2) {
                if (i + 8 >= head.length) {
                    break;                       // SOF 를 만났지만 헤더가 잘려 크기를 못 읽는다
                }
                int height = (u(head, i + 5) << 8) | u(head, i + 6);
                int width = (u(head, i + 7) << 8) | u(head, i + 8);
                return new ImageType("image/jpeg", "jpg", width, height);
            }
            i += 2 + length;
        }
        return new ImageType("image/jpeg", "jpg", null, null);
    }

    // ── PNG ─────────────────────────────────────────────────────────────────

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private static boolean isPng(byte[] head) {
        if (head.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (head[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    /** IHDR 은 시그니처 바로 뒤에 오도록 규격이 강제한다 — 오프셋 16~23 이 너비·높이. */
    private static ImageType png(byte[] head) {
        if (head.length < 24) {
            return new ImageType("image/png", "png", null, null);
        }
        int width = int32(head, 16);
        int height = int32(head, 20);
        if (width <= 0 || height <= 0) {
            return new ImageType("image/png", "png", null, null);
        }
        return new ImageType("image/png", "png", width, height);
    }

    // ── WebP ────────────────────────────────────────────────────────────────

    /** RIFF 컨테이너의 4~7 바이트는 파일 크기라 형식 판별에 쓰지 않는다. */
    private static boolean isWebp(byte[] head) {
        return head.length >= 12
                && u(head, 0) == 'R' && u(head, 1) == 'I' && u(head, 2) == 'F' && u(head, 3) == 'F'
                && u(head, 8) == 'W' && u(head, 9) == 'E' && u(head, 10) == 'B' && u(head, 11) == 'P';
    }

    // ── 공통 ────────────────────────────────────────────────────────────────

    private static int u(byte[] bytes, int index) {
        return bytes[index] & 0xFF;
    }

    private static int int32(byte[] bytes, int index) {
        return (u(bytes, index) << 24) | (u(bytes, index + 1) << 16)
                | (u(bytes, index + 2) << 8) | u(bytes, index + 3);
    }
}
