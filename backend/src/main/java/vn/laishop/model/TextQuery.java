package vn.laishop.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "text_queries")
public class TextQuery extends SearchQuery {
    private String text;
    public String getText() { return text; }
    protected TextQuery() { }
}

