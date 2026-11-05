package vn.laishop.search.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import vn.laishop.catalog.CatalogTypes.*;

public final class AiTypes {
    private AiTypes() { }
    public record ChatRequest(UUID conversationId,@PositiveOrZero Long expectedVersion,@NotBlank @Size(max=500) String message) { }
    public record SearchArguments(@NotNull @Size(max=6) List<@NotBlank @Size(max=60) String> terms,Long categoryId,@NotNull @Size(max=10) List<@NotBlank @Size(max=20) String> sizes,@NotNull @Size(max=10) List<@NotBlank @Size(max=50) String> colors,@DecimalMin("0") BigDecimal minPrice,@DecimalMin("0") BigDecimal maxPrice,@NotNull Sort sort,@NotNull Boolean inStockOnly) { }
    public record Clarification(@NotBlank @Size(max=500) String question) { }
    public record Message(String role,String content,Long queryId) { }
    public record Conversation(UUID id,long version,SearchArguments state,List<Message> messages) { }
    public record ChatResponse(UUID conversationId,long version,String reply,SearchArguments appliedFilters,Long queryId,ProductPage products,String provider,String model) { }
    public record AiCapabilities(boolean enabled,String provider,String model,String description) { }
    public record TurnContext(List<CategoryResponse> categories,List<String> sizes,List<String> colors,SearchArguments previous,List<Message> messages,String userMessage) { }
    public sealed interface Decision permits SearchDecision,ClarifyDecision { }
    public record SearchDecision(@Valid SearchArguments arguments) implements Decision { }
    public record ClarifyDecision(@Valid Clarification arguments) implements Decision { }
}
