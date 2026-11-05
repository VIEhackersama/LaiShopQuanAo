package vn.laishop.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import vn.laishop.common.ApiException;
import static org.junit.jupiter.api.Assertions.*;
import static vn.laishop.search.SearchTypes.*;

class OpenAiSpeechRecognitionAdapterTest {
    private HttpServer server;

    @AfterEach void stopServer() { if(server!=null) server.stop(0); }

    @Test void sendsRealAudioMultipartAndReturnsProviderTranscript() throws Exception {
        AtomicReference<String> authorization=new AtomicReference<>();
        AtomicReference<String> requestBody=new AtomicReference<>();
        start(200,"{\"text\":\"áo thun trắng size M\"}",authorization,requestBody);

        byte[] audio="webm-audio-content".getBytes(StandardCharsets.UTF_8);
        Transcript transcript=adapter().transcribe(new AudioInput(audio,"audio/webm",2.75));

        assertEquals("áo thun trắng size M",transcript.text());
        assertEquals(2.75,transcript.durationSeconds());
        assertEquals("Bearer test-key",authorization.get());
        assertTrue(requestBody.get().contains("name=\"model\"\r\n\r\ngpt-transcribe"));
        assertTrue(requestBody.get().contains("filename=\"voice.webm\""));
        assertTrue(requestBody.get().contains("webm-audio-content"));
    }

    @Test void rejectsInvalidApiKeyWithoutRetryingAsTransient() throws Exception {
        start(401,"{\"error\":{\"message\":\"bad key\"}}",new AtomicReference<>(),new AtomicReference<>());
        ApiException error=assertThrows(ApiException.class,()->adapter().transcribe(input()));
        assertEquals(503,error.status());
        assertEquals("TRANSCRIPTION_AUTH_FAILED",error.code());
    }

    @Test void mapsRateLimitAndServerErrorsToRetryableProviderFailure() throws Exception {
        start(429,"{\"error\":{\"message\":\"busy\"}}",new AtomicReference<>(),new AtomicReference<>());
        assertThrows(ProviderUnavailableException.class,()->adapter().transcribe(input()));
    }

    @Test void rejectsEmptyProviderTranscriptAsUnclearSpeech() throws Exception {
        start(200,"{\"text\":\"   \"}",new AtomicReference<>(),new AtomicReference<>());
        ApiException error=assertThrows(ApiException.class,()->adapter().transcribe(input()));
        assertEquals(422,error.status());
        assertEquals("TRANSCRIPTION_EMPTY",error.code());
    }

    private void start(int status,String response,AtomicReference<String> authorization,AtomicReference<String> body) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/audio/transcriptions",exchange->{
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.ISO_8859_1));
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(status,bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    private OpenAiSpeechRecognitionAdapter adapter() {
        URI endpoint=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v1/audio/transcriptions");
        return new OpenAiSpeechRecognitionAdapter(HttpClient.newHttpClient(),new ObjectMapper(),endpoint,
                "test-key","gpt-transcribe","vi",Duration.ofSeconds(5));
    }

    private static AudioInput input() {
        return new AudioInput("webm-audio-content".getBytes(StandardCharsets.UTF_8),"audio/webm",2.5);
    }
}
