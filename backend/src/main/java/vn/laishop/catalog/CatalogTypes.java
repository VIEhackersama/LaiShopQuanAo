package vn.laishop.catalog;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class CatalogTypes {
    private CatalogTypes() { }
    public enum Sort { RELEVANCE, PRICE_ASC, PRICE_DESC, NEWEST, NAME_ASC }
    public record Conditions(Long categoryId, @Size(max=20) List<@Size(max=20) String> sizes, @Size(max=20) List<@Size(max=50) String> colors, @DecimalMin("0") BigDecimal minPrice, @DecimalMin("0") BigDecimal maxPrice, Sort sort) { }
    public record CategoryResponse(long id, String name) { }
    public record ProductCard(long id, String name, String description, String imageUrl, String minPrice, String maxPrice, List<String> colors, boolean inStock) { }
    public record ProductPage(List<ProductCard> items, int page, int pageSize, long total) { }
    public record VariantResponse(long id, String sku, String size, String color, String price, int stockQuantity, boolean active) { }
    public record ImageResponse(String imageUrl, String altText) { }
    public record ProductResponse(long id, String name, String description, String category, List<VariantResponse> variants, List<ImageResponse> images) { }
    public record Candidate(long id, int rank, BigDecimal score) { }
}
