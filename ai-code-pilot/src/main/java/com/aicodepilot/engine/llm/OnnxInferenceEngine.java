package com.aicodepilot.engine.llm;

import ai.onnxruntime.*;
import com.aicodepilot.model.AIResponse;
import com.aicodepilot.util.PluginLogger;

import java.nio.LongBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * InferenceEngine backed by Microsoft ONNX Runtime.
 *
 * <p>Suitable for models exported to ONNX format (e.g., CodeBERT, smaller
 * encoder-decoder models). ONNX Runtime provides excellent cross-platform
 * CPU performance with optional acceleration (OpenVINO, DirectML, etc.).
 *
 * <p><b>Recommended model:</b> phi-2 ONNX (2.7B params, ~2 GB RAM in INT4).
 *
 * <p><b>Token encoding:</b> A simple whitespace tokenizer is used here for
 * portability. For production, replace with a proper HuggingFace tokenizer
 * (via the DJL tokenizers artifact or a native binding).
 */
public class OnnxInferenceEngine implements InferenceEngine {

    private final Path modelPath;
    private OrtEnvironment ortEnv;
    private OrtSession ortSession;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean initialized = false;

    // Vocabulary (simplified — replace with real vocab file in production)
    private static final int VOCAB_SIZE = 50257;  // GPT-2 vocab size
    private static final int PAD_TOKEN  = 0;
    private static final int EOS_TOKEN  = 50256;

    public OnnxInferenceEngine(Path modelPath) {
        this.modelPath = modelPath;
    }

    @Override
    public void initialize() throws Exception {
        PluginLogger.info("Initializing ONNX Runtime engine...");
        ortEnv = OrtEnvironment.getEnvironment();

        // Configure session for CPU-optimized inference
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        opts.setInterOpNumThreads(Math.max(1,
                Runtime.getRuntime().availableProcessors() / 2));
        opts.setIntraOpNumThreads(Math.max(1,
                Runtime.getRuntime().availableProcessors() / 2));
        // Enable memory pattern optimization for repeated sequence lengths
        opts.setMemoryPatternOptimization(true);

        ortSession = ortEnv.createSession(modelPath.toString(), opts);
        initialized = true;

        PluginLogger.info("ONNX session created. Input nodes: "
                + ortSession.getInputNames());
    }

    @Override
    public AIResponse generate(String prompt, String systemPrompt,
                               int maxNewTokens, float temperature) {
        ensureInitialized();
        lock.lock();
        try {
            long startMs = System.currentTimeMillis();

            // Tokenize input (simplified — use proper tokenizer in production)
            long[] inputIds = tokenize(buildFullPrompt(prompt, systemPrompt));

            // Greedy decode loop (generates one token at a time)
            List<Long> generatedIds = new ArrayList<>();
            long[] currentIds = inputIds;

            for (int step = 0; step < maxNewTokens; step++) {
                // Prepare input tensors
                Map<String, OnnxTensor> inputs = new HashMap<>();
                long[][] ids2d = new long[][]{currentIds};
                long[][] attMask = new long[][]{ones(currentIds.length)};

                inputs.put("input_ids",
                        OnnxTensor.createTensor(ortEnv, ids2d));
                inputs.put("attention_mask",
                        OnnxTensor.createTensor(ortEnv, attMask));

                // Run inference
                try (OrtSession.Result result = ortSession.run(inputs)) {
                    float[][][] logits = (float[][][]) result.get(0).getValue();

                    // Get logits for last token position
                    float[] lastLogits = logits[0][currentIds.length - 1];

                    // Apply temperature and sample
                    int nextToken = temperature < 0.01f
                            ? argmax(lastLogits)
                            : sampleWithTemperature(lastLogits, temperature);

                    if (nextToken == EOS_TOKEN) break;

                    generatedIds.add((long) nextToken);

                    // Append new token for next iteration
                    long[] newIds = Arrays.copyOf(currentIds, currentIds.length + 1);
                    newIds[currentIds.length] = nextToken;
                    currentIds = newIds;
                }

                // Release input tensors
                for (OnnxTensor t : inputs.values()) t.close();
            }

            String text = detokenize(generatedIds);
            long latencyMs = System.currentTimeMillis() - startMs;
            return AIResponse.success(text, latencyMs);

        } catch (OrtException e) {
            PluginLogger.error("ONNX inference error", e);
            return AIResponse.error("ONNX inference failed: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String getEngineName() { return "ONNX Runtime"; }

    @Override
    public void close() throws Exception {
        if (ortSession != null) ortSession.close();
        if (ortEnv != null) ortEnv.close();
        initialized = false;
    }

    // -----------------------------------------------------------------------
    // Tokenization helpers (stub — replace with real tokenizer)
    // -----------------------------------------------------------------------

    private long[] tokenize(String text) {
        // STUB: Replace with HuggingFace tokenizer or BPE implementation
        // This simplified version splits on spaces and maps to char codes
        String[] words = text.split("\\s+");
        long[] ids = new long[Math.min(words.length, 512)];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = (words[i].hashCode() & 0x7FFFFFFF) % VOCAB_SIZE;
        }
        return ids;
    }

    private String detokenize(List<Long> ids) {
        // STUB: Replace with real vocabulary lookup
        return "[Generated text — integrate real tokenizer for production]";
    }

    private String buildFullPrompt(String prompt, String system) {
        if (system == null || system.isBlank()) return prompt;
        return system + "\n\n" + prompt;
    }

    private long[] ones(int length) {
        long[] mask = new long[length];
        Arrays.fill(mask, 1L);
        return mask;
    }

    private int argmax(float[] logits) {
        int best = 0;
        for (int i = 1; i < logits.length; i++) {
            if (logits[i] > logits[best]) best = i;
        }
        return best;
    }

    private int sampleWithTemperature(float[] logits, float temperature) {
        // Apply temperature
        double[] probs = new double[logits.length];
        double sum = 0;
        for (int i = 0; i < logits.length; i++) {
            probs[i] = Math.exp(logits[i] / temperature);
            sum += probs[i];
        }
        // Normalize
        double r = Math.random() * sum;
        double cumSum = 0;
        for (int i = 0; i < probs.length; i++) {
            cumSum += probs[i];
            if (cumSum >= r) return i;
        }
        return probs.length - 1;
    }

    private void ensureInitialized() {
        if (!initialized) throw new IllegalStateException("ONNX engine not initialized");
    }
}
