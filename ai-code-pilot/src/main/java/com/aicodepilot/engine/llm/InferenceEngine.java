package com.aicodepilot.engine.llm;

import com.aicodepilot.model.AIResponse;

/**
 * Contract for all local AI inference backends.
 *
 * <p>Implementations wrap different inference runtimes (llama.cpp, ONNX,
 * DJL) behind a uniform interface so the rest of the plugin is agnostic
 * to which engine is active at runtime.
 */
public interface InferenceEngine extends AutoCloseable {

    /**
     * Initializes the engine and loads model weights into memory.
     * Called once after construction. May take several seconds.
     *
     * @throws Exception if the model cannot be loaded
     */
    void initialize() throws Exception;

    /**
     * Generates text from the given prompt.
     *
     * @param prompt       the user / task prompt
     * @param systemPrompt optional system instruction (may be null)
     * @param maxNewTokens maximum number of tokens to generate
     * @param temperature  sampling temperature (0.0 = greedy, 1.0 = creative)
     * @return {@link AIResponse} containing the generated text and metadata
     */
    AIResponse generate(String prompt, String systemPrompt,
                        int maxNewTokens, float temperature);

    /**
     * Returns a human-readable name for this engine (used in status displays).
     */
    String getEngineName();

    /**
     * Releases all resources (model handles, native memory, file handles).
     */
    @Override
    void close() throws Exception;
}
