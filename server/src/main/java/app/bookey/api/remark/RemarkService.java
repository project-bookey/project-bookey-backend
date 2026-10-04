package app.bookey.api.remark;

import app.bookey.api.remark.dto.RemarkDtos.RemarkView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.reading.ReadingRecord;
import app.bookey.domain.reading.ReadingRecordRepository;
import app.bookey.domain.remark.BookRemark;
import app.bookey.domain.remark.BookRemarkRepository;
import app.bookey.domain.remark.RemarkKind;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 한 마디 — 완독·하차 때 남기는 한 줄. 읽기 기록마다 하나, 도서 상세에 최신순으로 돈다. */
@Service
@RequiredArgsConstructor
public class RemarkService {

    /** 도서 상세에서 돌리는 개수 상한 — 한 장씩 넘겨 보는 자리라 오래된 것까지 다 내릴 필요는 없다. */
    static final int MAX_LIST_SIZE = 30;

    private final BookRemarkRepository remarkRepository;
    private final ReadingRecordRepository recordRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<RemarkView> listByBook(Long bookId, int size) {
        int limit = Math.max(1, Math.min(size, MAX_LIST_SIZE));
        List<BookRemark> remarks = remarkRepository
                .findAllByBookIdOrderByWrittenAtDescIdDesc(bookId, PageRequest.of(0, limit));
        Map<Long, User> authors = loadAuthors(remarks);
        return remarks.stream().map(remark -> toView(remark, authors.get(remark.getUserId()))).toList();
    }

    /** 이 읽기 기록에 남긴 내 한 마디 — 없으면 null. */
    @Transactional(readOnly = true)
    public RemarkView mine(Long userId, Long recordId) {
        ownedRecord(userId, recordId);
        return remarkRepository.findByReadingRecordId(recordId)
                .map(remark -> toView(remark, userRepository.findById(userId).orElse(null)))
                .orElse(null);
    }

    /** 남기기 — 이미 남겼으면 고친다. 다 읽었거나 하차한 기록에만 남길 수 있다. */
    @Transactional
    public RemarkView write(Long userId, Long recordId, String body) {
        ReadingRecord record = ownedRecord(userId, recordId);
        RemarkKind kind = RemarkKind.of(record.getStatus());
        Instant now = Instant.now();
        BookRemark remark = remarkRepository.findByReadingRecordId(recordId)
                .map(existing -> {
                    existing.rewrite(kind, body, now);
                    return existing;
                })
                .orElseGet(() -> remarkRepository.save(BookRemark.builder()
                        .userId(userId)
                        .bookId(record.getBookId())
                        .readingRecordId(recordId)
                        .kind(kind)
                        .body(body)
                        .writtenAt(now)
                        .build()));
        return toView(remark, userRepository.findById(userId).orElse(null));
    }

    /** 지우기 — 이미 없으면 그대로 둔다(두 번 눌러도 같은 결과). */
    @Transactional
    public void delete(Long userId, Long recordId) {
        ownedRecord(userId, recordId);
        remarkRepository.findByReadingRecordId(recordId).ifPresent(remarkRepository::delete);
    }

    private ReadingRecord ownedRecord(Long userId, Long recordId) {
        ReadingRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> ApiException.of(ErrorCode.RECORD_NOT_FOUND));
        if (!record.isOwnedBy(userId)) {
            throw ApiException.of(ErrorCode.FORBIDDEN);
        }
        return record;
    }

    private Map<Long, User> loadAuthors(List<BookRemark> remarks) {
        List<Long> ids = remarks.stream().map(BookRemark::getUserId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /** 탈퇴한 작성자는 리뷰와 같이 "알 수 없음". */
    static RemarkView toView(BookRemark remark, User author) {
        return new RemarkView(
                remark.getId(), remark.getBookId(), remark.getUserId(),
                author == null ? "알 수 없음" : author.getNickname(),
                remark.getKind(), remark.getBody(), remark.getWrittenAt());
    }
}
