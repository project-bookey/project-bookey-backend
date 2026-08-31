package app.bookey.api.book;

import app.bookey.api.book.client.AladinBookClient;
import app.bookey.api.book.client.ExternalBook;
import app.bookey.api.book.client.GoogleBooksClient;
import app.bookey.api.book.client.KakaoBookClient;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.book.BookSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 도서 검색 파이프라인 (§F1).
 *
 * <pre>
 * 1) 내부 캐시(books) 조회 ── hit ──→ 즉시 반환
 * 2) 카카오 책 검색
 * 3) ISBN13 으로 알라딘 ItemLookUp 비동기 호출 → 페이지 수/카테고리 보강
 * 4) 국내 결과 0건이면 Google Books 폴백
 * 5) books upsert (isbn13 unique) 후 반환
 * </pre>
 *
 * API 키는 서버에만 있고 클라이언트는 외부 API 를 직접 호출하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookSearchService {

    private static final int CACHE_HIT_THRESHOLD = 5;

    private final BookRepository bookRepository;
    private final KakaoBookClient kakaoClient;
    private final AladinBookClient aladinClient;
    private final GoogleBooksClient googleClient;
    private final BookMetaEnricher metaEnricher;

    @Transactional
    public List<Book> search(String keyword, int size) {
        // 1) 내부 캐시
        List<Book> cached = bookRepository
                .searchCache(keyword, PageRequest.of(0, size))
                .getContent();
        if (cached.size() >= CACHE_HIT_THRESHOLD) {
            metaEnricher.enrichMissingPagesAsync(idsNeedingEnrichment(cached));
            return cached;
        }

        // 2) 카카오
        List<ExternalBook> external = new ArrayList<>(kakaoClient.search(keyword, size));
        BookSource externalSource = BookSource.KAKAO;

        // 3) 카카오 결과가 없으면 알라딘 폴백
        if (external.isEmpty()) {
            external = new ArrayList<>(aladinClient.search(keyword, size));
            externalSource = BookSource.ALADIN;
        }

        // 4) 국내 결과가 없으면 구글 폴백
        if (external.isEmpty()) {
            external = new ArrayList<>(googleClient.search(keyword, size));
            externalSource = BookSource.GOOGLE;
        }

        if (external.isEmpty()) {
            // API 장애 시에도 캐시 결과는 그대로 반환한다 (graceful degradation)
            return cached;
        }

        // 5) upsert
        List<Book> merged = upsertAll(external, externalSource);

        // 3) 페이지 수 없는 책은 비동기로 알라딘 보강 — 검색 응답을 블로킹하지 않는다
        metaEnricher.enrichMissingPagesAsync(idsNeedingEnrichment(merged));

        LinkedHashMap<Long, Book> deduped = new LinkedHashMap<>();
        merged.forEach(b -> deduped.put(b.getId(), b));
        cached.forEach(b -> deduped.putIfAbsent(b.getId(), b));
        return new ArrayList<>(deduped.values()).subList(0, Math.min(deduped.size(), size));
    }

    /** ISBN 직접 조회 / 바코드 스캔 (§F1). */
    @Transactional
    public Optional<Book> findOrFetchByIsbn(String isbn13) {
        Optional<Book> cached = bookRepository.findByIsbn13(isbn13);
        if (cached.isPresent()) {
            Book book = cached.get();
            if (!book.hasTotalPages()) {
                aladinClient.lookupByIsbn13(isbn13).ifPresent(meta -> book.enrichMeta(
                        meta.totalPages(), meta.category(), meta.description(),
                        meta.coverUrl(), meta.publishedAt()));
            }
            return Optional.of(book);
        }

        Optional<ExternalBook> aladin = aladinClient.lookupByIsbn13(isbn13);
        if (aladin.isPresent()) {
            return Optional.of(upsert(aladin.get(), BookSource.ALADIN));
        }
        List<ExternalBook> kakao = kakaoClient.search(isbn13, 1);
        if (!kakao.isEmpty()) {
            return Optional.of(upsert(kakao.get(0), BookSource.KAKAO));
        }
        List<ExternalBook> google = googleClient.search("isbn:" + isbn13, 1);
        if (!google.isEmpty()) {
            return Optional.of(upsert(google.get(0), BookSource.GOOGLE));
        }
        return Optional.empty();
    }

    private List<Long> idsNeedingEnrichment(List<Book> books) {
        return books.stream()
                .filter(b -> !b.hasTotalPages() && b.getIsbn13() != null && b.getMetaEnrichedAt() == null)
                .map(Book::getId)
                .toList();
    }

    private List<Book> upsertAll(List<ExternalBook> externals, BookSource source) {
        List<String> isbns = externals.stream()
                .map(ExternalBook::isbn13)
                .filter(i -> i != null && !i.isBlank())
                .toList();
        Map<String, Book> existing = new LinkedHashMap<>();
        if (!isbns.isEmpty()) {
            bookRepository.findAllByIsbn13In(isbns).forEach(b -> existing.put(b.getIsbn13(), b));
        }

        List<Book> result = new ArrayList<>();
        for (ExternalBook external : externals) {
            if (external.title() == null || external.title().isBlank()) {
                continue;
            }
            Book book = external.isbn13() == null ? null : existing.get(external.isbn13());
            if (book == null) {
                book = bookRepository.save(toEntity(external, source));
                if (book.getIsbn13() != null) {
                    existing.put(book.getIsbn13(), book);
                }
            } else {
                book.enrichMeta(external.totalPages(), external.category(), external.description(),
                        external.coverUrl(), external.publishedAt());
            }
            result.add(book);
        }
        return result;
    }

    private Book upsert(ExternalBook external, BookSource source) {
        if (external.isbn13() != null) {
            Optional<Book> existing = bookRepository.findByIsbn13(external.isbn13());
            if (existing.isPresent()) {
                Book book = existing.get();
                book.enrichMeta(external.totalPages(), external.category(), external.description(),
                        external.coverUrl(), external.publishedAt());
                return book;
            }
        }
        return bookRepository.save(toEntity(external, source));
    }

    private Book toEntity(ExternalBook external, BookSource source) {
        return Book.builder()
                .isbn13(external.isbn13())
                .title(external.title())
                .subtitle(external.subtitle())
                .author(external.author())
                .translator(external.translator())
                .publisher(external.publisher())
                .publishedAt(external.publishedAt())
                .totalPages(external.totalPages())
                .coverUrl(external.coverUrl())
                .category(external.category())
                .description(external.description())
                .source(source)
                .userCreated(false)
                .build();
    }

    /** 비동기 메타 보강 워커. 별도 트랜잭션에서 동작한다. */
    @Service
    @RequiredArgsConstructor
    public static class BookMetaEnricher {

        private final BookRepository bookRepository;
        private final AladinBookClient aladinClient;

        @Async("bookMetaExecutor")
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void enrichMissingPagesAsync(List<Long> bookIds) {
            if (bookIds == null || bookIds.isEmpty() || !aladinClient.isConfigured()) {
                return;
            }
            for (Long bookId : bookIds) {
                bookRepository.findById(bookId).ifPresent(book -> {
                    if (book.hasTotalPages() || book.getIsbn13() == null) {
                        return;
                    }
                    aladinClient.lookupByIsbn13(book.getIsbn13())
                            .ifPresentOrElse(
                                    meta -> book.enrichMeta(meta.totalPages(), meta.category(),
                                            meta.description(), meta.coverUrl(), meta.publishedAt()),
                                    book::markEnrichAttempted);
                });
            }
        }
    }
}
