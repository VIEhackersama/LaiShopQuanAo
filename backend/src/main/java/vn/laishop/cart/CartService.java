package vn.laishop.cart;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.laishop.common.ApiException;
import static vn.laishop.cart.CartTypes.*;

@Service
public class CartService {
    private final CartDao carts;
    private final JdbcTemplate jdbc;
    public CartService(CartDao carts,JdbcTemplate jdbc) { this.carts=carts; this.jdbc=jdbc; }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public CartResponse view(long customer) { return carts.view(customer); }
    @Transactional public CartResponse add(long customer,AddItem request) {
        carts.lockCustomer(customer); var headers=carts.lockCart(customer); var variant=carts.variant(request.variantId());
        long cart;
        if(headers.isEmpty()) { carts.check(variant,request.quantity()); cart=jdbc.queryForObject("INSERT INTO carts(customer_id) VALUES (?) RETURNING id",Long.class,customer); }
        else cart=headers.getFirst().id();
        List<Integer> quantities=jdbc.query("SELECT quantity FROM cart_items WHERE cart_id=? AND variant_id=?",(r,n)->r.getInt(1),cart,request.variantId());
        long quantity=(quantities.isEmpty()?0:quantities.getFirst())+(long)request.quantity(); carts.check(variant,quantity);
        jdbc.update("INSERT INTO cart_items(cart_id,variant_id,quantity) VALUES (?,?,?) ON CONFLICT(cart_id,variant_id) DO UPDATE SET quantity=EXCLUDED.quantity",cart,request.variantId(),Math.toIntExact(quantity));
        carts.touch(cart); return carts.view(customer);
    }
    @Transactional public CartResponse update(long customer,long item,UpdateItem request) {
        carts.lockCustomer(customer); var headers=carts.lockCart(customer); if(headers.isEmpty()) throw ApiException.missing(); long cart=headers.getFirst().id();
        List<Long> variants=jdbc.query("SELECT variant_id FROM cart_items WHERE id=? AND cart_id=?",(r,n)->r.getLong(1),item,cart);
        if(variants.isEmpty()) throw ApiException.missing(); carts.check(carts.variant(variants.getFirst()),request.quantity());
        jdbc.update("UPDATE cart_items SET quantity=? WHERE id=? AND cart_id=?",request.quantity(),item,cart); carts.touch(cart); return carts.view(customer);
    }
    @Transactional public CartResponse remove(long customer,long item) {
        carts.lockCustomer(customer); var headers=carts.lockCart(customer); if(headers.isEmpty()) throw ApiException.missing(); long cart=headers.getFirst().id();
        if(jdbc.update("DELETE FROM cart_items WHERE id=? AND cart_id=?",item,cart)!=1) throw ApiException.missing(); carts.touch(cart); return carts.view(customer);
    }
    @Transactional public CartResponse clear(long customer) {
        carts.lockCustomer(customer); var headers=carts.lockCart(customer);
        if(!headers.isEmpty()) { long cart=headers.getFirst().id(); jdbc.update("DELETE FROM cart_items WHERE cart_id=?",cart); carts.touch(cart); } return carts.view(customer);
    }
}
