package vn.laishop.search;

import static vn.laishop.search.SearchTypes.*;

/** Adapter must decode actual audio, measure duration, reject malformed/silent audio and return Vietnamese transcription. It must not persist shop data. */
public interface SpeechRecognitionPort {
    Transcript transcribe(AudioInput input);
}
