package app.bookey.domain.post;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostTest {

    private static final Long OWNER = 10L;

    private Post post(PostVisibility visibility) {
        return Post.builder()
                .userId(OWNER)
                .bookId(5L)
                .readingRecordId(50L)
                .slug("독후감")
                .title("제목")
                .bodyMd("본문")
                .visibility(visibility)
                .build();
    }

    @Test
    @DisplayName("공개 독후감은 소유자·타인·비로그인 모두 읽을 수 있다")
    void publicIsReadableByEveryone() {
        Post post = post(PostVisibility.PUBLIC);

        assertThat(post.isReadableBy(OWNER)).isTrue();
        assertThat(post.isReadableBy(99L)).isTrue();
        assertThat(post.isReadableBy(null)).isTrue();
    }

    @Test
    @DisplayName("링크 공개 독후감도 링크를 아는 누구나 읽을 수 있다")
    void linkIsReadableByEveryone() {
        Post post = post(PostVisibility.LINK);

        assertThat(post.isReadableBy(OWNER)).isTrue();
        assertThat(post.isReadableBy(99L)).isTrue();
        assertThat(post.isReadableBy(null)).isTrue();
    }

    @Test
    @DisplayName("비공개 독후감은 소유자만 읽을 수 있다")
    void privateIsReadableByOwnerOnly() {
        Post post = post(PostVisibility.PRIVATE);

        assertThat(post.isReadableBy(OWNER)).isTrue();
        assertThat(post.isReadableBy(99L)).isFalse();
        assertThat(post.isReadableBy(null)).isFalse();
    }

    @Test
    @DisplayName("changeBook 은 책을 바꾸고, null 이면 무시한다(책 제거 불가)")
    void changeBookIgnoresNull() {
        Post post = post(PostVisibility.PRIVATE);

        post.changeBook(7L);
        assertThat(post.getBookId()).isEqualTo(7L);

        post.changeBook(null);
        assertThat(post.getBookId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("changeBook 으로 책이 실제로 바뀌면 이전 책의 독서 기록 연결을 끊는다")
    void changeBookClearsReadingRecord() {
        Post post = post(PostVisibility.PRIVATE);
        assertThat(post.getReadingRecordId()).isEqualTo(50L);

        post.changeBook(7L);

        assertThat(post.getBookId()).isEqualTo(7L);
        assertThat(post.getReadingRecordId()).isNull();
    }

    @Test
    @DisplayName("changeBook 에 같은 책을 주면 독서 기록 연결을 그대로 둔다")
    void changeBookKeepsReadingRecordForSameBook() {
        Post post = post(PostVisibility.PRIVATE);

        post.changeBook(5L);

        assertThat(post.getBookId()).isEqualTo(5L);
        assertThat(post.getReadingRecordId()).isEqualTo(50L);
    }

    @Test
    @DisplayName("changeBook 에 null 을 주면 책도 독서 기록도 건드리지 않는다")
    void changeBookIgnoresNullEntirely() {
        Post post = post(PostVisibility.PRIVATE);

        post.changeBook(null);

        assertThat(post.getBookId()).isEqualTo(5L);
        assertThat(post.getReadingRecordId()).isEqualTo(50L);
    }

    @Test
    @DisplayName("edit 은 책을 건드리지 않는다")
    void editKeepsBook() {
        Post post = post(PostVisibility.PRIVATE);

        post.edit("새 제목", "새 본문", new String[]{"태그"});

        assertThat(post.getTitle()).isEqualTo("새 제목");
        assertThat(post.getBodyMd()).isEqualTo("새 본문");
        assertThat(post.getBookId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("hasBook 은 책이 연결되어 있는지 알려준다")
    void hasBookChecksBookId() {
        Post withBook = post(PostVisibility.PRIVATE);
        Post withoutBook = Post.builder()
                .userId(OWNER).slug("책없음").title("제목").bodyMd("본문").build();

        assertThat(withBook.hasBook()).isTrue();
        assertThat(withoutBook.hasBook()).isFalse();
    }
}
