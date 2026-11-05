package vn.laishop.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    public Long getId() { return id; }
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;
    public Category getCategory() { return category; }
    private String name;
    public String getName() { return name; }
    private String description;
    public String getDescription() { return description; }
    private boolean active;
    public boolean getActive() { return active; }
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode embedding;
    public JsonNode getEmbedding() { return embedding; }
    private String searchText;
    public String getSearchText() { return searchText; }
    private Instant createdAt;
    public Instant getCreatedAt() { return createdAt; }
    private Instant updatedAt;
    public Instant getUpdatedAt() { return updatedAt; }
    protected Product() { }
}

