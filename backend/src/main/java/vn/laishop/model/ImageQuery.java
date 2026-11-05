package vn.laishop.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "image_queries")
public class ImageQuery extends SearchQuery {
    private String imageUrl;
    public String getImageUrl() { return imageUrl; }
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode selectedRegion;
    public JsonNode getSelectedRegion() { return selectedRegion; }
    protected ImageQuery() { }
}

