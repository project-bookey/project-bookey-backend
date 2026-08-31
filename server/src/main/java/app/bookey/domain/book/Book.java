package app.bookey.domain.book;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Entity
@Table(name = "books")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Book extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 13, unique = true)
    private String isbn13;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 500)
    private String subtitle;

    @Column(length = 500)
    private String author;

    @Column(length = 255)
    private String translator;

    @Column(length = 255)
    private String publisher;

    @Column(name = "published_at")
    private LocalDate publishedAt;

    /** 진척도 계산의 핵심. 외부 API 에 없으면 사용자 입력으로 채운다(§F1). */
    @Column(name = "total_pages")
    private Integer totalPages;

    @Column(name = "cover_url")
    private String coverUrl;

    @Column(length = 255)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "genre_key", nullable = false, length = 30)
    private GenreKey genreKey = GenreKey.GENERAL;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookSource source = BookSource.MANUAL;

    @Column(name = "is_user_created", nullable = false)
    private boolean userCreated;

    @Column(name = "meta_enriched_at")
    private Instant metaEnrichedAt;

    @Builder
    private Book(String isbn13, String title, String subtitle, String author, String translator,
                 String publisher, LocalDate publishedAt, Integer totalPages, String coverUrl,
                 String category, String description, BookSource source, boolean userCreated) {
        this.isbn13 = isbn13;
        this.title = title;
        this.subtitle = subtitle;
        this.author = author;
        this.translator = translator;
        this.publisher = publisher;
        this.publishedAt = publishedAt;
        this.totalPages = totalPages;
        this.coverUrl = coverUrl;
        this.category = category;
        this.genreKey = GenreKey.fromCategory(category);
        this.description = description;
        this.source = source == null ? BookSource.MANUAL : source;
        this.userCreated = userCreated;
    }

    /** 알라딘 등 2차 API 로 부족한 메타를 채운다(§F1 검색 파이프라인 3번). 기존 값은 덮어쓰지 않는다. */
    public void enrichMeta(Integer totalPages, String category, String description,
                           String coverUrl, LocalDate publishedAt) {
        if (this.totalPages == null && totalPages != null && totalPages > 0) {
            this.totalPages = totalPages;
        }
        if (this.category == null && category != null) {
            this.category = category;
            this.genreKey = GenreKey.fromCategory(category);
        }
        if (this.description == null && description != null) {
            this.description = description;
        }
        if (this.coverUrl == null && coverUrl != null) {
            this.coverUrl = coverUrl;
        }
        if (this.publishedAt == null && publishedAt != null) {
            this.publishedAt = publishedAt;
        }
        this.metaEnrichedAt = Instant.now();
    }

    public void markEnrichAttempted() {
        this.metaEnrichedAt = Instant.now();
    }

    /** 관리자 메타 수정 (§F13 도서 관리). */
    public void updateByAdmin(String title, String author, String publisher,
                              Integer totalPages, String coverUrl, String category) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
        if (author != null) {
            this.author = author;
        }
        if (publisher != null) {
            this.publisher = publisher;
        }
        if (totalPages != null && totalPages > 0) {
            this.totalPages = totalPages;
        }
        if (coverUrl != null) {
            this.coverUrl = coverUrl;
        }
        if (category != null) {
            this.category = category;
            this.genreKey = GenreKey.fromCategory(category);
        }
    }

    public void applyCrowdPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public boolean hasTotalPages() {
        return totalPages != null && totalPages > 0;
    }
}
