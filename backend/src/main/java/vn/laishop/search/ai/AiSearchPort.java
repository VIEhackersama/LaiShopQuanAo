package vn.laishop.search.ai;

import static vn.laishop.search.ai.AiTypes.*;

/** Read-only search planning. The provider may request a catalog search or ask for clarification, never execute SQL or commerce actions. */
public interface AiSearchPort {
    Decision decide(TurnContext context);
    String model();
    String provider();
}
