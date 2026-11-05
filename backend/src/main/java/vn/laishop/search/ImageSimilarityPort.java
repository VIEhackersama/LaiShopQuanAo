package vn.laishop.search;

import static vn.laishop.search.SearchTypes.*;

/** Adapter decodes JPEG/PNG/WebP, applies normalized crop, and returns unique product IDs ordered by similarity above its calibrated threshold. Scores are not probabilities. */
public interface ImageSimilarityPort {
    ImageMatches search(ImageInput input);
}
