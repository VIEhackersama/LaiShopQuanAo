package vn.laishop.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "voice_queries")
public class VoiceQuery extends SearchQuery {
    private String audioUrl;
    public String getAudioUrl() { return audioUrl; }
    private String transcribedText;
    public String getTranscribedText() { return transcribedText; }
    protected VoiceQuery() { }
}

