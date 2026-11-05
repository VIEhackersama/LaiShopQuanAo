package vn.laishop.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "search_results")
public class SearchResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    public Long getId() { return id; }
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "query_id")
    private SearchQuery query;
    public SearchQuery getQuery() { return query; }
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
    public Product getProduct() { return product; }
    private int rank;
    public int getRank() { return rank; }
    private BigDecimal score;
    public BigDecimal getScore() { return score; }
    protected SearchResult() { }
}

