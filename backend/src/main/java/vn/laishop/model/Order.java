package vn.laishop.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "orders")
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    public Long getId() { return id; }
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;
    public Customer getCustomer() { return customer; }
    private String orderNumber;
    public String getOrderNumber() { return orderNumber; }
    private String status;
    public String getStatus() { return status; }
    private String receiverName;
    public String getReceiverName() { return receiverName; }
    private String receiverPhone;
    public String getReceiverPhone() { return receiverPhone; }
    private String shippingAddress;
    public String getShippingAddress() { return shippingAddress; }
    private BigDecimal totalAmount;
    public BigDecimal getTotalAmount() { return totalAmount; }
    private Instant createdAt;
    public Instant getCreatedAt() { return createdAt; }
    private Instant updatedAt;
    public Instant getUpdatedAt() { return updatedAt; }
    protected Order() { }
}

