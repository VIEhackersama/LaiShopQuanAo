package vn.laishop.cart;

import jakarta.validation.constraints.*;
import java.util.List;

public final class CartTypes {
    private CartTypes() { }
    public record AddItem(@Positive long variantId, @Min(1) @Max(10000) int quantity) { }
    public record UpdateItem(@Min(1) @Max(10000) int quantity) { }
    public record CartLine(long id,long variantId,String productName,String imageUrl,String sku,String size,String color,int quantity,String unitPrice,String lineTotal,int stockQuantity,List<String> warnings) { }
    public record CartResponse(Long id,long version,List<CartLine> items,int totalQuantity,String totalAmount) { }
}
