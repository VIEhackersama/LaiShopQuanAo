package vn.laishop.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static vn.laishop.search.SearchTypes.*;

/**
 * Demo fallback adapter for local testing and academic evaluation without requiring external services.
 * Kept for reference and test stubs; superseded by LocalSpeechRecognitionAdapter as active component.
 */
public final class DemoSpeechRecognitionAdapter implements SpeechRecognitionPort {
    private static final Logger LOG = LoggerFactory.getLogger(DemoSpeechRecognitionAdapter.class);

    @Override
    public Transcript transcribe(AudioInput input) {
        LOG.info("DemoSpeechRecognitionAdapter: transcribed recorded audio ({} bytes, {} seconds)",
                input.bytes() != null ? input.bytes().length : 0, input.durationSeconds());

        // Returns sample Vietnamese fashion search query so user can review/edit and execute the search
        return new Transcript("Áo thun Everyday", input.durationSeconds());
    }
}
