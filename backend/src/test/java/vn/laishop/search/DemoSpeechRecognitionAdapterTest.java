package vn.laishop.search;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DemoSpeechRecognitionAdapterTest {
    @Test
    void returnsSampleTranscriptionForTesting() {
        DemoSpeechRecognitionAdapter adapter = new DemoSpeechRecognitionAdapter();
        SearchTypes.AudioInput input = new SearchTypes.AudioInput(new byte[]{1, 2, 3}, "audio/webm", 5.0);
        SearchTypes.Transcript transcript = adapter.transcribe(input);

        assertNotNull(transcript);
        assertNotNull(transcript.text());
        assertFalse(transcript.text().isBlank());
        assertEquals(5.0, transcript.durationSeconds());
    }
}
