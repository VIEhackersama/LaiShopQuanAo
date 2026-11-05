package vn.laishop.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    public Long getId() { return id; }
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;
    public Order getOrder() { return order; }
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;
    public ProductVariant getVariant() { return variant; }
    private String productName;
    public String getProductName() { return productName; }
    private String sku;
    public String getSku() { return sku; }
    private String size;
    public String getSize() { return size; }
    private String color;
    public String getColor() { return color; }
    private BigDecimal unitPrice;
    public BigDecimal getUnitPrice() { return unitPrice; }
    private int quantity;
    public int getQuantity() { return quantity; }
    protected OrderItem() { }
}

