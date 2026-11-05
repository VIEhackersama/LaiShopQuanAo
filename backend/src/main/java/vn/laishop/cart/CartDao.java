package vn.laishop.cart;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.laishop.common.ApiException;
import static vn.laishop.cart.CartTypes.*;

@Repository
public class CartDao {
    private final JdbcTemplate jdbc;
    public CartDao(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void lockCustomer(long customer) { jdbc.queryForObject("SELECT id FROM customers WHERE id=? FOR UPDATE",Long.class,customer); }
    public record CartHeader(long id,long version) { }
    public List<CartHeader> lockCart(long customer) { return jdbc.query("SELECT id,version FROM carts WHERE customer_id=? FOR UPDATE",(r,n)->new CartHeader(r.getLong(1),r.getLong(2)),customer); }
    public record Variant(long id,long productId,String name,String sku,String size,String color,BigDecimal price,int stock,boolean active) { }
    public Variant variant(long id) {
        List<Variant> variants=jdbc.query("SELECT v.*,p.name,p.active AND v.active AND c.active available FROM product_variants v JOIN products p ON p.id=v.product_id JOIN categories c ON c.id=p.category_id WHERE v.id=?",(r,n)->new Variant(r.getLong("id"),r.getLong("product_id"),r.getString("name"),r.getString("sku"),r.getString("size"),r.getString("color"),r.getBigDecimal("price"),r.getInt("stock_quantity"),r.getBoolean("available")),id);
        if(variants.isEmpty()) throw ApiException.missing(); return variants.getFirst();
    }
    public CartResponse view(long customer) {
        List<CartHeader> headers=jdbc.query("SELECT id,version FROM carts WHERE customer_id=?",(r,n)->new CartHeader(r.getLong(1),r.getLong(2)),customer);
        if(headers.isEmpty()) return new CartResponse(null,0,List.of(),0,"0.00");
        CartHeader header=headers.getFirst();
        List<CartLine> lines=jdbc.query("SELECT ci.id,ci.variant_id,ci.quantity,p.name,v.sku,v.size,v.color,v.price,v.stock_quantity,p.active AND v.active AND c.active available,(SELECT image_url FROM product_images WHERE product_id=p.id ORDER BY display_order LIMIT 1) image_url FROM cart_items ci JOIN product_variants v ON v.id=ci.variant_id JOIN products p ON p.id=v.product_id JOIN categories c ON c.id=p.category_id WHERE ci.cart_id=? ORDER BY ci.id",(r,n)->new CartLine(r.getLong("id"),r.getLong("variant_id"),r.getString("name"),r.getString("image_url"),r.getString("sku"),r.getString("size"),r.getString("color"),r.getInt("quantity"),r.getBigDecimal("price").toPlainString(),r.getBigDecimal("price").multiply(BigDecimal.valueOf(r.getInt("quantity"))).toPlainString(),r.getInt("stock_quantity"),!r.getBoolean("available")?List.of("Sản phẩm đã ngừng bán"):r.getInt("stock_quantity")<r.getInt("quantity")?List.of("Không đủ tồn kho"):List.of()),header.id());
        BigDecimal total=lines.stream().map(l->new BigDecimal(l.lineTotal())).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new CartResponse(header.id(),header.version(),lines,lines.stream().mapToInt(CartLine::quantity).sum(),total.toPlainString());
    }
    public void touch(long cart) { jdbc.update("UPDATE carts SET version=version+1,updated_at=now() WHERE id=?",cart); }
    public void check(Variant variant,long quantity) { if(!variant.active() || quantity>variant.stock()) throw ApiException.conflict("STOCK_UNAVAILABLE",variant.name()+" — "+variant.size()+" / "+variant.color()+": không đủ tồn kho hoặc đã ngừng bán."); }
}
