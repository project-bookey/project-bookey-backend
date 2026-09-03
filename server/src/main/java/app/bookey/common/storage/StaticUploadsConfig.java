package app.bookey.common.storage;

import app.bookey.common.config.BookeyProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * 로컬 디스크 저장소일 때 업로드 파일을 /uploads/** 로 서빙한다.
 * 인증 없이 열려야 하므로 SecurityConfig 에서 GET /uploads/** 를 permitAll 로 둔다.
 */
@Configuration
@ConditionalOnProperty(prefix = "bookey.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class StaticUploadsConfig implements WebMvcConfigurer {

    private final String location;

    public StaticUploadsConfig(BookeyProperties properties) {
        Path dir = Path.of(properties.storage().local().dir()).toAbsolutePath().normalize();
        String uri = dir.toUri().toString();
        // 디렉터리가 아직 없으면 toUri() 가 끝 슬래시를 붙이지 않는다 — 없으면 파일 하나로 취급돼 서빙이 깨진다.
        this.location = uri.endsWith("/") ? uri : uri + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
