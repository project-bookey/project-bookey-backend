package app.bookey.common.config;

import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * OpenAPI 문서에 필수 필드를 표시한다.
 *
 * <p>springdoc 은 자바 레코드의 널 가능성을 알지 못해 모든 속성을 선택 항목으로 내보낸다.
 * 그러면 생성된 클라이언트 타입이 전부 {@code ?} 가 되어, 서버가 늘 채워 보내는 값에도
 * 방어 코드를 쓰게 된다. 실제 버그를 가리는 잡음이다.
 *
 * <p>그래서 다음 세 경우를 필수로 표시한다.
 * <ul>
 *   <li>원시 타입 컴포넌트 — 자바가 널을 담을 수 없으므로 언제나 값이 있다</li>
 *   <li>컬렉션·배열 — 이 서버는 비어 있을 때 널 대신 빈 목록을 돌려준다</li>
 *   <li>{@code @NotNull} · {@code @NotBlank} 가 붙은 컴포넌트 — 작성자가 명시한 계약</li>
 * </ul>
 *
 * <p>그 밖의 참조 타입은 널일 수 있으므로 선택 항목으로 남긴다. 특정 필드를 필수로 알리고 싶으면
 * 레코드 컴포넌트에 {@code @NotNull} 을 붙이면 된다.
 *
 * <p>반대로 컬렉션이지만 "생략 = 유지, 빈 목록 = 비움" 처럼 널에 뜻이 있는 요청 필드는
 * {@code @Schema(requiredMode = NOT_REQUIRED)} 로 필수 표시를 거부한다(예: {@code UpdatePostRequest.tags}).
 * 옵트아웃이 붙은 컴포넌트는 이 보정에서 제외된다 — {@code @NotNull} 과 같이 쓰면 모순이니 같이 쓰지 않는다.
 */
@Slf4j
@Configuration
public class OpenApiRequiredFieldsConfig {

    private static final String DTO_BASE_PACKAGE = "app.bookey";

    @Bean
    public OpenApiCustomizer recordRequiredFieldsCustomizer() {
        Map<String, Class<?>> records = scanRecords();
        log.debug("OpenAPI 필수 필드 후보 레코드 {}개", records.size());

        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            openApi.getComponents().getSchemas().forEach((name, schema) -> {
                Class<?> type = records.get(name);
                if (type == null || schema.getProperties() == null) {
                    return;
                }
                for (RecordComponent component : type.getRecordComponents()) {
                    if (!schema.getProperties().containsKey(component.getName())) {
                        continue;
                    }
                    if (isAlwaysPresent(component)) {
                        markRequired(schema, component.getName());
                    }
                }
            });
        };
    }

    static boolean isAlwaysPresent(RecordComponent component) {
        if (isOptedOut(component)) {
            return false;
        }
        Class<?> type = component.getType();
        return type.isPrimitive()
                || type.isArray()
                || Collection.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type)
                || component.isAnnotationPresent(NotNull.class)
                || component.isAnnotationPresent(NotBlank.class);
    }

    /**
     * {@code @Schema(requiredMode = NOT_REQUIRED)} 가 붙은 컴포넌트는 작성자가 선택 항목임을 명시한 것이다.
     * {@code @Schema} 는 레코드 컴포넌트 자체가 아니라 접근자·필드로 전파되므로 둘 다 본다.
     */
    private static boolean isOptedOut(RecordComponent component) {
        io.swagger.v3.oas.annotations.media.Schema annotation = schemaAnnotation(component);
        return annotation != null && annotation.requiredMode() == RequiredMode.NOT_REQUIRED;
    }

    private static io.swagger.v3.oas.annotations.media.Schema schemaAnnotation(RecordComponent component) {
        var onAccessor = component.getAccessor().getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
        if (onAccessor != null) {
            return onAccessor;
        }
        try {
            return component.getDeclaringRecord().getDeclaredField(component.getName())
                    .getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private void markRequired(Schema<?> schema, String property) {
        if (schema.getRequired() == null || !schema.getRequired().contains(property)) {
            schema.addRequiredItem(property);
        }
    }

    /**
     * 응답 DTO 는 대부분 중첩 레코드다(예: {@code ClubDtos.ClubHomeView}).
     * 클래스패스를 훑어 레코드만 골라 단순 이름으로 찾을 수 있게 만든다.
     * 단순 이름이 겹치면 OpenAPI 스키마 이름도 겹쳐 어느 쪽인지 특정할 수 없으므로 제외한다.
     */
    private Map<String, Class<?>> scanRecords() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(acceptAll());

        Map<String, Class<?>> found = new HashMap<>();
        Map<String, Integer> seen = new HashMap<>();

        for (var candidate : scanner.findCandidateComponents(DTO_BASE_PACKAGE)) {
            String className = candidate.getBeanClassName();
            if (className == null) {
                continue;
            }
            try {
                Class<?> type = Class.forName(className);
                if (!type.isRecord()) {
                    continue;
                }
                String simpleName = type.getSimpleName();
                seen.merge(simpleName, 1, Integer::sum);
                found.put(simpleName, type);
            } catch (Throwable ignored) {
                // 로딩할 수 없는 클래스는 문서 보정 대상이 아니다.
            }
        }
        seen.forEach((name, count) -> {
            if (count > 1) {
                found.remove(name);
                log.warn("DTO 이름이 겹쳐 필수 필드 보정에서 제외한다: {}", name);
            }
        });
        return found;
    }

    private TypeFilter acceptAll() {
        return (MetadataReader reader, MetadataReaderFactory factory) -> true;
    }
}
