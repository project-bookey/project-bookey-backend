package app.bookey.api.remark;

import app.bookey.api.remark.dto.RemarkDtos.RemarkView;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.reading.ReadingRecord;
import app.bookey.domain.reading.ReadingRecordRepository;
import app.bookey.domain.reading.ReadingStatus;
import app.bookey.domain.remark.BookRemark;
import app.bookey.domain.remark.BookRemarkRepository;
import app.bookey.domain.remark.RemarkKind;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemarkServiceTest {

    private static final long USER_ID = 10L;
    private static final long RECORD_ID = 100L;
    private static final long BOOK_ID = 7L;

    private final BookRemarkRepository remarkRepository = mock(BookRemarkRepository.class);
    private final ReadingRecordRepository recordRepository = mock(ReadingRecordRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RemarkService service = new RemarkService(remarkRepository, recordRepository, userRepository);

    @Test
    @DisplayName("완독한 기록에 처음 남기면 '완독' 한 마디가 앞뒤 공백을 걷고 새로 생긴다")
    void writeCreatesFinishedRemark() {
        givenRecord(USER_ID, ReadingStatus.FINISHED);
        when(remarkRepository.findByReadingRecordId(RECORD_ID)).thenReturn(Optional.empty());
        when(remarkRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, "독자")));

        RemarkView view = service.write(USER_ID, RECORD_ID, "  마지막 장에서 한참 앉아 있었다 ");

        ArgumentCaptor<BookRemark> saved = ArgumentCaptor.forClass(BookRemark.class);
        verify(remarkRepository).save(saved.capture());
        assertThat(saved.getValue().getBookId()).isEqualTo(BOOK_ID);
        assertThat(saved.getValue().getReadingRecordId()).isEqualTo(RECORD_ID);
        assertThat(view.kind()).isEqualTo(RemarkKind.FINISHED);
        assertThat(view.body()).isEqualTo("마지막 장에서 한참 앉아 있었다");
        assertThat(view.authorNickname()).isEqualTo("독자");
    }

    @Test
    @DisplayName("이미 남긴 기록에 다시 쓰면 새로 만들지 않고 고친다 — 그때의 상태로 바뀌고 최신순 맨 앞으로 온다")
    void writeRewritesExisting() {
        givenRecord(USER_ID, ReadingStatus.ABANDONED);
        Instant before = Instant.parse("2026-09-01T00:00:00Z");
        BookRemark existing = BookRemark.builder().userId(USER_ID).bookId(BOOK_ID).readingRecordId(RECORD_ID)
                .kind(RemarkKind.FINISHED).body("처음 쓴 말").writtenAt(before).build();
        when(remarkRepository.findByReadingRecordId(RECORD_ID)).thenReturn(Optional.of(existing));

        RemarkView view = service.write(USER_ID, RECORD_ID, "지금의 나와는 맞지 않았다");

        verify(remarkRepository, never()).save(any());
        assertThat(view.kind()).isEqualTo(RemarkKind.ABANDONED);
        assertThat(view.body()).isEqualTo("지금의 나와는 맞지 않았다");
        assertThat(view.writtenAt()).isAfter(before);
    }

    @Test
    @DisplayName("아직 읽는 중인 책에는 남길 수 없다")
    void writeRefusesOpenRecord() {
        givenRecord(USER_ID, ReadingStatus.READING);

        assertThatThrownBy(() -> service.write(USER_ID, RECORD_ID, "벌써 좋다"))
                .extracting("errorCode").isEqualTo(ErrorCode.REMARK_NOT_CLOSED);
        verify(remarkRepository, never()).save(any());
    }

    @Test
    @DisplayName("남의 읽기 기록에는 남기거나 지울 수 없다")
    void writeRefusesOthersRecord() {
        givenRecord(99L, ReadingStatus.FINISHED);

        assertThatThrownBy(() -> service.write(USER_ID, RECORD_ID, "남의 책"))
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> service.delete(USER_ID, RECORD_ID))
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("지우기는 이미 없어도 그대로 끝난다")
    void deleteIsIdempotent() {
        givenRecord(USER_ID, ReadingStatus.FINISHED);
        when(remarkRepository.findByReadingRecordId(RECORD_ID)).thenReturn(Optional.empty());

        service.delete(USER_ID, RECORD_ID);

        verify(remarkRepository, never()).delete(any(BookRemark.class));
    }

    @Test
    @DisplayName("도서별 목록은 상한(30개)까지만 받고, 탈퇴한 작성자는 '알 수 없음'으로 내린다")
    void listCapsSizeAndNamesMissingAuthor() {
        BookRemark mine = BookRemark.builder().userId(USER_ID).bookId(BOOK_ID).readingRecordId(RECORD_ID)
                .kind(RemarkKind.FINISHED).body("좋았다").writtenAt(Instant.now()).build();
        BookRemark gone = BookRemark.builder().userId(55L).bookId(BOOK_ID).readingRecordId(101L)
                .kind(RemarkKind.ABANDONED).body("어려웠다").writtenAt(Instant.now()).build();
        when(remarkRepository.findAllByBookIdOrderByWrittenAtDescIdDesc(eq(BOOK_ID), any()))
                .thenReturn(List.of(mine, gone));
        when(userRepository.findAllById(any())).thenReturn(List.of(user(USER_ID, "독자")));

        List<RemarkView> views = service.listByBook(BOOK_ID, 500);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(remarkRepository).findAllByBookIdOrderByWrittenAtDescIdDesc(eq(BOOK_ID), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(RemarkService.MAX_LIST_SIZE);
        assertThat(views).extracting(RemarkView::authorNickname).containsExactly("독자", "알 수 없음");
    }

    private void givenRecord(long ownerId, ReadingStatus status) {
        ReadingRecord record = ReadingRecord.builder().userId(ownerId).bookId(BOOK_ID).status(status).build();
        set(record, "id", RECORD_ID);
        when(recordRepository.findById(RECORD_ID)).thenReturn(Optional.of(record));
    }

    private User user(long id, String nickname) {
        User user = User.builder().handle("handle" + id).nickname(nickname).build();
        set(user, "id", id);
        return user;
    }

    private void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
