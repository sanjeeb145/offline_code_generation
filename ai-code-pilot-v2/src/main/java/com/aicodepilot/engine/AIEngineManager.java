package com.aicodepilot.engine;

import com.aicodepilot.engine.llm.InferenceEngine;
import com.aicodepilot.engine.llm.LlamaInferenceEngine;
import com.aicodepilot.engine.llm.RuleBasedEngine;
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
 * <p>Engine selection priority:
 * <ol>
 *   <li>llama.cpp + GGUF model — fastest CPU performance</li>
 *   <li>Rule-based — always available, no model required</li>
 * </ol>
 *
 * <p>Includes an LRU response cache (50 entries) to avoid redundant
 * inference for repeated identical prompts.
 *
 * <p><b>Thread safety:</b> all public methods are safe for concurrent calls.
 */
public class AIEngineManager {

    // ── Constants ────────────────────────────────────────────────────────────
    private static final int  DEFAULT_MAX_TOKENS       = 512;
    private static final long INFERENCE_TIMEOUT_SECONDS = 60L;
    private static final int  CACHE_MAX_SIZE            = 50;

    // ── State ─────────────────────────────────────────────────────────────────
    private final Path modelDirectory;
    private InferenceEngine activeEngine;
    private String engineType = "Not initialized";
    private final AtomicBoolean ready = new AtomicBoolean(false);

    /** LRU cache — access-ordered LinkedHashMap */
    @SuppressWarnings("serial")
    private final Map<String, AIResponse> responseCache =
        new LinkedHashMap<>(CACHE_MAX_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, AIResponse> e) {
                return size() > CACHE_MAX_SIZE;
            }
        };

    // ── Construction ──────────────────────────────────────────────────────────

    public AIEngineManager(Path modelDirectory) {
        this.modelDirectory = modelDirectory;
    }

    // ── Initialization ────────────────────────────────────────────────────────

    /**
     * Selects and loads the best available inference engine.
     * Called once from a background Job during plugin startup.
     */
    public void initialize(IProgressMonitor monitor) {
        monitor.subTask("Scanning for AI model...");

        // 1. Try llama.cpp with GGUF model
        Path ggufModel = findFile(modelDirectory, ".gguf");
        Path llamaBinary = LlamaInferenceEngine.findBinary();

        if (ggufModel != null && llamaBinary != null) {
            monitor.subTask("Loading llama.cpp engine: " + ggufModel.getFileName());
            try {
                LlamaInferenceEngine engine =
                        new LlamaInferenceEngine(ggufModel, llamaBinary);
                engine.initialize();
                activeEngine = engine;
                engineType   = engine.getEngineName();
                PluginLogger.info("Engine loaded: " + engineType);
                ready.set(true);
                return;
            } catch (Exception e) {
                PluginLogger.warn("llama.cpp engine failed: " + e.getMessage()
                        + " — falling back to rule-based engine.");
            }
        } else {
            if (ggufModel == null) {
                PluginLogger.info("No .gguf model found in: " + modelDirectory);
            }
            if (llamaBinary == null) {
                PluginLogger.info("llama-cli binary not found. "
                        + "Place it in ~/.aicodepilot/ or set path in preferences.");
            }
        }

        // 2. Fall back to rule-based engine (always works)
        monitor.subTask("Loading rule-based engine (fallback)...");
        RuleBasedEngine fallback = new RuleBasedEngine();
        fallback.initialize();
        activeEngine = fallback;
        engineType   = fallback.getEngineName();
        ready.set(true);
        PluginLogger.info("Rule-based engine active. "
                + "Install a GGUF model for AI-powered analysis.");
    }

    // ── Inference API ─────────────────────────────────────────────────────────

    /**
     * Synchronous inference — blocks calling thread.
     * Always call from a background thread, never from the UI thread.
     */
    public AIResponse infer(AIRequest request) {
        ensureReady();

        // Check cache
        String key = cacheKey(request);
        synchronized (responseCache) {
            AIResponse cached = responseCache.get(key);
            if (cached != null) {
                PluginLogger.debug("Cache hit for prompt hash: " + key);
                return cached.withCacheHit(true);
            }
        }

        long start = System.currentTimeMillis();
        AIResponse response;
        try {
            int maxTokens = request.getMaxNewTokens() > 0
                    ? request.getMaxNewTokens() : DEFAULT_MAX_TOKENS;
            response = activeEngine.generate(
                    request.getPrompt(),
                    request.getSystemPrompt(),
                    maxTokens,
                    request.getTemperature());
            response = response.withLatencyMs(System.currentTimeMillis() - start);
        } catch (Exception e) {
            PluginLogger.error("Inference failed", e);
            response = AIResponse.error("Inference failed: " + e.getMessage());
        }

        if (response.isSuccess()) {
            synchronized (responseCache) {
                responseCache.put(key, response);
            }
        }
        return response;
    }

    /**
     * Asynchronous inference — returns immediately with a CompletableFuture.
     * Preferred for UI handlers to keep the UI thread non-blocking.
     */
    public CompletableFuture<AIResponse> inferAsync(AIRequest request) {
        return CompletableFuture.supplyAsync(() -> infer(request))
                .orTimeout(INFERENCE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally(ex ->
                        AIResponse.error("Inference timed out: " + ex.getMessage()));
    }

    // ── Resource management ───────────────────────────────────────────────────

    public void shutdown() {
        ready.set(false);
        if (activeEngine != null) {
            try { activeEngine.close(); } catch (Exception e) {
                PluginLogger.error("Engine shutdown error", e);
            }
        }
        synchronized (responseCache) { responseCache.clear(); }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public boolean isReady()         { return ready.get(); }
    public String  getEngineType()   { return engineType; }
    public InferenceEngine getActiveEngine() { return activeEngine; }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void ensureReady() {
        if (!ready.get()) throw new IllegalStateException(
                "AI engine not initialized. Please wait for startup to complete.");
    }

    private String cacheKey(AIRequest req) {
        return req.getPrompt().hashCode() + "_" + req.getMaxNewTokens();
    }

    private Path findFile(Path dir, String extension) {
        if (dir == null || !Files.isDirectory(dir)) return null;
        try {
            return Files.walk(dir, 2)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(extension))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            PluginLogger.warn("Model scan error: " + e.getMessage());
            return null;
        }
    }
}
