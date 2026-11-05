package vn.laishop.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "product_variants")
public class ProductVariant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    public Long getId() { return id; }
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
    public Product getProduct() { return product; }
    private String sku;
    public String getSku() { return sku; }
    private String size;
    public String getSize() { return size; }
    private String color;
    public String getColor() { return color; }
    private BigDecimal price;
    public BigDecimal getPrice() { return price; }
    private int stockQuantity;
    public int getStockQuantity() { return stockQuantity; }
    private boolean active;
    public boolean getActive() { return active; }
    protected ProductVariant() { }
}

