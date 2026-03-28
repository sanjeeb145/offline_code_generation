package com.aicodepilot.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Immutable response from the AI inference engine.
 *
 * <p>Carries the generated text, error state, latency metrics, and optional
 * structured suggestions for display in Eclipse UI components.
 */
public final class AIResponse {

    private final boolean success;
    private final String text;
    private final String errorMessage;
    private final long latencyMs;
    private final boolean cacheHit;
    private final Instant timestamp;
    private final List<CodeSuggestion> suggestions;

    // -----------------------------------------------------------------------
    // Factory methods
    // -----------------------------------------------------------------------

    public static AIResponse success(String text, long latencyMs) {
        return new AIResponse(true, text, null, latencyMs, false,
                Collections.emptyList());
    }

    public static AIResponse success(String text, long latencyMs,
                                     List<CodeSuggestion> suggestions) {
        return new AIResponse(true, text, null, latencyMs, false, suggestions);
    }

    public static AIResponse error(String errorMessage) {
        return new AIResponse(false, "", errorMessage, 0, false,
                Collections.emptyList());
    }

    // -----------------------------------------------------------------------
    // With-methods for immutable updates
    // -----------------------------------------------------------------------

    public AIResponse withCacheHit(boolean hit) {
        return new AIResponse(success, text, errorMessage, latencyMs, hit, suggestions);
    }

    public AIResponse withLatencyMs(long ms) {
        return new AIResponse(success, text, errorMessage, ms, cacheHit, suggestions);
    }

    // -----------------------------------------------------------------------
    // Private constructor
    // -----------------------------------------------------------------------

    private AIResponse(boolean success, String text, String errorMessage,
                       long latencyMs, boolean cacheHit, List<CodeSuggestion> suggestions) {
        this.success      = success;
        this.text         = text;
        this.errorMessage = errorMessage;
        this.latencyMs    = latencyMs;
        this.cacheHit     = cacheHit;
        this.timestamp    = Instant.now();
        this.suggestions  = Collections.unmodifiableList(suggestions);
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public boolean isSuccess()          { return success; }
    public String getText()             { return text; }
    public String getErrorMessage()     { return errorMessage; }
    public long getLatencyMs()          { return latencyMs; }
    public boolean isCacheHit()         { return cacheHit; }
    public Instant getTimestamp()       { return timestamp; }
    public List<CodeSuggestion> getSuggestions() { return suggestions; }

    @Override
    public String toString() {
        return "AIResponse{success=" + success + ", latencyMs=" + latencyMs
                + ", cacheHit=" + cacheHit + "}";
    }

    // -----------------------------------------------------------------------
    // Nested type — structured code suggestion
    // -----------------------------------------------------------------------

    /**
     * A single structured suggestion from the AI, suitable for display in
     * the suggestions panel or as a quick-fix in the Problems view.
     */
    public record CodeSuggestion(
            String category,      // e.g., "BUG", "PERFORMANCE", "REFACTOR"
            String title,         // Short one-liner
            String description,   // Detailed explanation
            String suggestedCode, // Replacement code snippet (may be empty)
            Severity severity
    ) {
        public enum Severity { ERROR, WARNING, INFO, TIP }
    }
}
