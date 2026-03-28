package com.aicodepilot.engine;

import com.aicodepilot.engine.llm.InferenceEngine;

import com.aicodepilot.engine.llm.LlamaInferenceEngine;
import com.aicodepilot.engine.llm.OnnxInferenceEngine;
import com.aicodepilot.engine.llm.RuleBasedEngine;
import com.aicodepilot.engine.llm.DJLInferenceEngine;
import com.aicodepilot.model.AIRequest;
import com.aicodepilot.model.AIResponse;
import com.aicodepilot.util.PluginLogger;
import org.eclipse.core.runtime.IProgressMonitor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central orchestrator for all AI inference operations.
 *
 * <p>Implements an engine selection strategy: detects available models and
 * picks the best inference backend in priority order:
 * <ol>
 *   <li>llama.cpp (native, fastest CPU performance)</li>
 *   <li>ONNX Runtime (cross-platform, good CPU throughput)</li>
 *   <li>DJL PyTorch (pure Java, widest model support)</li>
 * </ol>
 *
 * <p>All inference is performed offline. No network calls are made.
 *
 * <p>Thread-safety: all public methods are safe for concurrent calls.
 * Inference is serialized internally by the selected engine to respect
 * RAM constraints on 8–16 GB machines.
 */
public class AIEngineManager {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    /** Max tokens to generate per response (tune per model). */
    private static final int DEFAULT_MAX_NEW_TOKENS = 512;

    /** Inference timeout — prevents runaway generation on slow CPUs. */
    private static final long INFERENCE_TIMEOUT_SECONDS = 30;

    /** Simple in-memory LRU cache for repeated identical prompts. */
    private static final int CACHE_MAX_SIZE = 50;

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    private final Path modelDirectory;
    private InferenceEngine activeEngine;
    private String engineType;
    private final AtomicBoolean ready = new AtomicBoolean(false);

    /** LRU response cache keyed by prompt hash. */
    @SuppressWarnings("serial")
    private final Map<String, AIResponse> responseCache =
            new LinkedHashMap<>(CACHE_MAX_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, AIResponse> eldest) {
                    return size() > CACHE_MAX_SIZE;
                }
            };

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    public AIEngineManager(Path modelDirectory) {
        this.modelDirectory = modelDirectory;
    }

    // -----------------------------------------------------------------------
    // Initialization
    // -----------------------------------------------------------------------

    /**
     * Detects available models and initializes the best available engine.
     * Called once during plugin startup from a background Job.
     *
     * @param monitor Eclipse progress monitor (for UI feedback)
     */
    public void initialize(IProgressMonitor monitor) throws Exception {
        monitor.subTask("Scanning model directory: " + modelDirectory);

        InferenceEngine engine = selectAndLoadEngine(monitor);
        if (engine == null) {
            PluginLogger.warn("No AI model found in " + modelDirectory
                    + ". Plugin will operate in rule-based mode.");
            // Fall back to rule-based engine — still useful for pattern detection
            activeEngine = new RuleBasedEngine();
            engineType = "RuleBased (no model found)";
        } else {
            activeEngine = engine;
        }

        ready.set(true);
        PluginLogger.info("AI Engine ready. Type: " + engineType);
    }

    /**
     * Tries each engine in priority order, returning the first that succeeds.
     */
    private InferenceEngine selectAndLoadEngine(IProgressMonitor monitor) {
        // 1. Try llama.cpp (GGUF models — fastest on CPU)
        Path ggufModel = findModel(modelDirectory, ".gguf");
        if (ggufModel != null) {
            monitor.subTask("Loading llama.cpp engine...");
            try {
                LlamaInferenceEngine engine = new LlamaInferenceEngine(ggufModel);
                engine.initialize();
                engineType = "llama.cpp (GGUF)";
                PluginLogger.info("Loaded GGUF model: " + ggufModel.getFileName());
                return engine;
            } catch (Exception e) {
                PluginLogger.warn("llama.cpp engine failed, trying ONNX: " + e.getMessage());
            }
        }

        // 2. Try ONNX Runtime
        Path onnxModel = findModel(modelDirectory, ".onnx");
        if (onnxModel != null) {
            monitor.subTask("Loading ONNX Runtime engine...");
            try {
                OnnxInferenceEngine engine = new OnnxInferenceEngine(onnxModel);
                engine.initialize();
                engineType = "ONNX Runtime";
                PluginLogger.info("Loaded ONNX model: " + onnxModel.getFileName());
                return engine;
            } catch (Exception e) {
                PluginLogger.warn("ONNX engine failed, trying DJL: " + e.getMessage());
            }
        }

        // 3. Try DJL (PyTorch / TorchScript models)
        Path ptModel = findModel(modelDirectory, ".pt");
        if (ptModel != null) {
            monitor.subTask("Loading DJL PyTorch engine...");
            try {
                DJLInferenceEngine engine = new DJLInferenceEngine(ptModel);
                engine.initialize();
                engineType = "DJL/PyTorch";
                PluginLogger.info("Loaded PyTorch model: " + ptModel.getFileName());
                return engine;
            } catch (Exception e) {
                PluginLogger.warn("DJL engine failed: " + e.getMessage());
            }
        }

        return null; // No engine available
    }

    // -----------------------------------------------------------------------
    // Inference API
    // -----------------------------------------------------------------------

    /**
     * Performs synchronous AI inference. Blocks the calling thread.
     * Should be called from a background thread only.
     *
     * @param request the inference request with prompt and parameters
     * @return AI response with generated text and metadata
     */
    public AIResponse infer(AIRequest request) {
        ensureReady();

        // Check cache first
        String cacheKey = computeCacheKey(request);
        synchronized (responseCache) {
            AIResponse cached = responseCache.get(cacheKey);
            if (cached != null) {
                PluginLogger.debug("Cache hit for prompt hash: " + cacheKey);
                return cached.withCacheHit(true);
            }
        }

        long startMs = System.currentTimeMillis();
        AIResponse response;

        try {
            // Apply token limit from request or default
            int maxTokens = request.getMaxNewTokens() > 0
                    ? request.getMaxNewTokens()
                    : DEFAULT_MAX_NEW_TOKENS;

            response = activeEngine.generate(
                    request.getPrompt(),
                    request.getSystemPrompt(),
                    maxTokens,
                    request.getTemperature()
            );

            long elapsedMs = System.currentTimeMillis() - startMs;
            response = response.withLatencyMs(elapsedMs);
            PluginLogger.debug("Inference completed in " + elapsedMs + "ms");

        } catch (Exception e) {
            PluginLogger.error("Inference failed", e);
            response = AIResponse.error("Inference failed: " + e.getMessage());
        }

        // Cache successful responses
        if (response.isSuccess()) {
            synchronized (responseCache) {
                responseCache.put(cacheKey, response);
            }
        }

        return response;
    }

    /**
     * Performs asynchronous AI inference. Returns immediately with a future.
     * Preferred API for UI handlers — keeps the UI thread non-blocking.
     *
     * @param request the inference request
     * @return CompletableFuture that completes with the AI response
     */
    public CompletableFuture<AIResponse> inferAsync(AIRequest request) {
        return CompletableFuture.supplyAsync(() -> infer(request))
                .orTimeout(INFERENCE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally(ex -> AIResponse.error("Inference timed out: " + ex.getMessage()));
    }

    // -----------------------------------------------------------------------
    // Resource Management
    // -----------------------------------------------------------------------

    /**
     * Shuts down the active inference engine and releases native resources.
     */
    public void shutdown() {
        ready.set(false);
        if (activeEngine != null) {
            try {
                activeEngine.close();
            } catch (Exception e) {
                PluginLogger.error("Error during engine shutdown", e);
            }
        }
        synchronized (responseCache) {
            responseCache.clear();
        }
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public boolean isReady() {
        return ready.get();
    }

    public String getEngineType() {
        return engineType;
    }

    public InferenceEngine getActiveEngine() {
        return activeEngine;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void ensureReady() {
        if (!ready.get()) {
            throw new IllegalStateException(
                    "AI engine is not yet initialized. Please wait for engine warm-up.");
        }
    }

    /**
     * Scans the model directory for a file matching the given extension.
     * Returns the first match, or null if none found.
     */
    private Path findModel(Path directory, String extension) {
        try {
            return Files.walk(directory, 2)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(extension))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            PluginLogger.warn("Model scan failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Computes a cache key from the prompt and key parameters.
     * Simple but effective — avoids hashing large strings.
     */
    private String computeCacheKey(AIRequest request) {
        return String.valueOf(
                request.getPrompt().hashCode() * 31
                        + request.getMaxNewTokens()
        );
    }
}
