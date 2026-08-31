package app.bookey.support;

import app.bookey.domain.admin.Admin;
import app.bookey.domain.admin.AdminRepository;
import app.bookey.domain.admin.AdminRole;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.book.BookSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 로컬 개발 시드. prod 프로필에서는 동작하지 않는다.
 * 운영 환경의 최초 관리자 계정은 별도 마이그레이션·CLI 로 만든다.
 */
@Slf4j
@Component
@Profile("!prod")
@RequiredArgsConstructor
public class LocalDataSeeder implements ApplicationRunner {

    private static final String DEFAULT_ADMIN_EMAIL = "admin@bookey.local";

    private final AdminRepository adminRepository;
    private final BookRepository bookRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedAdmin();
        seedBooks();
    }

    private void seedAdmin() {
        if (adminRepository.existsByEmail(DEFAULT_ADMIN_EMAIL)) {
            return;
        }
        String password = System.getenv().getOrDefault("ADMIN_SEED_PASSWORD", "bookey-local-1234");
        adminRepository.save(new Admin(DEFAULT_ADMIN_EMAIL, passwordEncoder.encode(password),
                "로컬 관리자", AdminRole.SUPER_ADMIN));
        log.info("Seeded admin account: {} (password from ADMIN_SEED_PASSWORD, default bookey-local-1234)",
                DEFAULT_ADMIN_EMAIL);
    }

    /** 외부 API 키 없이도 검색·모임을 시험할 수 있도록 표본 도서를 넣는다. */
    private void seedBooks() {
        if (bookRepository.count() > 0) {
            return;
        }
        List<Book> samples = List.of(
                Book.builder().isbn13("9788934972464").title("사피엔스").author("유발 하라리")
                        .publisher("김영사").totalPages(636).category("인문학")
                        .publishedAt(LocalDate.of(2015, 11, 24)).source(BookSource.MANUAL).build(),
                Book.builder().isbn13("9788970127248").title("총, 균, 쇠").author("재레드 다이아몬드")
                        .publisher("문학사상").totalPages(752).category("인문학 > 역사")
                        .publishedAt(LocalDate.of(2005, 12, 19)).source(BookSource.MANUAL).build(),
                Book.builder().isbn13("9788932917245").title("아몬드").author("손원평")
                        .publisher("창비").totalPages(263).category("소설")
                        .publishedAt(LocalDate.of(2017, 3, 31)).source(BookSource.MANUAL).build(),
                Book.builder().isbn13("9791162241974").title("클린 아키텍처").author("로버트 C. 마틴")
                        .publisher("인사이트").totalPages(444).category("컴퓨터/IT")
                        .publishedAt(LocalDate.of(2019, 8, 20)).source(BookSource.MANUAL).build()
        );
        bookRepository.saveAll(samples);
        log.info("Seeded {} sample books", samples.size());
    }
}
