package app.bookey.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bookeyOpenApi() {
        SecurityScheme bearer = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(new Info()
                        .title("bookey API")
                        .version("v1")
                        .description("독서 진척 관리 · 재촉 · 검증 리뷰 · 독서 모임"))
                .components(new Components().addSecuritySchemes("bearerAuth", bearer))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
