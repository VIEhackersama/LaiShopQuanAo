package vn.laishop.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "search_queries")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class SearchQuery {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    public Long getId() { return id; }
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;
    public Customer getCustomer() { return customer; }
    private String searchSession;
    public String getSearchSession() { return searchSession; }
    private String queryType;
    public String getQueryType() { return queryType; }
    private String status;
    public String getStatus() { return status; }
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode searchConditions;
    public JsonNode getSearchConditions() { return searchConditions; }
    private Instant createdAt;
    public Instant getCreatedAt() { return createdAt; }
    protected SearchQuery() { }
}

