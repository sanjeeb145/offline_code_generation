package com.aicodepilot.engine.llm;

import com.aicodepilot.model.AIResponse;

/**
 * Contract for all local AI inference backends.
 *
 * <p>Priority order (auto-detected at startup):
 * <ol>
 *   <li>{@link LlamaInferenceEngine}  – llama.cpp subprocess (GGUF models, fastest CPU)</li>
 *   <li>{@link RuleBasedEngine}       – Pattern-based fallback (no model needed)</li>
 * </ol>
 */
public interface InferenceEngine extends AutoCloseable {

    /**
     * Load model weights into memory. Called once after construction.
     * May take several seconds for large models.
     */
    void initialize() throws Exception;

    /**
     * Generate text from the given prompt (blocking call).
     *
     * @param prompt       the user / task prompt
     * @param systemPrompt optional system instruction (may be null or blank)
     * @param maxNewTokens maximum number of tokens to generate
     * @param temperature  sampling temperature — 0.0 = deterministic, 1.0 = creative
     * @return {@link AIResponse} with generated text and metadata
     */
    AIResponse generate(String prompt, String systemPrompt,
                        int maxNewTokens, float temperature);

    /** Human-readable engine name shown in status bar. */
    String getEngineName();

    /** Release all resources (file handles, native memory, subprocess). */
    @Override
    void close() throws Exception;
}
