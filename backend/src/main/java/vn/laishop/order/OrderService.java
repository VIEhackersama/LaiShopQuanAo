package vn.laishop.order;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.laishop.cart.CartDao;
import vn.laishop.common.ApiException;
import static vn.laishop.order.OrderTypes.*;

@Service
public class OrderService {
    private final CartDao carts;
    private final JdbcTemplate jdbc;
    private final BankInfo bank;
    public OrderService(CartDao carts,JdbcTemplate jdbc,@Value("${app.bank.name}") String name,@Value("${app.bank.account}") String account,@Value("${app.bank.holder}") String holder) { this.carts=carts; this.jdbc=jdbc; this.bank=new BankInfo(name,account,holder); }
    private boolean bankAvailable() { return !bank.name().isBlank() && !bank.account().isBlank() && !bank.holder().isBlank(); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public CheckoutResponse checkout(long customer) {
        record Shipping(String name,String phone,String address) { }
        Shipping shipping=jdbc.queryForObject("SELECT full_name,phone,shipping_address FROM customers WHERE id=?",(r,n)->new Shipping(r.getString(1),r.getString(2),r.getString(3)),customer);
        return new CheckoutResponse(carts.view(customer),shipping.name(),shipping.phone(),shipping.address(),bank,bankAvailable());
    }
    @Transactional public OrderResponse place(long customer,String key,PlaceOrderRequest request) {
        if(key==null || !key.matches("[A-Za-z0-9_-]{8,100}")) throw new ApiException(400,"INVALID_IDEMPOTENCY_KEY","Idempotency-Key phải có 8–100 ký tự chữ, số, gạch ngang hoặc gạch dưới.");
        carts.lockCustomer(customer);
        String hash=hash(request);
        record Submission(String hash,long order) { }
        List<Submission> previous=jdbc.query("SELECT request_hash,order_id FROM order_submissions WHERE customer_id=? AND request_key=?",(r,n)->new Submission(r.getString(1),r.getLong(2)),customer,key);
        if(!previous.isEmpty()) {
            if(!previous.getFirst().hash().equals(hash)) throw ApiException.conflict("IDEMPOTENCY_CONFLICT","Khóa gửi đơn đã được dùng với nội dung khác.");
            return detail(customer,previous.getFirst().order());
        }
        if(request.paymentMethod()==PaymentMethod.BANK_TRANSFER && !bankAvailable()) throw new ApiException(503,"BANK_NOT_CONFIGURED","Thông tin tài khoản chuyển khoản chưa được cấu hình. Vui lòng chọn COD.");
        var headers=carts.lockCart(customer);
        if(headers.isEmpty()) throw ApiException.conflict("EMPTY_CART","Giỏ hàng đang trống.");
        long cart=headers.getFirst().id();
        List<Long> productIds=jdbc.query("SELECT DISTINCT v.product_id FROM cart_items ci JOIN product_variants v ON v.id=ci.variant_id WHERE ci.cart_id=? ORDER BY v.product_id",(r,n)->r.getLong(1),cart);
        for(long id:productIds) jdbc.queryForObject("SELECT id FROM products WHERE id=? FOR UPDATE",Long.class,id);
        List<Long> variants=jdbc.query("SELECT variant_id FROM cart_items WHERE cart_id=? ORDER BY variant_id",(r,n)->r.getLong(1),cart);
        for(long id:variants) jdbc.queryForObject("SELECT id FROM product_variants WHERE id=? FOR UPDATE",Long.class,id);
        var snapshot=carts.view(customer);
        if(snapshot.items().isEmpty()) throw ApiException.conflict("EMPTY_CART","Giỏ hàng đang trống.");
        if(snapshot.version()!=request.cartVersion() || new BigDecimal(snapshot.totalAmount()).compareTo(request.expectedTotal())!=0) throw ApiException.conflict("CHECKOUT_CHANGED","Giỏ hàng hoặc giá đã thay đổi. Vui lòng xem lại trước khi xác nhận.");
        for(var line:snapshot.items()) carts.check(carts.variant(line.variantId()),line.quantity());
        String number="LAI-"+UUID.randomUUID().toString().replace("-","").toUpperCase(java.util.Locale.ROOT);
        Long order=jdbc.queryForObject("INSERT INTO orders(customer_id,order_number,status,receiver_name,receiver_phone,shipping_address,total_amount) VALUES (?,?,'PENDING',?,?,?,?) RETURNING id",Long.class,customer,number,request.receiverName().strip(),request.receiverPhone(),request.shippingAddress().strip(),new BigDecimal(snapshot.totalAmount()));
        for(var line:snapshot.items()) {
            jdbc.update("INSERT INTO order_items(order_id,variant_id,product_name,sku,size,color,unit_price,quantity) VALUES (?,?,?,?,?,?,?,?)",order,line.variantId(),line.productName(),line.sku(),line.size(),line.color(),new BigDecimal(line.unitPrice()),line.quantity());
            if(jdbc.update("UPDATE product_variants SET stock_quantity=stock_quantity-? WHERE id=? AND stock_quantity>=?",line.quantity(),line.variantId(),line.quantity())!=1) throw ApiException.conflict("STOCK_UNAVAILABLE","Tồn kho đã thay đổi.");
        }
        jdbc.update("INSERT INTO payments(order_id,method,status,amount) VALUES (?,?,'PENDING',?)",order,request.paymentMethod().name(),new BigDecimal(snapshot.totalAmount()));
        jdbc.update("DELETE FROM cart_items WHERE cart_id=?",cart); carts.touch(cart);
        jdbc.update("INSERT INTO order_submissions(customer_id,request_key,request_hash,order_id) VALUES (?,?,?,?)",customer,key,hash,order);
        return detail(customer,order);
    }
    public OrderResponse detail(long customer,long id) {
        List<OrderResponse> orders=jdbc.query("SELECT o.*,p.method,p.status payment_status FROM orders o JOIN payments p ON p.order_id=o.id WHERE o.id=? AND o.customer_id=?",(r,n)->new OrderResponse(r.getLong("id"),r.getString("order_number"),r.getString("status"),r.getTimestamp("created_at").toInstant(),r.getString("receiver_name"),r.getString("receiver_phone"),r.getString("shipping_address"),r.getBigDecimal("total_amount").toPlainString(),PaymentMethod.valueOf(r.getString("method")),r.getString("payment_status"),List.of(),bank,r.getString("order_number")),id,customer);
        if(orders.isEmpty()) throw ApiException.missing();
        var o=orders.getFirst();
        List<OrderLine> lines=jdbc.query("SELECT * FROM order_items WHERE order_id=? ORDER BY id",(r,n)->new OrderLine(r.getString("product_name"),r.getString("sku"),r.getString("size"),r.getString("color"),r.getBigDecimal("unit_price").toPlainString(),r.getInt("quantity"),r.getBigDecimal("unit_price").multiply(BigDecimal.valueOf(r.getInt("quantity"))).toPlainString()),id);
        return new OrderResponse(o.id(),o.orderNumber(),o.status(),o.createdAt(),o.receiverName(),o.receiverPhone(),o.shippingAddress(),o.totalAmount(),o.paymentMethod(),o.paymentStatus(),lines,o.paymentMethod()==PaymentMethod.BANK_TRANSFER?bank:null,o.paymentMethod()==PaymentMethod.BANK_TRANSFER?o.orderNumber():null);
    }
    public OrderPage list(long customer,int page,int pageSize) {
        if(page<0 || pageSize<1 || pageSize>100) throw new ApiException(400,"INVALID_PAGE","page >= 0, pageSize từ 1 đến 100.");
        List<Long> ids=jdbc.query("SELECT id FROM orders WHERE customer_id=? ORDER BY id DESC LIMIT ? OFFSET ?",(r,n)->r.getLong(1),customer,pageSize,(long)page*pageSize);
        Long total=jdbc.queryForObject("SELECT count(*) FROM orders WHERE customer_id=?",Long.class,customer);
        return new OrderPage(ids.stream().map(id->detail(customer,id)).toList(),page,pageSize,total);
    }
    private static String hash(PlaceOrderRequest request) {
        String payload=request.receiverName()+"\u0000"+request.receiverPhone()+"\u0000"+request.shippingAddress()+"\u0000"+request.paymentMethod()+"\u0000"+request.cartVersion()+"\u0000"+request.expectedTotal().stripTrailingZeros().toPlainString();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256 is required by the Java runtime",error); }
    }
}
