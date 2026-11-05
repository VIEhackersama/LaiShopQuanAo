package vn.laishop.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "product_images")
public class ProductImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    public Long getId() { return id; }
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
    public Product getProduct() { return product; }
    private String imageUrl;
    public String getImageUrl() { return imageUrl; }
    private String altText;
    public String getAltText() { return altText; }
    private int displayOrder;
    public int getDisplayOrder() { return displayOrder; }
    protected ProductImage() { }
}

