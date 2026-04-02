package com.aicodepilot.model;

/**
 * Immutable request object for AI inference operations.
 *
 * <p>Uses the Builder pattern for readable construction and to ensure
 * required fields are always set. All inference calls go through this type
 * so parameters are consistent and easy to log/audit.
 */
public final class AIRequest {

    // -----------------------------------------------------------------------
    // Core fields
    // -----------------------------------------------------------------------

    private final String prompt;
    private final String systemPrompt;
    private final int maxNewTokens;
    private final float temperature;
    private final RequestType requestType;

    // -----------------------------------------------------------------------
    // Request types — drive prompt template selection
    // -----------------------------------------------------------------------

    public enum RequestType {
        ANALYZE_CODE,
        GENERATE_CODE,
        DETECT_BUGS,
        REFACTOR,
        EXPLAIN_CODE,
        GENERATE_DEVOPS,
        GENERATE_TESTS,
        SQL_OPTIMIZE
    }

    // -----------------------------------------------------------------------
    // Construction via Builder
    // -----------------------------------------------------------------------

    private AIRequest(Builder builder) {
        this.prompt       = builder.prompt;
        this.systemPrompt = builder.systemPrompt;
        this.maxNewTokens = builder.maxNewTokens;
        this.temperature  = builder.temperature;
        this.requestType  = builder.requestType;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String prompt;
        private String systemPrompt = "";
        private int maxNewTokens    = 512;
        private float temperature   = 0.2f; // Low temperature for deterministic code generation
        private RequestType requestType = RequestType.ANALYZE_CODE;

        public Builder prompt(String prompt) {
            if (prompt == null || prompt.isBlank()) {
                throw new IllegalArgumentException("Prompt must not be blank");
            }
            this.prompt = prompt;
            return this;
        }
        public Builder systemPrompt(String sp) { this.systemPrompt = sp; return this; }
        public Builder maxNewTokens(int n)     { this.maxNewTokens = n; return this; }
        public Builder temperature(float t)    { this.temperature = t; return this; }
        public Builder requestType(RequestType t) { this.requestType = t; return this; }

        public AIRequest build() {
            if (prompt == null) throw new IllegalStateException("prompt is required");
            return new AIRequest(this);
        }
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public String getPrompt()        { return prompt; }
    public String getSystemPrompt()  { return systemPrompt; }
    public int getMaxNewTokens()     { return maxNewTokens; }
    public float getTemperature()    { return temperature; }
    public RequestType getRequestType() { return requestType; }
}
