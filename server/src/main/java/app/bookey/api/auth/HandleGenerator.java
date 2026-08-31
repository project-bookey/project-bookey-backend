package app.bookey.api.auth;

import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;

/** 공개 블로그 주소(bookey.app/@handle)에 쓰이는 핸들 생성기 (§F7). */
@Component
@RequiredArgsConstructor
public class HandleGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 20;

    private final UserRepository userRepository;

    public String generate(String seed) {
        String base = normalize(seed);
        if (base.isEmpty()) {
            base = "reader";
        }
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String candidate = i == 0 ? base : base + RANDOM.nextInt(1000, 9999);
            if (candidate.length() <= 30 && !userRepository.existsByHandle(candidate)) {
                return candidate;
            }
        }
        return "reader" + System.nanoTime() % 1_000_000_000L;
    }

    private String normalize(String seed) {
        if (seed == null) {
            return "";
        }
        String s = seed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        return s.length() > 20 ? s.substring(0, 20) : s;
    }
}
