package app.bookey.admin;

import app.bookey.admin.dto.AdminCatalogDtos.AdminBookCreateRequest;
import app.bookey.admin.support.AdminAuditService;
import app.bookey.api.club.ClubService;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import app.bookey.domain.admin.AdminRole;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookPageSuggestionRepository;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.club.ClubBookRepository;
import app.bookey.domain.club.ClubMemberRepository;
import app.bookey.domain.club.ClubRepository;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminCatalogServiceTest {

    private static final AuthAdmin OPERATOR = new AuthAdmin(1L, "op@bookey.app", AdminRole.OPERATOR);
    private static final AuthAdmin SUPPORT = new AuthAdmin(2L, "cs@bookey.app", AdminRole.SUPPORT);

    private final BookRepository bookRepository = mock(BookRepository.class);
    private final AdminCatalogService service = new AdminCatalogService(bookRepository,
            mock(BookPageSuggestionRepository.class), mock(ClubRepository.class), mock(ClubBookRepository.class),
            mock(ClubMemberRepository.class), mock(UserRepository.class), mock(ClubService.class),
            mock(AdminAuditService.class), mock(JdbcTemplate.class));

    private static AdminBookCreateRequest request(String isbn) {
        return new AdminBookCreateRequest(isbn, "독립출판 시집", "작가", null, 120, null, null, "검색에 없는 책");
    }

    @Test
    @DisplayName("ISBN 은 하이픈·공백을 빼고 13자리 숫자만 받는다")
    void normalizeIsbn() {
        assertThat(AdminCatalogService.normalizeIsbn("978-89-01-23456-7")).isEqualTo("9788901234567");
        assertThat(AdminCatalogService.normalizeIsbn("  ")).isNull();
        assertThatThrownBy(() -> AdminCatalogService.normalizeIsbn("12345"))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("이미 있는 ISBN 이면 409 로 그 책을 알려 주고 만들지 않는다. CS 담당은 등록할 수 없다")
    void duplicateIsbnAndRole() {
        Book existing = Book.builder().isbn13("9788901234567").title("이미 있는 책").build();
        when(bookRepository.findByIsbn13("9788901234567")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createBook(OPERATOR, request("9788901234567")))
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
        assertThatThrownBy(() -> service.createBook(SUPPORT, request(null)))
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("병합은 최고 관리자만 — 운영자는 미리보기도 못 본다")
    void mergeNeedsSuper() {
        assertThatThrownBy(() -> service.mergePreview(OPERATOR, 1L, 2L))
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
    }
}
