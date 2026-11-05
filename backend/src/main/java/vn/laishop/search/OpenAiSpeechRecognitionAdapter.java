package vn.laishop.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.laishop.common.ApiException;
import static vn.laishop.search.SearchTypes.*;

/** Real server-side speech recognition using OpenAI's audio transcription API. */
@Component
@ConditionalOnProperty(prefix="app.search.openai.voice",name="enabled",havingValue="true")
public final class OpenAiSpeechRecognitionAdapter implements SpeechRecognitionPort {
    private final HttpClient client;
    private final ObjectMapper json;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final String language;
    private final Duration requestTimeout;

    @Autowired
    public OpenAiSpeechRecognitionAdapter(
            ObjectMapper json,
            @Value("${app.search.openai.voice.api-key:}") String apiKey,
            @Value("${app.search.openai.voice.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.search.openai.voice.model:gpt-transcribe}") String model,
            @Value("${app.search.openai.voice.language:vi}") String language,
            @Value("${app.search.openai.voice.connect-timeout-seconds:5}") long connectTimeoutSeconds,
            @Value("${app.search.openai.voice.request-timeout-seconds:30}") long requestTimeoutSeconds) {
        this(HttpClient.newBuilder().connectTimeout(duration(connectTimeoutSeconds,"connect timeout")).build(),
                json,endpoint(baseUrl),apiKey,model,language,duration(requestTimeoutSeconds,"request timeout"));
    }

    OpenAiSpeechRecognitionAdapter(HttpClient client,ObjectMapper json,URI endpoint,String apiKey,
                                   String model,String language,Duration requestTimeout) {
        if(client==null || json==null) throw new IllegalArgumentException("HTTP client and JSON mapper are required");
        if(endpoint==null || !(endpoint.getScheme().equals("https") || endpoint.getScheme().equals("http")))
            throw new IllegalArgumentException("OpenAI transcription endpoint must use HTTP or HTTPS");
        if(apiKey==null || apiKey.isBlank()) throw new IllegalArgumentException("OPENAI_API_KEY is required when OpenAI voice search is enabled");
        if(model==null || !model.matches("[A-Za-z0-9._-]{1,100}")) throw new IllegalArgumentException("Invalid transcription model");
        if(language==null || !language.matches("[A-Za-z-]{2,20}")) throw new IllegalArgumentException("Invalid transcription language");
        if(requestTimeout==null || requestTimeout.isNegative() || requestTimeout.isZero()) throw new IllegalArgumentException("Request timeout must be positive");
        this.client=client; this.json=json; this.endpoint=endpoint; this.apiKey=apiKey.strip();
        this.model=model; this.language=language; this.requestTimeout=requestTimeout;
    }

    @Override public Transcript transcribe(AudioInput input) {
        if(input==null) throw new ApiException(422,"INVALID_AUDIO","Bản ghi âm không hợp lệ.");
        String boundary="----LaiShopVoice"+UUID.randomUUID().toString().replace("-","");
        HttpRequest request=HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Authorization","Bearer "+apiKey)
                .header("Content-Type","multipart/form-data; boundary="+boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(input,boundary)))
                .build();
        try {
            HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status=response.statusCode();
            if(status==401 || status==403)
                throw new ApiException(503,"TRANSCRIPTION_AUTH_FAILED","Khóa dịch vụ nhận dạng giọng nói chưa hợp lệ.");
            if(status==400 || status==413 || status==422)
                throw new ApiException(422,"TRANSCRIPTION_REJECTED","Dịch vụ không thể nhận dạng bản ghi này. Vui lòng ghi âm lại.");
            if(status==429 || status>=500)
                throw new ProviderUnavailableException("Dịch vụ nhận dạng giọng nói đang bận hoặc tạm thời không khả dụng.",null);
            if(status<200 || status>=300)
                throw new ProviderUnavailableException("Dịch vụ nhận dạng giọng nói trả về trạng thái không mong đợi.",null);
            Response body=json.readValue(response.body(),Response.class);
            if(body.text()==null || body.text().isBlank())
                throw new ApiException(422,"TRANSCRIPTION_EMPTY","Không nhận được giọng nói rõ ràng. Vui lòng thử lại.");
            return new Transcript(body.text().strip(),input.durationSeconds());
        } catch(ApiException | ProviderUnavailableException error) {
            throw error;
        } catch(InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException("Yêu cầu nhận dạng giọng nói đã bị gián đoạn.",error);
        } catch(IOException error) {
            throw new ProviderUnavailableException("Không thể kết nối dịch vụ nhận dạng giọng nói.",error);
        }
    }

    private byte[] multipart(AudioInput input,String boundary) {
        try {
            ByteArrayOutputStream output=new ByteArrayOutputStream(input.bytes().length+1024);
            field(output,boundary,"model",model);
            field(output,boundary,"language",language);
            field(output,boundary,"response_format","json");
            field(output,boundary,"prompt","Truy vấn tìm kiếm sản phẩm thời trang bằng tiếng Việt.");
            output.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+filename(input.contentType())+"\"\r\n"
                    +"Content-Type: "+input.contentType()+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(input.bytes());
            output.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
            return output.toByteArray();
        } catch(IOException impossible) {
            throw new IllegalStateException("Cannot build in-memory multipart request",impossible);
        }
    }

    private static void field(ByteArrayOutputStream output,String boundary,String name,String value) throws IOException {
        output.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+name+"\"\r\n\r\n"+value+"\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String filename(String contentType) {
        return switch(contentType) { case "audio/webm" -> "voice.webm"; case "audio/mp4" -> "voice.mp4"; case "audio/wav" -> "voice.wav"; default -> "voice.bin"; };
    }

    private static URI endpoint(String baseUrl) {
        if(baseUrl==null || baseUrl.isBlank()) throw new IllegalArgumentException("OpenAI base URL is required");
        String normalized=baseUrl.endsWith("/")?baseUrl:baseUrl+"/";
        return URI.create(normalized).resolve("audio/transcriptions");
    }

    private static Duration duration(long seconds,String name) {
        if(seconds<1 || seconds>120) throw new IllegalArgumentException(name+" must be between 1 and 120 seconds");
        return Duration.ofSeconds(seconds);
    }

    private record Response(String text) { }
}
