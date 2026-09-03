package app.bookey.common.storage;

import app.bookey.common.storage.ImageSniffer.ImageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 매직넘버 판별은 손으로 만든 최소 바이트열로 검증한다.
 * 클라이언트가 보내는 Content-Type·파일명은 믿지 않으므로 여기서 나오는 값이 유일한 근거다.
 */
class ImageSnifferTest {

    /** 바이트 리터럴을 짧게 쓰기 위한 헬퍼. */
    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static final byte[] SOI = bytes(0xFF, 0xD8);

    /** APP0(JFIF) 세그먼트 — 길이 0x0010 = 자기 길이 2 + 페이로드 14. */
    private static final byte[] APP0_JFIF = bytes(
            0xFF, 0xE0, 0x00, 0x10,
            0x4A, 0x46, 0x49, 0x46, 0x00,   // "JFIF\0"
            0x01, 0x01,                     // 버전
            0x00,                           // 단위
            0x00, 0x01, 0x00, 0x01,         // 밀도
            0x00, 0x00);                    // 썸네일 없음

    /** EXIF APP1 세그먼트 — 길이 0x000C = 자기 길이 2 + 페이로드 10. */
    private static final byte[] APP1_EXIF = bytes(
            0xFF, 0xE1, 0x00, 0x0C,
            0x45, 0x78, 0x69, 0x66, 0x00, 0x00,   // "Exif\0\0"
            0x00, 0x00, 0x00, 0x00);              // TIFF 헤더 자리(내용은 판별에 쓰지 않는다)

    /** SOF 세그먼트 — 길이 0x0011 = 자기 길이 2 + 정밀도 1 + 높이 2 + 너비 2 + 성분 수 1 + 성분 3×3. */
    private static byte[] sof(int marker, int height, int width) {
        return bytes(0xFF, marker, 0x00, 0x11,
                0x08,
                (height >> 8) & 0xFF, height & 0xFF,
                (width >> 8) & 0xFF, width & 0xFF,
                0x03,
                0x01, 0x22, 0x00,
                0x02, 0x11, 0x01,
                0x03, 0x11, 0x01);
    }

    @Test
    @DisplayName("JPEG(APP0 + SOF0)은 image/jpeg 로 판별하고 SOF0 에서 640×480 을 읽는다")
    void sniffsBaselineJpeg() {
        ImageType type = ImageSniffer.sniff(concat(SOI, APP0_JFIF, sof(0xC0, 480, 640)));

        assertThat(type).isNotNull();
        assertThat(type.contentType()).isEqualTo("image/jpeg");
        assertThat(type.extension()).isEqualTo("jpg");
        assertThat(type.width()).isEqualTo(640);
        assertThat(type.height()).isEqualTo(480);
    }

    @Test
    @DisplayName("EXIF(APP1)가 앞선 프로그레시브 JPEG 도 세그먼트를 건너뛰어 SOF2 에서 320×240 을 읽는다")
    void skipsExifSegmentAndReadsProgressiveSof() {
        ImageType type = ImageSniffer.sniff(concat(SOI, APP1_EXIF, sof(0xC2, 240, 320)));

        assertThat(type).isNotNull();
        assertThat(type.contentType()).isEqualTo("image/jpeg");
        assertThat(type.width()).isEqualTo(320);
        assertThat(type.height()).isEqualTo(240);
    }

    @Test
    @DisplayName("SOF1(확장 시퀀셜) 마커도 크기를 읽는다")
    void readsExtendedSequentialSof() {
        ImageType type = ImageSniffer.sniff(concat(SOI, APP0_JFIF, sof(0xC1, 100, 200)));

        assertThat(type).isNotNull();
        assertThat(type.width()).isEqualTo(200);
        assertThat(type.height()).isEqualTo(100);
    }

    @Test
    @DisplayName("앞 바이트 안에 SOF 가 없는 JPEG 은 형식만 판별하고 크기는 비운다")
    void jpegWithoutSofHasNullSize() {
        ImageType type = ImageSniffer.sniff(concat(SOI, APP0_JFIF));

        assertThat(type).isNotNull();
        assertThat(type.contentType()).isEqualTo("image/jpeg");
        assertThat(type.width()).isNull();
        assertThat(type.height()).isNull();
    }

    @Test
    @DisplayName("PNG 는 IHDR 에서 800×600 을 읽는다")
    void sniffsPng() {
        byte[] png = concat(
                bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),   // 시그니처
                bytes(0x00, 0x00, 0x00, 0x0D),                            // IHDR 길이 13
                bytes(0x49, 0x48, 0x44, 0x52),                            // "IHDR"
                bytes(0x00, 0x00, 0x03, 0x20),                            // 너비 800
                bytes(0x00, 0x00, 0x02, 0x58),                            // 높이 600
                bytes(0x08, 0x06, 0x00, 0x00, 0x00));

        ImageType type = ImageSniffer.sniff(png);

        assertThat(type).isNotNull();
        assertThat(type.contentType()).isEqualTo("image/png");
        assertThat(type.extension()).isEqualTo("png");
        assertThat(type.width()).isEqualTo(800);
        assertThat(type.height()).isEqualTo(600);
    }

    @Test
    @DisplayName("WebP 는 형식만 판별하고 크기는 비운다")
    void sniffsWebpWithoutSize() {
        byte[] webp = concat(
                bytes(0x52, 0x49, 0x46, 0x46),   // "RIFF"
                bytes(0x2A, 0x00, 0x00, 0x00),   // 파일 크기(판별에 쓰지 않는다)
                bytes(0x57, 0x45, 0x42, 0x50),   // "WEBP"
                bytes(0x56, 0x50, 0x38, 0x20));  // "VP8 "

        ImageType type = ImageSniffer.sniff(webp);

        assertThat(type).isNotNull();
        assertThat(type.contentType()).isEqualTo("image/webp");
        assertThat(type.extension()).isEqualTo("webp");
        assertThat(type.width()).isNull();
        assertThat(type.height()).isNull();
    }

    @Test
    @DisplayName("RIFF 로 시작해도 WEBP 가 아니면 거부한다")
    void rejectsNonWebpRiff() {
        byte[] wav = concat(
                bytes(0x52, 0x49, 0x46, 0x46),
                bytes(0x2A, 0x00, 0x00, 0x00),
                bytes(0x57, 0x41, 0x56, 0x45));  // "WAVE"

        assertThat(ImageSniffer.sniff(wav)).isNull();
    }

    @Test
    @DisplayName("텍스트·빈 바이트·너무 짧은 바이트는 모두 거부한다")
    void rejectsUnsupportedBytes() {
        assertThat(ImageSniffer.sniff("hello world".getBytes())).isNull();
        assertThat(ImageSniffer.sniff(new byte[0])).isNull();
        assertThat(ImageSniffer.sniff(bytes(0xFF, 0xD8))).isNull();
        assertThat(ImageSniffer.sniff(bytes(0x89, 0x50, 0x4E))).isNull();
        assertThat(ImageSniffer.sniff(null)).isNull();
    }
}
