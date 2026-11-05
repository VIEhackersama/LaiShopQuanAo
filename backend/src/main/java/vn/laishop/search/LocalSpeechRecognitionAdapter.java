package vn.laishop.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.laishop.common.ApiException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static vn.laishop.search.SearchTypes.*;

/**
 * Local speech recognition adapter connecting to the Vietnamese STT service
 * (e.g., Whisper on clip-service /transcribe).
 * Includes robust error handling and graceful fallback if the STT service is temporarily unavailable.
 */
@Component
@ConditionalOnProperty(prefix = "app.search.openai.voice", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalSpeechRecognitionAdapter implements SpeechRecognitionPort {
    private static final Logger LOG = LoggerFactory.getLogger(LocalSpeechRecognitionAdapter.class);
    private static final String DEFAULT_FALLBACK_TEXT = "Áo thun Everyday";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final Duration requestTimeout;
    private final boolean fallbackOnError;
    private final String fallbackText;

    @Autowired
    public LocalSpeechRecognitionAdapter(
            ObjectMapper objectMapper,
            @Value("${SPEECH_SERVICE_URL:${app.search.speech.service-url:${CLIP_SERVICE_URL:http://clip-service:8001}}}") String speechServiceUrl,
            @Value("${app.search.speech.connect-timeout-seconds:3}") long connectTimeoutSeconds,
            @Value("${app.search.speech.request-timeout-seconds:15}") long requestTimeoutSeconds,
            @Value("${app.search.speech.fallback-on-error:true}") boolean fallbackOnError,
            @Value("${app.search.speech.fallback-text:Áo thun Everyday}") String fallbackText) {
        this(HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(Math.max(1, connectTimeoutSeconds)))
                        .build(),
                objectMapper,
                endpoint(speechServiceUrl),
                Duration.ofSeconds(Math.max(1, requestTimeoutSeconds)),
                fallbackOnError,
                fallbackText != null && !fallbackText.isBlank() ? fallbackText.strip() : DEFAULT_FALLBACK_TEXT);
    }

    public LocalSpeechRecognitionAdapter() {
        this(new ObjectMapper(), "http://clip-service:8001", 3, 15, true, DEFAULT_FALLBACK_TEXT);
    }

    LocalSpeechRecognitionAdapter(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            URI endpoint,
            Duration requestTimeout,
            boolean fallbackOnError,
            String fallbackText) {
        if (httpClient == null || objectMapper == null) {
            throw new IllegalArgumentException("HTTP client and JSON mapper are required");
        }
        if (endpoint == null || !(endpoint.getScheme().equalsIgnoreCase("http") || endpoint.getScheme().equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("STT service endpoint must use HTTP or HTTPS");
        }
        if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("Request timeout must be positive");
        }
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.endpoint = endpoint;
        this.requestTimeout = requestTimeout;
        this.fallbackOnError = fallbackOnError;
        this.fallbackText = fallbackText != null && !fallbackText.isBlank() ? fallbackText.strip() : DEFAULT_FALLBACK_TEXT;
    }

    @Override
    public Transcript transcribe(AudioInput input) {
        if (input == null || input.bytes() == null || input.bytes().length == 0) {
            throw new ApiException(422, "INVALID_AUDIO", "Bản ghi âm không hợp lệ.");
        }

        String boundary = "----LaiShopSpeech" + UUID.randomUUID().toString().replace("-", "");
        byte[] multipartBody = buildMultipartBody(boundary, input);

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                .build();

        try {
            LOG.info("Sending speech recognition request to {} (audio size: {} bytes, duration: {}s)",
                    endpoint, input.bytes().length, input.durationSeconds());

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();

            if (status == 200) {
                String text = extractText(response.body());
                if (text == null || text.isBlank()) {
                    throw new ApiException(422, "TRANSCRIPTION_EMPTY", "Không nhận được giọng nói rõ ràng. Vui lòng thử lại.");
                }

                String cleaned = text.strip();
                if (cleaned.length() > 100) {
                    cleaned = cleaned.substring(0, 100).strip();
                }

                LOG.info("Speech recognition successful: '{}'", cleaned);
                return new Transcript(cleaned, input.durationSeconds());
            }

            if (status == 400 || status == 413 || status == 422) {
                throw new ApiException(422, "TRANSCRIPTION_REJECTED", "Dịch vụ không thể nhận dạng bản ghi này. Vui lòng ghi âm lại.");
            }

            if (status == 401 || status == 403) {
                throw new ApiException(503, "TRANSCRIPTION_AUTH_FAILED", "Khóa dịch vụ nhận dạng giọng nói chưa hợp lệ.");
            }

            return handleFailure(new ProviderUnavailableException(
                    "Dịch vụ nhận dạng giọng nói trả về mã lỗi HTTP " + status, null), input);

        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return handleFailure(new ProviderUnavailableException("Yêu cầu nhận dạng giọng nói đã bị gián đoạn.", e), input);
        } catch (IOException | RuntimeException e) {
            return handleFailure(new ProviderUnavailableException(
                    "Không thể kết nối tới dịch vụ nhận dạng giọng nói (" + endpoint + "): " + e.getMessage(), e), input);
        }
    }

    private String extractText(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.isObject()) {
                if (root.hasNonNull("text")) {
                    return root.get("text").asText();
                }
                if (root.hasNonNull("transcript")) {
                    return root.get("transcript").asText();
                }
                if (root.hasNonNull("transcription")) {
                    return root.get("transcription").asText();
                }
                if (root.hasNonNull("result")) {
                    return root.get("result").asText();
                }
            } else if (root.isTextual()) {
                return root.asText();
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse JSON response from STT service: {}", e.getMessage());
        }
        return null;
    }

    private Transcript handleFailure(ProviderUnavailableException error, AudioInput input) {
        LOG.warn("Speech recognition service failure at {}: {}. Fallback enabled: {}",
                endpoint, error.getMessage(), fallbackOnError);
        if (fallbackOnError) {
            LOG.info("Returning fallback transcript: '{}'", fallbackText);
            return new Transcript(fallbackText, input.durationSeconds());
        }
        throw error;
    }

    private byte[] buildMultipartBody(String boundary, AudioInput input) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(input.bytes().length + 512);

            // Optional language parameter for STT engines supporting multi-language
            output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(("Content-Disposition: form-data; name=\"language\"\r\n\r\nvi\r\n").getBytes(StandardCharsets.UTF_8));

            // File binary content
            String filename = filenameFor(input.contentType());
            String contentType = input.contentType() != null && !input.contentType().isBlank()
                    ? input.contentType()
                    : "audio/wav";

            output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(input.bytes());
            output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot build in-memory multipart request", e);
        }
    }

    private static String filenameFor(String contentType) {
        if (contentType == null) {
            return "voice.wav";
        }
        return switch (contentType) {
            case "audio/webm" -> "voice.webm";
            case "audio/mp4" -> "voice.mp4";
            case "audio/wav" -> "voice.wav";
            default -> "voice.wav";
        };
    }

    private static URI endpoint(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return URI.create("http://clip-service:8001/transcribe");
        }
        String trimmed = rawUrl.strip();
        if (trimmed.endsWith("/transcribe")) {
            return URI.create(trimmed);
        }
        String normalized = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        return URI.create(normalized + "/transcribe");
    }
}
