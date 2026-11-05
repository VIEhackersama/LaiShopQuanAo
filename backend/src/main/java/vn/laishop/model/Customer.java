package vn.laishop.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "customers")
public class Customer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    public Long getId() { return id; }
    private String fullName;
    public String getFullName() { return fullName; }
    private String email;
    public String getEmail() { return email; }
    private String passwordHash;
    public String getPasswordHash() { return passwordHash; }
    private String phone;
    public String getPhone() { return phone; }
    private String shippingAddress;
    public String getShippingAddress() { return shippingAddress; }
    private Instant createdAt;
    public Instant getCreatedAt() { return createdAt; }
    protected Customer() { }
}

