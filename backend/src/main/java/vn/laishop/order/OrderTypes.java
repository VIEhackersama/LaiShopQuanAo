package vn.laishop.order;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import vn.laishop.cart.CartTypes.CartResponse;

public final class OrderTypes {
    private OrderTypes() { }
    public enum PaymentMethod { COD, BANK_TRANSFER }
    public record PlaceOrderRequest(@NotBlank @Size(max=120) String receiverName,@NotBlank @Pattern(regexp="[0-9]{10}") String receiverPhone,@NotBlank @Size(max=500) String shippingAddress,@NotNull PaymentMethod paymentMethod,@PositiveOrZero long cartVersion,@io.swagger.v3.oas.annotations.media.Schema(type="string",example="209000.00") @NotNull @DecimalMin("0") java.math.BigDecimal expectedTotal) { }
    public record BankInfo(String name,String account,String holder) { }
    public record CheckoutResponse(CartResponse cart,String receiverName,String receiverPhone,String shippingAddress,BankInfo bank,boolean bankTransferAvailable) { }
    public record OrderLine(String productName,String sku,String size,String color,String unitPrice,int quantity,String lineTotal) { }
    public record OrderResponse(long id,String orderNumber,String status,Instant createdAt,String receiverName,String receiverPhone,String shippingAddress,String totalAmount,PaymentMethod paymentMethod,String paymentStatus,List<OrderLine> items,BankInfo bank,String transferContent) { }
    public record OrderPage(List<OrderResponse> items,int page,int pageSize,long total) { }
}
