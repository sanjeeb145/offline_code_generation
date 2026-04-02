package com.aicodepilot.engine.llm;

import com.aicodepilot.model.AIResponse;
import com.aicodepilot.util.PluginLogger;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * InferenceEngine backed by llama.cpp via a managed subprocess.
 *
 * <p>The plugin locates the llama-cli binary from:
 * <ol>
 *   <li>Preference: Window → Preferences → AI Code Pilot → Model Settings → llama.cpp Binary</li>
 *   <li>Fallback: {@code ~/.aicodepilot/llama-cli} (Linux/macOS)</li>
 *   <li>Fallback: {@code ~/.aicodepilot/llama-cli.exe} (Windows)</li>
 * </ol>
 *
 * <p>Each inference call spawns a short-lived subprocess. A {@link ReentrantLock}
 * serialises concurrent calls so RAM usage stays bounded.
 *
 * <p><b>Supported models:</b> Any GGUF file (Q4_K_M quantisation recommended).
 * DeepSeek-Coder-1.3B-Q4_K_M.gguf works well on 8 GB RAM systems.
 */
public class LlamaInferenceEngine implements InferenceEngine {

    // ── Constants ────────────────────────────────────────────────────────────
    private static final int  CONTEXT_SIZE          = 2048;
    private static final int  CPU_THREADS           =
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    private static final long SUBPROCESS_TIMEOUT_S  = 60L;

    // ── State ─────────────────────────────────────────────────────────────────
    private final Path modelPath;
    private final Path binaryPath;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean initialized = false;

    // ── Construction ──────────────────────────────────────────────────────────

    public LlamaInferenceEngine(Path modelPath, Path binaryPath) {
        this.modelPath  = modelPath;
        this.binaryPath = binaryPath;
    }

    // ── InferenceEngine ───────────────────────────────────────────────────────

    @Override
    public void initialize() throws Exception {
        if (!Files.exists(modelPath)) {
            throw new FileNotFoundException("GGUF model not found: " + modelPath);
        }
        if (!Files.exists(binaryPath)) {
            throw new FileNotFoundException("llama-cli binary not found: " + binaryPath);
        }
        binaryPath.toFile().setExecutable(true);
        initialized = true;
        PluginLogger.info("LlamaEngine ready. Model: " + modelPath.getFileName()
                + ", Threads: " + CPU_THREADS);
    }

    @Override
    public AIResponse generate(String prompt, String systemPrompt,
                               int maxNewTokens, float temperature) {
        ensureInitialized();
        lock.lock();
        try {
            return runSubprocess(prompt, systemPrompt, maxNewTokens, temperature);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String getEngineName() {
        return "llama.cpp — " + modelPath.getFileName();
    }

    @Override
    public void close() {
        initialized = false;
    }

    // ── Subprocess logic ──────────────────────────────────────────────────────

    private AIResponse runSubprocess(String prompt, String systemPrompt,
                                     int maxTokens, float temperature) {
        long startMs = System.currentTimeMillis();
        String fullPrompt = buildPrompt(prompt, systemPrompt);

        List<String> cmd = new ArrayList<>();
        cmd.add(binaryPath.toAbsolutePath().toString());
        cmd.add("-m");  cmd.add(modelPath.toAbsolutePath().toString());
        cmd.add("-p");  cmd.add(fullPrompt);
        cmd.add("-n");  cmd.add(String.valueOf(maxTokens));
        cmd.add("--temp"); cmd.add(String.format("%.2f", temperature));
        cmd.add("--ctx-size"); cmd.add(String.valueOf(CONTEXT_SIZE));
        cmd.add("-t"); cmd.add(String.valueOf(CPU_THREADS));
        cmd.add("--no-display-prompt");
        cmd.add("--log-disable");
        cmd.add("--repeat-penalty"); cmd.add("1.1");

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            // Drain stderr on daemon thread to prevent buffer deadlock
            StringBuilder stderr = new StringBuilder();
            Thread stderrReader = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) stderr.append(line).append("\n");
                } catch (IOException ignored) {}
            }, "llama-stderr");
            stderrReader.setDaemon(true);
            stderrReader.start();

            // Read generated text from stdout
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append("\n");
            }

            boolean done = process.waitFor(SUBPROCESS_TIMEOUT_S, TimeUnit.SECONDS);
            if (!done) {
                process.destroyForcibly();
                return AIResponse.error("llama.cpp timed out after " + SUBPROCESS_TIMEOUT_S + "s");
            }

            if (process.exitValue() != 0) {
                return AIResponse.error("llama.cpp exited with code " + process.exitValue());
            }

            String text = cleanOutput(output.toString());
            long latency = System.currentTimeMillis() - startMs;
            PluginLogger.debug("Inference: " + text.length() + " chars in " + latency + "ms");
            return AIResponse.success(text, latency);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AIResponse.error("Inference interrupted");
        } catch (IOException e) {
            return AIResponse.error("Failed to launch llama.cpp: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** ChatML format — understood by most code-focused GGUF models. */
    private String buildPrompt(String prompt, String system) {
        StringBuilder sb = new StringBuilder();
        if (system != null && !system.isBlank()) {
            sb.append("<|im_start|>system\n").append(system.trim()).append("\n<|im_end|>\n");
        }
        sb.append("<|im_start|>user\n")
          .append(prompt.trim())
          .append("\n<|im_end|>\n<|im_start|>assistant\n");
        return sb.toString();
    }

    private String cleanOutput(String raw) {
        return raw.replace("<|im_end|>", "").trim();
    }

    private void ensureInitialized() {
        if (!initialized) throw new IllegalStateException("Engine not initialized");
    }

    // ── Static factory: find llama-cli binary ────────────────────────────────

    /** Finds the llama-cli binary in common locations. Returns null if not found. */
    public static Path findBinary() {
        String home = System.getProperty("user.home");
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String binName = isWindows ? "llama-cli.exe" : "llama-cli";

        List<Path> candidates = List.of(
            Path.of(home, ".aicodepilot", binName),
            Path.of(home, ".aicodepilot", "llama-cli-linux-x64"),
            Path.of(home, ".aicodepilot", "llama-cli-macos-x64"),
            Path.of("/usr/local/bin/llama-cli"),
            Path.of("/usr/bin/llama-cli")
        );

        return candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .orElse(null);
    }
}
