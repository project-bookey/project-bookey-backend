package app.bookey.common.config;

import app.bookey.api.post.dto.PostDtos.PostView;
import app.bookey.api.post.dto.PostDtos.UpdatePostRequest;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @NotNull}·{@code @NotBlank} 는 swagger-core 가 자체적으로 required 처리하므로
 * 여기서는 이 보정이 직접 책임지는 규칙(원시 타입·컬렉션·옵트아웃)만 검증한다.
 */
class OpenApiRequiredFieldsConfigTest {

    private static Map<String, RecordComponent> componentsOf(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .collect(Collectors.toMap(RecordComponent::getName, Function.identity()));
    }

    /** springdoc 이 만드는 것과 같은 모양의 스키마 — 컴포넌트 이름마다 속성 하나, required 는 비어 있음. */
    private static Schema<?> schemaOf(Class<?> record) {
        ObjectSchema schema = new ObjectSchema();
        for (RecordComponent component : record.getRecordComponents()) {
            schema.addProperty(component.getName(), new StringSchema());
        }
        return schema;
    }

    @Test
    @DisplayName("UpdatePostRequest 의 tags·imageIds·quoteIds 는 NOT_REQUIRED 옵트아웃으로 필수에서 빠진다")
    void updatePostRequestCollectionsAreOptional() {
        Map<String, RecordComponent> components = componentsOf(UpdatePostRequest.class);

        assertThat(OpenApiRequiredFieldsConfig.isAlwaysPresent(components.get("tags"))).isFalse();
        assertThat(OpenApiRequiredFieldsConfig.isAlwaysPresent(components.get("imageIds"))).isFalse();
        assertThat(OpenApiRequiredFieldsConfig.isAlwaysPresent(components.get("quoteIds"))).isFalse();
    }

    @Test
    @DisplayName("옵트아웃이 없는 컬렉션·원시 타입 컴포넌트는 그대로 필수고, 그 밖의 참조 타입은 선택이다")
    void defaultRulesStillApply() {
        Map<String, RecordComponent> components = componentsOf(PostView.class);

        assertThat(OpenApiRequiredFieldsConfig.isAlwaysPresent(components.get("tags"))).isTrue();
        assertThat(OpenApiRequiredFieldsConfig.isAlwaysPresent(components.get("images"))).isTrue();
        assertThat(OpenApiRequiredFieldsConfig.isAlwaysPresent(components.get("quotes"))).isTrue();
        assertThat(OpenApiRequiredFieldsConfig.isAlwaysPresent(components.get("viewCount"))).isTrue();
        assertThat(OpenApiRequiredFieldsConfig.isAlwaysPresent(components.get("likedByMe"))).isTrue();
        assertThat(OpenApiRequiredFieldsConfig.isAlwaysPresent(components.get("bookId"))).isFalse();
        assertThat(OpenApiRequiredFieldsConfig.isAlwaysPresent(components.get("authorHandle"))).isFalse();
    }

    @Test
    @DisplayName("커스터마이저를 태우면 UpdatePostRequest 는 required 가 없고 PostView 는 컬렉션·원시 타입만 required 다")
    void customizerMarksRequiredPerRecord() {
        OpenAPI openApi = new OpenAPI().components(new Components()
                .addSchemas("UpdatePostRequest", schemaOf(UpdatePostRequest.class))
                .addSchemas("PostView", schemaOf(PostView.class)));

        new OpenApiRequiredFieldsConfig().recordRequiredFieldsCustomizer().customise(openApi);

        Schema<?> update = openApi.getComponents().getSchemas().get("UpdatePostRequest");
        assertThat(update.getRequired()).isNullOrEmpty();

        Schema<?> view = openApi.getComponents().getSchemas().get("PostView");
        assertThat(view.getRequired()).containsExactlyInAnyOrder(
                "tags", "viewCount", "images", "quotes", "likeCount", "likedByMe", "commentCount", "mine");
    }
}
