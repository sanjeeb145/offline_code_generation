package com.aicodepilot.engine.llm;

import com.aicodepilot.model.AIResponse;
import com.aicodepilot.util.PluginLogger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * InferenceEngine backed by llama.cpp via a managed subprocess.
 *
 * <p><b>Architecture:</b> The plugin ships a pre-compiled {@code llama-cli}
 * binary inside the plugin jar. On first use it is extracted to a temp
 * directory and invoked as a subprocess for each inference request. This
 * avoids JNI complexity and keeps native code isolated.
 *
 * <p><b>Supported models:</b> Any GGUF format model compatible with llama.cpp
 * (e.g., DeepSeek-Coder-1.3B-Q4_K_M.gguf, CodeLlama-7B-Q4_K_M.gguf).
 *
 * <p><b>Performance:</b> On 8 GB RAM with a 4-bit quantized 3B model,
 * expect ~20–40 tokens/second on modern CPUs. Context length is capped
 * at 2048 tokens to bound RAM usage.
 *
 * <p><b>Thread safety:</b> Uses a reentrant lock to serialize calls.
 * llama.cpp subprocess is single-threaded per process; parallelism is
 * handled at the {@link com.aicodepilot.engine.AIEngineManager} level.
 */
public class LlamaInferenceEngine implements InferenceEngine {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    private static final String LLAMA_BINARY_LINUX   = "llama-cli-linux-x64";
    private static final String LLAMA_BINARY_WINDOWS = "llama-cli-windows-x64.exe";
    private static final String LLAMA_BINARY_MACOS   = "llama-cli-macos-x64";

    /** Max context window tokens — keeps RAM usage bounded. */
    private static final int CONTEXT_SIZE = 2048;

    /** Number of CPU threads for llama.cpp. Auto-set to half of available cores. */
    private static final int CPU_THREADS = Math.max(2,
            Runtime.getRuntime().availableProcessors() / 2);

    /** Subprocess execution timeout in seconds. */
    private static final long SUBPROCESS_TIMEOUT_SEC = 60;

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    private final Path modelPath;
    private Path binaryPath;
    private final ReentrantLock inferenceLock = new ReentrantLock();
    private volatile boolean initialized = false;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    public LlamaInferenceEngine(Path modelPath) {
        this.modelPath = modelPath;
    }

    // -----------------------------------------------------------------------
    // InferenceEngine implementation
    // -----------------------------------------------------------------------

    @Override
    public void initialize() throws Exception {
        PluginLogger.info("Initializing llama.cpp engine with model: " + modelPath.getFileName());

        // Extract native binary from plugin resources
        binaryPath = extractBinary();
        binaryPath.toFile().setExecutable(true);

        // Validate the binary works with a quick probe
        validateBinary();

        initialized = true;
        PluginLogger.info("llama.cpp engine ready. Threads: " + CPU_THREADS
                + ", Context: " + CONTEXT_SIZE);
    }

    @Override
    public AIResponse generate(String prompt, String systemPrompt,
                               int maxNewTokens, float temperature) {
        ensureInitialized();

        // Serialize concurrent calls — llama.cpp is not thread-safe across subprocesses
        inferenceLock.lock();
        try {
            return runInference(prompt, systemPrompt, maxNewTokens, temperature);
        } finally {
            inferenceLock.unlock();
        }
    }

    @Override
    public String getEngineName() {
        return "llama.cpp (GGUF)";
    }

    @Override
    public void close() {
        initialized = false;
        // Subprocess is short-lived; no persistent handle to close
        PluginLogger.info("llama.cpp engine closed.");
    }

    // -----------------------------------------------------------------------
    // Core inference logic
    // -----------------------------------------------------------------------

    /**
     * Runs a llama.cpp subprocess for a single inference call.
     *
     * <p>Format: {@code llama-cli -m <model> -p "<prompt>" -n <tokens> ...}
     */
    private AIResponse runInference(String prompt, String systemPrompt,
                                    int maxNewTokens, float temperature) {
        String fullPrompt = buildPrompt(prompt, systemPrompt);
        long startMs = System.currentTimeMillis();

        List<String> command = buildCommand(fullPrompt, maxNewTokens, temperature);
        PluginLogger.debug("Launching llama.cpp subprocess...");

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false); // Separate stdout and stderr
            pb.environment().put("LLAMA_NO_MMAP", "0"); // Enable mmap for speed

            Process process = pb.start();

            // Read stderr asynchronously to prevent buffer deadlocks
            StringBuilder stderrBuffer = new StringBuilder();
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        stderrBuffer.append(line).append("\n");
                    }
                } catch (IOException ignored) {}
            }, "llama-stderr-reader");
            stderrThread.setDaemon(true);
            stderrThread.start();

            // Read generated text from stdout
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(SUBPROCESS_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return AIResponse.error("llama.cpp timed out after "
                        + SUBPROCESS_TIMEOUT_SEC + "s");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                PluginLogger.warn("llama.cpp exited with code " + exitCode
                        + ": " + stderrBuffer);
                return AIResponse.error("llama.cpp failed (exit " + exitCode + ")");
            }

            String generatedText = cleanOutput(output.toString(), fullPrompt);
            long latencyMs = System.currentTimeMillis() - startMs;

            PluginLogger.debug("Generated " + generatedText.length() + " chars in " + latencyMs + "ms");
            return AIResponse.success(generatedText, latencyMs);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AIResponse.error("Inference interrupted");
        } catch (IOException e) {
            return AIResponse.error("Failed to launch llama.cpp: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Command construction
    // -----------------------------------------------------------------------

    private List<String> buildCommand(String prompt, int maxTokens, float temperature) {
        List<String> cmd = new ArrayList<>();
        cmd.add(binaryPath.toAbsolutePath().toString());
        cmd.add("-m");
        cmd.add(modelPath.toAbsolutePath().toString());
        cmd.add("-p");
        cmd.add(prompt);
        cmd.add("-n");
        cmd.add(String.valueOf(maxTokens));
        cmd.add("--temp");
        cmd.add(String.format("%.2f", temperature));
        cmd.add("--ctx-size");
        cmd.add(String.valueOf(CONTEXT_SIZE));
        cmd.add("-t");
        cmd.add(String.valueOf(CPU_THREADS));
        cmd.add("--no-display-prompt"); // Suppress echo of input prompt
        cmd.add("--log-disable");       // Suppress llama.cpp logging to stderr
        // Repeat penalty to reduce repetitive output
        cmd.add("--repeat-penalty");
        cmd.add("1.1");
        cmd.add("--repeat-last-n");
        cmd.add("64");
        return cmd;
    }

    /**
     * Constructs the full prompt in ChatML format understood by code models.
     */
    private String buildPrompt(String userPrompt, String systemPrompt) {
        StringBuilder sb = new StringBuilder();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            sb.append("<|im_start|>system\n")
              .append(systemPrompt.trim())
              .append("\n<|im_end|>\n");
        }

        sb.append("<|im_start|>user\n")
          .append(userPrompt.trim())
          .append("\n<|im_end|>\n")
          .append("<|im_start|>assistant\n");

        return sb.toString();
    }

    /**
     * Strips the echoed prompt and stop tokens from llama.cpp output.
     */
    private String cleanOutput(String raw, String prompt) {
        String text = raw;
        // Remove echoed prompt if present
        int promptEnd = text.indexOf("<|im_start|>assistant");
        if (promptEnd >= 0) {
            text = text.substring(promptEnd + "<|im_start|>assistant".length()).trim();
        }
        // Remove end-of-turn token
        text = text.replace("<|im_end|>", "").trim();
        return text;
    }

    // -----------------------------------------------------------------------
    // Binary extraction
    // -----------------------------------------------------------------------

    /**
     * Extracts the appropriate llama.cpp binary from plugin resources to a
     * temp directory. The binary is cached across sessions.
     */
    private Path extractBinary() throws IOException {
        String binaryName = getPlatformBinaryName();
        Path tempDir = Files.createTempDirectory("aicodepilot-llama");
        Path dest = tempDir.resolve(binaryName);

        try (InputStream is = getClass().getResourceAsStream("/native/" + binaryName)) {
            if (is == null) {
                throw new FileNotFoundException(
                        "llama.cpp binary not found in plugin resources: " + binaryName
                        + ". Please download llama.cpp and place the binary in plugin/native/");
            }
            Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
        }

        PluginLogger.info("Extracted llama binary to: " + dest);
        return dest;
    }

    private String getPlatformBinaryName() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return LLAMA_BINARY_WINDOWS;
        if (os.contains("mac")) return LLAMA_BINARY_MACOS;
        return LLAMA_BINARY_LINUX;
    }

    private void validateBinary() throws Exception {
        Process p = new ProcessBuilder(binaryPath.toString(), "--version").start();
        boolean done = p.waitFor(5, TimeUnit.SECONDS);
        if (!done || p.exitValue() != 0) {
            throw new Exception("llama.cpp binary validation failed");
        }
    }

    private void ensureInitialized() {
        if (!initialized) throw new IllegalStateException("Engine not initialized");
    }
}
