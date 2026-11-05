package vn.laishop.common;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {
    @Bean OpenAPI shopApi() { return new OpenAPI().info(new Info().title("LaiShopQuanAo API").version("1.0.0").description("Single-shop storefront. Cookie session authentication and CSRF header from /api/v1/auth/csrf are required for mutations. Money is a decimal string; checkout requires Idempotency-Key. Media providers are intentionally unconfigured.")); }
    @Bean OpenApiCustomizer errors() {
        return api -> {
            ObjectSchema schema=new ObjectSchema();
            schema.addProperty("status",new IntegerSchema()); schema.addProperty("code",new StringSchema()); schema.addProperty("detail",new StringSchema()); schema.addProperty("traceId",new StringSchema());
            schema.setRequired(java.util.List.of("status","code","detail"));
            api.getComponents().addSchemas("ApiProblem",schema);
            api.getPaths().values().forEach(path->path.readOperations().forEach(operation->{
                for(String status:java.util.List.of("400","401","403","404","409","413","429","500","503")) operation.getResponses().addApiResponse(status,new ApiResponse().description("Problem Details; inspect code, detail, traceId and optional fieldErrors.").content(new Content().addMediaType("application/problem+json",new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiProblem")))));
            }));
        };
    }
}
