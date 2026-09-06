package app.bookey.api.book;

import app.bookey.api.book.client.Yes24Client;
import app.bookey.api.book.client.Yes24Client.CurationKind;
import app.bookey.api.book.client.Yes24Client.Yes24Item;
import app.bookey.api.book.dto.BookDtos.BookSummary;
import app.bookey.common.config.BookeyProperties;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * YES24 큐레이션 (베스트셀러·스테디셀러·신상품).
 * 순위 목록은 Redis 에 책 id 목록으로 1시간 캐시하고, 상품은 books 테이블에 upsert 해
 * 앱 어디서든 내부 책으로 다룬다(서재 담기·독후감·온보딩 재사용).
 * 키가 없거나 API 가 죽으면 빈 목록 — 앱은 해당 구역을 그냥 숨긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Yes24CurationService {

    private final Yes24Client yes24Client;
    private final BookSearchService bookSearchService;
    private final BookRepository bookRepository;
    private final StringRedisTemplate redis;
    private final BookeyProperties properties;

    @Transactional
    public List<BookSummary> curation(CurationKind kind, int size) {
        if (!yes24Client.isConfigured()) {
            return List.of();
        }
        String cacheKey = "yes24:curation:" + kind.name();
        List<BookSummary> cached = fromCache(cacheKey, size);
        if (!cached.isEmpty()) {
            return cached;
        }

        List<Yes24Item> items = yes24Client.curation(kind, size);
        if (items.isEmpty()) {
            return List.of();
        }
        List<Book> books = bookSearchService.upsertYes24(items);
        try {
            String ids = books.stream().map(b -> String.valueOf(b.getId()))
                    .collect(Collectors.joining(","));
            redis.opsForValue().set(cacheKey, ids, properties.bookApi().yes24CurationTtl());
        } catch (RedisConnectionFailureException e) {
            log.warn("YES24 큐레이션 캐시 저장 실패 — 캐시 없이 계속: {}", e.getMessage());
        }
        return books.stream().map(BookSummary::from).limit(size).toList();
    }

    /** 캐시된 id 목록 → 순서 유지해 책으로 복원. 캐시가 없거나 깨졌으면 빈 목록. */
    private List<BookSummary> fromCache(String cacheKey, int size) {
        String raw;
        try {
            raw = redis.opsForValue().get(cacheKey);
        } catch (RedisConnectionFailureException e) {
            return List.of();
        }
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<Long> ids = Arrays.stream(raw.split(",")).map(Long::parseLong).toList();
            Map<Long, Book> books = bookRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(Book::getId, Function.identity()));
            List<BookSummary> result = new ArrayList<>();
            for (Long id : ids) {
                Book book = books.get(id);
                if (book != null) {
                    result.add(BookSummary.from(book));
                }
                if (result.size() >= size) {
                    break;
                }
            }
            return result;
        } catch (NumberFormatException e) {
            return List.of();
        }
    }
}
