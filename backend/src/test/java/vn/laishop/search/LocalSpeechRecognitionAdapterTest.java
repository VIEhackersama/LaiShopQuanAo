package vn.laishop.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import vn.laishop.common.ApiException;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static vn.laishop.search.SearchTypes.*;

class LocalSpeechRecognitionAdapterTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void transcribesSuccessfullyWithJsonText() throws Exception {
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(200, "{\"text\":\"áo sơ mi nam trắng\"}", contentType, requestBody);

        LocalSpeechRecognitionAdapter adapter = createAdapter(true, "Áo thun Everyday");
        byte[] audio = "test-audio-bytes".getBytes(StandardCharsets.UTF_8);
        Transcript transcript = adapter.transcribe(new AudioInput(audio, "audio/webm", 4.5));

        assertNotNull(transcript);
        assertEquals("áo sơ mi nam trắng", transcript.text());
        assertEquals(4.5, transcript.durationSeconds());
        assertTrue(contentType.get().contains("multipart/form-data"));
        assertTrue(requestBody.get().contains("name=\"language\"\r\n\r\nvi"));
        assertTrue(requestBody.get().contains("filename=\"voice.webm\""));
        assertTrue(requestBody.get().contains("test-audio-bytes"));
    }

    @Test
    void transcribesSuccessfullyWithTranscriptField() throws Exception {
        startServer(200, "{\"transcript\":\"quần jean ống rộng\"}", new AtomicReference<>(), new AtomicReference<>());

        LocalSpeechRecognitionAdapter adapter = createAdapter(true, "Áo thun Everyday");
        byte[] audio = "test-audio-bytes".getBytes(StandardCharsets.UTF_8);
        Transcript transcript = adapter.transcribe(new AudioInput(audio, "audio/wav", 3.0));

        assertNotNull(transcript);
        assertEquals("quần jean ống rộng", transcript.text());
        assertEquals(3.0, transcript.durationSeconds());
    }

    @Test
    void rejectsEmptyAudioInput() {
        LocalSpeechRecognitionAdapter adapter = new LocalSpeechRecognitionAdapter();

        ApiException error1 = assertThrows(ApiException.class, () -> adapter.transcribe(null));
        assertEquals(422, error1.status());
        assertEquals("INVALID_AUDIO", error1.code());

        ApiException error2 = assertThrows(ApiException.class,
                () -> adapter.transcribe(new AudioInput(new byte[0], "audio/wav", 3.0)));
        assertEquals(422, error2.status());
        assertEquals("INVALID_AUDIO", error2.code());
    }

    @Test
    void rejectsEmptyTranscriptionFromService() throws Exception {
        startServer(200, "{\"text\":\"   \"}", new AtomicReference<>(), new AtomicReference<>());

        LocalSpeechRecognitionAdapter adapter = createAdapter(true, "Áo thun Everyday");
        byte[] audio = "test-audio-bytes".getBytes(StandardCharsets.UTF_8);

        ApiException error = assertThrows(ApiException.class,
                () -> adapter.transcribe(new AudioInput(audio, "audio/webm", 2.0)));
        assertEquals(422, error.status());
        assertEquals("TRANSCRIPTION_EMPTY", error.code());
    }

    @Test
    void handlesRejectedAudioFromService() throws Exception {
        startServer(422, "{\"detail\":\"Cannot decode audio\"}", new AtomicReference<>(), new AtomicReference<>());

        LocalSpeechRecognitionAdapter adapter = createAdapter(true, "Áo thun Everyday");
        byte[] audio = "corrupted-audio".getBytes(StandardCharsets.UTF_8);

        ApiException error = assertThrows(ApiException.class,
                () -> adapter.transcribe(new AudioInput(audio, "audio/mp4", 2.5)));
        assertEquals(422, error.status());
        assertEquals("TRANSCRIPTION_REJECTED", error.code());
    }

    @Test
    void fallbackWhenServiceReturns500AndFallbackEnabled() throws Exception {
        startServer(500, "{\"error\":\"internal error\"}", new AtomicReference<>(), new AtomicReference<>());

        LocalSpeechRecognitionAdapter adapter = createAdapter(true, "Áo thun Everyday");
        byte[] audio = "audio-bytes".getBytes(StandardCharsets.UTF_8);
        Transcript transcript = adapter.transcribe(new AudioInput(audio, "audio/wav", 2.5));

        assertNotNull(transcript);
        assertEquals("Áo thun Everyday", transcript.text());
        assertEquals(2.5, transcript.durationSeconds());
    }

    @Test
    void fallbackWhenServiceUnreachableAndFallbackEnabled() {
        // Points to an unopened port
        URI unreachableUri = URI.create("http://127.0.0.1:54321/transcribe");
        LocalSpeechRecognitionAdapter adapter = new LocalSpeechRecognitionAdapter(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                unreachableUri,
                Duration.ofSeconds(1),
                true,
                "Áo thun Everyday"
        );

        byte[] audio = "audio-bytes".getBytes(StandardCharsets.UTF_8);
        Transcript transcript = adapter.transcribe(new AudioInput(audio, "audio/wav", 2.5));

        assertNotNull(transcript);
        assertEquals("Áo thun Everyday", transcript.text());
        assertEquals(2.5, transcript.durationSeconds());
    }

    @Test
    void throwsProviderUnavailableWhenServiceDownAndFallbackDisabled() {
        URI unreachableUri = URI.create("http://127.0.0.1:54321/transcribe");
        LocalSpeechRecognitionAdapter adapter = new LocalSpeechRecognitionAdapter(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                unreachableUri,
                Duration.ofSeconds(1),
                false,
                "Áo thun Everyday"
        );

        byte[] audio = "audio-bytes".getBytes(StandardCharsets.UTF_8);
        assertThrows(ProviderUnavailableException.class,
                () -> adapter.transcribe(new AudioInput(audio, "audio/wav", 2.5)));
    }

    private void startServer(int status, String response, AtomicReference<String> contentType, AtomicReference<String> body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/transcribe", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    private LocalSpeechRecognitionAdapter createAdapter(boolean fallbackOnError, String fallbackText) {
        int port = server != null ? server.getAddress().getPort() : 8001;
        URI endpoint = URI.create("http://127.0.0.1:" + port + "/transcribe");
        return new LocalSpeechRecognitionAdapter(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                endpoint,
                Duration.ofSeconds(5),
                fallbackOnError,
                fallbackText
        );
    }
}
