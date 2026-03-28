package com.aicodepilot.engine.llm;

import ai.djl.Device;
import ai.djl.Model;
import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.nlp.generate.CausalLMOutput;
import ai.djl.modality.nlp.generate.ContrastiveSeachConfig;
import ai.djl.modality.nlp.generate.TextGenerationConfig;
import ai.djl.translate.TranslateException;
import com.aicodepilot.model.AIResponse;
import com.aicodepilot.util.PluginLogger;

import java.io.IOException;
import java.nio.file.Path;

/**
 * InferenceEngine backed by Deep Java Library (DJL).
 *
 * <p>DJL provides a clean Java API over PyTorch, MXNet, and TensorFlow.
 * This implementation uses the PyTorch backend with a TorchScript (.pt)
 * model for CPU inference. DJL handles tensor operations natively.
 *
 * <p><b>Recommended model:</b> StarCoder2-3B or CodeT5+ exported to
 * TorchScript format. DJL's model zoo can download models automatically
 * but we use a local path for fully offline operation.
 *
 * <p><b>DJL advantages:</b>
 * <ul>
 *   <li>Pure Java API — no subprocess or JNI complexity</li>
 *   <li>Good memory management via NDArray auto-close</li>
 *   <li>Built-in batch support for future throughput optimization</li>
 * </ul>
 */
public class DJLInferenceEngine implements InferenceEngine {

    private final Path modelPath;
    private Model model;
    private Predictor<String, String> predictor;
    private volatile boolean initialized = false;

    public DJLInferenceEngine(Path modelPath) {
        this.modelPath = modelPath;
    }

    @Override
    public void initialize() throws Exception {
        PluginLogger.info("Initializing DJL inference engine...");

        // Force CPU device — no GPU dependency required
        Device device = Device.cpu();

        model = Model.newInstance("code-assistant", device);
        try {
            model.load(modelPath.getParent(), modelPath.getFileName().toString());
        } catch (IOException | ModelException e) {
            throw new Exception("Failed to load DJL model from: " + modelPath, e);
        }

        // Create a text generation translator
        // In production, wire up a proper HuggingFace-compatible translator
        CodeGenerationTranslator translator = new CodeGenerationTranslator();
        predictor = model.newPredictor(translator);

        initialized = true;
        PluginLogger.info("DJL engine ready. Device: " + device);
    }

    @Override
    public AIResponse generate(String prompt, String systemPrompt,
                               int maxNewTokens, float temperature) {
        ensureInitialized();
        long startMs = System.currentTimeMillis();
        try {
            String fullPrompt = buildPrompt(prompt, systemPrompt);
            String result = predictor.predict(fullPrompt);
            return AIResponse.success(result, System.currentTimeMillis() - startMs);
        } catch (TranslateException e) {
            PluginLogger.error("DJL inference failed", e);
            return AIResponse.error("DJL prediction failed: " + e.getMessage());
        }
    }

    @Override
    public String getEngineName() { return "DJL/PyTorch"; }

    @Override
    public void close() {
        if (predictor != null) predictor.close();
        if (model != null) model.close();
        initialized = false;
    }

    private String buildPrompt(String prompt, String system) {
        if (system == null || system.isBlank()) return prompt;
        return "System: " + system + "\n\nUser: " + prompt + "\nAssistant:";
    }

    private void ensureInitialized() {
        if (!initialized) throw new IllegalStateException("DJL engine not initialized");
    }
}


// =============================================================================
// RuleBasedEngine.java
// =============================================================================

package com.aicodepilot.engine.llm;

import com.aicodepilot.model.AIResponse;
import com.aicodepilot.util.PluginLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Fallback rule-based "engine" for when no LLM model is available.
 *
 * <p>Provides deterministic, pattern-based code suggestions and analysis.
 * Not as capable as a real LLM but still valuable for common Java patterns:
 * <ul>
 *   <li>Detecting obvious null pointer risks</li>
 *   <li>Identifying missing annotations (@Override, @NotNull)</li>
 *   <li>Suggesting standard boilerplate</li>
 *   <li>Detecting SQL injection risks</li>
 * </ul>
 *
 * <p>This engine is always available as a baseline — no model download needed.
 */
public class RuleBasedEngine implements InferenceEngine {

    // -----------------------------------------------------------------------
    // Pattern-based rules
    // -----------------------------------------------------------------------

    private record Rule(Pattern pattern, String suggestion, String category) {}

    private static final List<Rule> RULES = List.of(
        // Null safety rules
        new Rule(Pattern.compile("\\.get\\(\\)\\s*\\."),
                "⚠️ NPE Risk: Chain on Optional.get() without isPresent() check. "
                + "Use .orElse() or .orElseThrow() instead.",
                "NULL_SAFETY"),
        new Rule(Pattern.compile("\\w+\\.\\w+\\s*!=\\s*null\\s*\\?\\s*\\w+\\.\\w+\\s*:\\s*null"),
                "💡 Improvement: Replace null-check ternary with Optional.ofNullable() chain.",
                "MODERNIZE"),
        new Rule(Pattern.compile("catch\\s*\\(Exception\\s+\\w+\\)\\s*\\{\\s*\\}"),
                "🐛 Bug: Empty catch block silently swallows exceptions. "
                + "At minimum, log the exception.",
                "ERROR_HANDLING"),
        new Rule(Pattern.compile("catch\\s*\\(Exception\\s+\\w+\\)"),
                "⚠️ Anti-pattern: Catching raw Exception. "
                + "Prefer specific exception types for clarity and correct error handling.",
                "ERROR_HANDLING"),
        // SQL injection risks
        new Rule(Pattern.compile("\"SELECT.*\\+.*\""),
                "🔴 Security: String-concatenated SQL query detected — SQL Injection risk! "
                + "Use PreparedStatement with parameterized queries.",
                "SECURITY"),
        // Thread safety
        new Rule(Pattern.compile("static\\s+(?!final)\\w+\\s+\\w+\\s*="),
                "⚠️ Thread-safety: Mutable static field without 'final'. "
                + "Consider AtomicReference or synchronization.",
                "THREAD_SAFETY"),
        // equals/hashCode
        new Rule(Pattern.compile("@Override\\s+public\\s+boolean\\s+equals"),
                "💡 Reminder: If you override equals(), also override hashCode() to maintain contract.",
                "BEST_PRACTICE"),
        // Resource management
        new Rule(Pattern.compile("new\\s+FileInputStream|new\\s+FileOutputStream|new\\s+BufferedReader"),
                "💡 Use try-with-resources for file streams to ensure proper closing.",
                "RESOURCE_MANAGEMENT"),
        // Spring-specific
        new Rule(Pattern.compile("@Autowired\\s+private"),
                "💡 Spring: Prefer constructor injection over @Autowired field injection "
                + "for better testability and immutability.",
                "SPRING"),
        // Logging
        new Rule(Pattern.compile("System\\.out\\.println"),
                "⚠️ Use a proper logger (SLF4J/Log4j2) instead of System.out.println in production code.",
                "LOGGING"),
        // JPA N+1
        new Rule(Pattern.compile("@OneToMany(?!.*fetch)"),
                "⚠️ JPA: @OneToMany without explicit fetch type defaults to LAZY. "
                + "Be explicit: @OneToMany(fetch = FetchType.LAZY). Watch for N+1 query problems.",
                "JPA"),
        // Magic numbers
        new Rule(Pattern.compile("(?<![\\w.])(?!0|1|-1)\\d{2,}(?![\\w.])"),
                "💡 Code quality: Magic number detected. Extract to a named constant for readability.",
                "CODE_QUALITY")
    );

    private volatile boolean initialized = false;

    @Override
    public void initialize() {
        initialized = true;
        PluginLogger.info("Rule-based fallback engine initialized ("
                + RULES.size() + " rules loaded).");
    }

    @Override
    public AIResponse generate(String prompt, String systemPrompt,
                               int maxNewTokens, float temperature) {
        ensureInitialized();
        long startMs = System.currentTimeMillis();

        List<String> findings = new ArrayList<>();
        String codeToAnalyze = extractCode(prompt);

        for (Rule rule : RULES) {
            if (rule.pattern().matcher(codeToAnalyze).find()) {
                findings.add("[" + rule.category() + "] " + rule.suggestion());
            }
        }

        String result;
        if (findings.isEmpty()) {
            result = "✅ No obvious issues detected by rule-based analysis.\n\n"
                   + "Note: Install a GGUF model in the model directory for AI-powered "
                   + "deep analysis with natural language suggestions.";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("📋 Rule-Based Analysis Results:\n\n");
            for (int i = 0; i < findings.size(); i++) {
                sb.append(i + 1).append(". ").append(findings.get(i)).append("\n\n");
            }
            sb.append("---\nNote: Install a GGUF model for AI-powered analysis.");
            result = sb.toString();
        }

        return AIResponse.success(result, System.currentTimeMillis() - startMs);
    }

    @Override
    public String getEngineName() { return "Rule-Based (no model)"; }

    @Override
    public void close() { initialized = false; }

    private String extractCode(String prompt) {
        // Extract code section from prompt (after "Code:" or similar markers)
        int idx = prompt.indexOf("Code:");
        return idx >= 0 ? prompt.substring(idx + 5) : prompt;
    }

    private void ensureInitialized() {
        if (!initialized) throw new IllegalStateException("Rule engine not initialized");
    }
}
