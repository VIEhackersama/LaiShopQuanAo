package vn.laishop.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import static vn.laishop.search.SearchTypes.*;

/**
 * Demo fallback adapter for local testing and academic evaluation without requiring OpenAI API keys.
 * Automatically active when real OpenAI voice integration is disabled (OPENAI_VOICE_ENABLED=false).
 */
@Component
@ConditionalOnProperty(prefix = "app.search.openai.voice", name = "enabled", havingValue = "false", matchIfMissing = true)
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
