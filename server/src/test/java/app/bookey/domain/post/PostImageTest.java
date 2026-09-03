package app.bookey.domain.post;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostImageTest {

    private PostImage image() {
        return PostImage.builder()
                .userId(10L)
                .storageKey("posts/10/2026/09/abc.jpg")
                .url("https://cdn/posts/10/2026/09/abc.jpg")
                .contentType("image/jpeg")
                .byteSize(1024)
                .width(800)
                .height(600)
                .build();
    }

    @Test
    @DisplayName("업로드 직후에는 독후감에 붙지 않은 상태다")
    void newImageIsDetached() {
        PostImage image = image();

        assertThat(image.isDetached()).isTrue();
        assertThat(image.getPostId()).isNull();
        assertThat(image.isOwnedBy(10L)).isTrue();
        assertThat(image.isOwnedBy(99L)).isFalse();
    }

    @Test
    @DisplayName("attach 는 독후감과 순서를 붙이고, detach 는 되돌린다")
    void attachAndDetach() {
        PostImage image = image();

        image.attach(7L, 2);
        assertThat(image.isDetached()).isFalse();
        assertThat(image.getPostId()).isEqualTo(7L);
        assertThat(image.getSortOrder()).isEqualTo((short) 2);

        image.detach();
        assertThat(image.isDetached()).isTrue();
        assertThat(image.getPostId()).isNull();
        assertThat(image.getSortOrder()).isEqualTo((short) 0);
    }
}
