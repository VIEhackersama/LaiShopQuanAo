package vn.laishop.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "carts")
public class Cart {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    public Long getId() { return id; }
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;
    public Customer getCustomer() { return customer; }
    private long version;
    public long getVersion() { return version; }
    private Instant createdAt;
    public Instant getCreatedAt() { return createdAt; }
    private Instant updatedAt;
    public Instant getUpdatedAt() { return updatedAt; }
    protected Cart() { }
}

