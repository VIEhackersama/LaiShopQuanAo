package vn.laishop.search;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageReader;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.laishop.common.ApiException;
import static vn.laishop.search.SearchTypes.*;

/**
 * Image matcher supporting both multimodal CLIP embeddings with pgvector
 * and fallback local RGB matching.
 */
@Component
@ConditionalOnExpression("${LOCAL_IMAGE_SEARCH_ENABLED:false} || ${CLIP_SEARCH_ENABLED:true}")
public class LocalImageSimilarityAdapter implements ImageSimilarityPort {
    private static final Logger LOG = LoggerFactory.getLogger(LocalImageSimilarityAdapter.class);
    static final long DEFAULT_MAX_PIXELS = 16_000_000;
    static final int MAX_DIMENSION = 8_192;
    private final JdbcTemplate jdbc;
    private final double minimumScore;
    private final long maximumPixels;
    private final boolean clipEnabled;
    private final String clipServiceUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LocalImageSimilarityAdapter(
            JdbcTemplate jdbc,
            @Value("${LOCAL_IMAGE_SEARCH_MIN_SCORE:0.35}") double minimumScore,
            @Value("${LOCAL_IMAGE_SEARCH_MAX_PIXELS:16000000}") long maximumPixels,
            @Value("${CLIP_SEARCH_ENABLED:true}") boolean clipEnabled,
            @Value("${CLIP_SERVICE_URL:http://clip-service:8001}") String clipServiceUrl) {
        if (!Double.isFinite(minimumScore) || minimumScore < 0 || minimumScore > 1) {
            throw new IllegalArgumentException("LOCAL_IMAGE_SEARCH_MIN_SCORE must be between 0 and 1");
        }
        if (maximumPixels < 1 || maximumPixels > DEFAULT_MAX_PIXELS) {
            throw new IllegalArgumentException("LOCAL_IMAGE_SEARCH_MAX_PIXELS must be between 1 and 16000000");
        }
        this.jdbc = jdbc;
        this.minimumScore = minimumScore;
        this.maximumPixels = maximumPixels;
        this.clipEnabled = clipEnabled;
        this.clipServiceUrl = clipServiceUrl;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ImageMatches search(ImageInput input) {
        if (clipEnabled) {
            try {
                ImageMatches clipMatches = searchWithClip(input);
                if (clipMatches != null && !clipMatches.items().isEmpty()) {
                    return clipMatches;
                }
            } catch (Exception ex) {
                LOG.warn("CLIP search failed or service unavailable, falling back to RGB matching: {}", ex.getMessage(), ex);
            }
        }
        return searchWithRgb(input);
    }

    private ImageMatches searchWithClip(ImageInput input) throws IOException, InterruptedException {
        String boundary = "Boundary" + java.util.UUID.randomUUID().toString().replace("-", "");
        byte[] body = buildMultipartBody(boundary, input);

        HttpRequest request = HttpRequest.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .uri(URI.create(clipServiceUrl + "/embed"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("CLIP service HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode embedNode = root.get("embedding");
        if (embedNode == null || !embedNode.isArray() || embedNode.isEmpty()) {
            throw new RuntimeException("Invalid embedding in CLIP response: " + response.body());
        }

        StringBuilder vectorSb = new StringBuilder("[");
        for (int i = 0; i < embedNode.size(); i++) {
            if (i > 0) vectorSb.append(",");
            vectorSb.append(embedNode.get(i).asDouble());
        }
        vectorSb.append("]");
        String vectorStr = vectorSb.toString();

        List<ImageMatch> matches = jdbc.query(
                "SELECT id, (1 - (clip_embedding <=> ?::vector)) AS score "
                        + "FROM products "
                        + "WHERE active = true AND clip_embedding IS NOT NULL "
                        + "ORDER BY clip_embedding <=> ?::vector "
                        + "LIMIT 24",
                (rs, rowNum) -> new ImageMatch(
                        rs.getLong("id"),
                        BigDecimal.valueOf(Math.max(0.0, Math.min(1.0, rs.getDouble("score"))))
                                .setScale(6, RoundingMode.HALF_UP)
                ),
                vectorStr, vectorStr
        );
        return new ImageMatches(matches);
    }

    private byte[] buildMultipartBody(String boundary, ImageInput input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"query.jpg\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + (input.contentType() != null ? input.contentType() : "image/jpeg") + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(input.bytes());
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));

        if (input.region() != null) {
            addFormField(out, boundary, "x", String.valueOf(input.region().x()));
            addFormField(out, boundary, "y", String.valueOf(input.region().y()));
            addFormField(out, boundary, "width", String.valueOf(input.region().width()));
            addFormField(out, boundary, "height", String.valueOf(input.region().height()));
        }

        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private void addFormField(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write((value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private ImageMatches searchWithRgb(ImageInput input) {
        double[] query = describe(input.bytes(), input.region(), maximumPixels);
        List<Candidate> candidates = jdbc.query(
                "SELECT id,embedding::text FROM products WHERE active=true AND embedding IS NOT NULL "
                        + "AND CASE WHEN jsonb_typeof(embedding)='array' "
                        + "THEN jsonb_array_length(embedding)=3 ELSE false END",
                result -> {
                    List<Candidate> valid = new ArrayList<>();
                    while (result.next()) {
                        try {
                            valid.add(new Candidate(result.getLong(1), parseEmbedding(result.getString(2))));
                        } catch (IllegalStateException malformed) {
                            LOG.atWarn().addKeyValue("productId", result.getLong(1))
                                    .setCause(malformed).log("local_image_embedding_skipped");
                        }
                    }
                    return valid;
                });
        List<ImageMatch> matches = new ArrayList<>();
        for (Candidate candidate : candidates) {
            double score = similarity(query, candidate.embedding());
            if (score >= minimumScore) {
                matches.add(new ImageMatch(candidate.productId(),
                        BigDecimal.valueOf(score).setScale(6, RoundingMode.HALF_UP)));
            }
        }
        matches.sort(Comparator.comparing(ImageMatch::score).reversed()
                .thenComparingLong(ImageMatch::productId));
        return new ImageMatches(matches);
    }

    static double[] describe(byte[] bytes, Region region) {
        return describe(bytes, region, DEFAULT_MAX_PIXELS);
    }

    static double[] describe(byte[] bytes, Region region, long maximumPixels) {
        BufferedImage decoded = decodeSafely(bytes, maximumPixels);
        BufferedImage image = compositeOnWhite(decoded);
        int x = 0, y = 0, width = image.getWidth(), height = image.getHeight();
        if (region != null) {
            x = Math.min(width - 1, Math.max(0, (int) Math.floor(region.x() * width)));
            y = Math.min(height - 1, Math.max(0, (int) Math.floor(region.y() * height)));
            int right = Math.min(width, Math.max(x + 1, (int) Math.ceil((region.x() + region.width()) * width)));
            int bottom = Math.min(height, Math.max(y + 1, (int) Math.ceil((region.y() + region.height()) * height)));
            width = right - x;
            height = bottom - y;
        }
        long red = 0, green = 0, blue = 0, count = 0;
        int step = Math.max(1, Math.max(width, height) / 160);
        for (int py = y; py < y + height; py += step) {
            for (int px = x; px < x + width; px += step) {
                Color colour = new Color(image.getRGB(px, py));
                red += colour.getRed(); green += colour.getGreen(); blue += colour.getBlue(); count++;
            }
        }
        return new double[]{red / (double) count, green / (double) count, blue / (double) count};
    }

    private static BufferedImage decodeSafely(byte[] bytes, long maximumPixels) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (stream == null) throw invalidImage(null);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw invalidImage(null);
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("jpeg") && !format.equalsIgnoreCase("png")) throw invalidImage(null);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                long pixels = (long) width * height;
                if (width < 2 || height < 2 || width > MAX_DIMENSION || height > MAX_DIMENSION || pixels > maximumPixels) {
                    throw new ApiException(422, "IMAGE_DIMENSIONS_EXCEEDED",
                            "Ảnh phải ít nhất 2x2, không quá 8192px mỗi chiều và tối đa " + maximumPixels + " pixel.");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) throw invalidImage(null);
                return decoded;
            } finally {
                reader.dispose();
            }
        } catch (ApiException error) {
            throw error;
        } catch (IOException | RuntimeException error) {
            throw invalidImage(error);
        }
    }

    static double similarity(double[] left, double[] right) {
        if (left.length != 3 || right.length != 3) throw new IllegalArgumentException("RGB embedding must have 3 values");
        double squared = 0;
        for (int n = 0; n < 3; n++) {
            double difference = left[n] - right[n];
            squared += difference * difference;
        }
        return Math.max(0, 1 - Math.sqrt(squared) / (Math.sqrt(3) * 255));
    }

    static double[] parseEmbedding(String json) {
        if (json == null || !json.matches("\\s*\\[\\s*-?\\d+(?:\\.\\d+)?\\s*,\\s*-?\\d+(?:\\.\\d+)?\\s*,\\s*-?\\d+(?:\\.\\d+)?\\s*]\\s*")) {
            throw new IllegalStateException("Local image embedding must be a JSON array with three numbers");
        }
        String[] values = json.replaceAll("[\\[\\]\\s]", "").split(",");
        double[] parsed = new double[3];
        for (int n = 0; n < parsed.length; n++) {
            parsed[n] = Double.parseDouble(values[n]);
            if (!Double.isFinite(parsed[n]) || parsed[n] < 0 || parsed[n] > 255) {
                throw new IllegalStateException("Local image embedding values must be between 0 and 255");
            }
        }
        return parsed;
    }

    private static BufferedImage compositeOnWhite(BufferedImage source) {
        BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, output.getWidth(), output.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }

    private static ApiException invalidImage(Exception cause) {
        ApiException error = new ApiException(422, "UNDECODABLE_IMAGE",
                "Ảnh không thể giải mã cục bộ. Demo local hỗ trợ JPEG và PNG mà Java ImageIO đọc được.");
        if (cause != null) error.initCause(cause);
        return error;
    }

    private record Candidate(long productId, double[] embedding) { }
}
