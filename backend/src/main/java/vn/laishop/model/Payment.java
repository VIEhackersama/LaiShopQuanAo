package vn.laishop.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "payments")
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    public Long getId() { return id; }
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;
    public Order getOrder() { return order; }
    private String method;
    public String getMethod() { return method; }
    private String status;
    public String getStatus() { return status; }
    private BigDecimal amount;
    public BigDecimal getAmount() { return amount; }
    private String transactionReference;
    public String getTransactionReference() { return transactionReference; }
    private Instant paidAt;
    public Instant getPaidAt() { return paidAt; }
    private Instant createdAt;
    public Instant getCreatedAt() { return createdAt; }
    protected Payment() { }
}

