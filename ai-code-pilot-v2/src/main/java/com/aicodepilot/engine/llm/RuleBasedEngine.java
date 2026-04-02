package com.aicodepilot.engine.llm;

import com.aicodepilot.model.AIResponse;
import com.aicodepilot.util.PluginLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Rule-based fallback engine — always available, no model required.
 *
 * <p>Provides deterministic pattern-based analysis for common Java issues.
 * Not as capable as a real LLM but still useful for:
 * <ul>
 *   <li>Null pointer risks</li>
 *   <li>Empty catch blocks</li>
 *   <li>SQL injection patterns</li>
 *   <li>Thread safety issues</li>
 *   <li>Spring best-practice violations</li>
 *   <li>Logging anti-patterns</li>
 * </ul>
 *
 * <p>Used automatically when no GGUF model is configured.
 */
public class RuleBasedEngine implements InferenceEngine {

    private record Rule(Pattern pattern, String category, String message) {}

    private static final List<Rule> RULES = List.of(
        new Rule(Pattern.compile("\\.get\\(\\)\\s*\\."),
            "NULL_SAFETY",
            "NPE Risk: Calling .get() on Optional without isPresent() check. "
            + "Use .orElse(), .orElseThrow(), or .ifPresent() instead."),
        new Rule(Pattern.compile("catch\\s*\\(\\w+\\s+\\w+\\)\\s*\\{\\s*\\}"),
            "ERROR_HANDLING",
            "Empty catch block silently swallows exceptions. "
            + "At minimum, log the exception with the stack trace."),
        new Rule(Pattern.compile("catch\\s*\\((Exception|Throwable)\\s+"),
            "ERROR_HANDLING",
            "Catching raw Exception/Throwable is too broad. "
            + "Catch specific exception types for correct error handling."),
        new Rule(Pattern.compile("\"\\s*(SELECT|INSERT|UPDATE|DELETE).*\"\\s*\\+"),
            "SECURITY",
            "SQL Injection Risk: String-concatenated SQL query detected. "
            + "Use PreparedStatement with parameterized queries."),
        new Rule(Pattern.compile("System\\.out\\.println"),
            "LOGGING",
            "Use SLF4J logger instead of System.out.println in production code. "
            + "Example: log.info(\"message\", args);"),
        new Rule(Pattern.compile("@Autowired\\s*\\n\\s*private"),
            "SPRING",
            "Field injection via @Autowired detected. "
            + "Prefer constructor injection for better testability and immutability."),
        new Rule(Pattern.compile("new\\s+Thread\\s*\\("),
            "THREAD_SAFETY",
            "Direct Thread creation detected. "
            + "Use ExecutorService or Spring @Async for proper lifecycle management."),
        new Rule(Pattern.compile("new\\s+Random\\s*\\("),
            "THREAD_SAFETY",
            "java.util.Random is not thread-safe. "
            + "Use ThreadLocalRandom.current() or SecureRandom for security contexts."),
        new Rule(Pattern.compile("@OneToMany(?!.*fetch)"),
            "JPA",
            "JPA @OneToMany without explicit fetch type. "
            + "Be explicit: @OneToMany(fetch = FetchType.LAZY) to avoid N+1 query problems."),
        new Rule(Pattern.compile("public\\s+(?!final|static)\\w+\\s+\\w+\\s*;"),
            "ENCAPSULATION",
            "Public mutable field detected. "
            + "Use private with getter/setter to maintain encapsulation."),
        new Rule(Pattern.compile("e\\.printStackTrace\\(\\)"),
            "LOGGING",
            "e.printStackTrace() writes to stderr and lacks context. "
            + "Use: log.error(\"Operation failed\", e);"),
        new Rule(Pattern.compile("\\bString\\s*\\+\\s*=|\\+\\s*=.*String"),
            "PERFORMANCE",
            "String concatenation in a potential loop detected. "
            + "Use StringBuilder for multiple concatenations to avoid excessive object creation.")
    );

    private volatile boolean initialized = false;

    @Override
    public void initialize() {
        initialized = true;
        PluginLogger.info("RuleBasedEngine ready (" + RULES.size() + " rules)");
    }

    @Override
    public AIResponse generate(String prompt, String systemPrompt,
                               int maxNewTokens, float temperature) {
        if (!initialized) throw new IllegalStateException("Engine not initialized");
        long start = System.currentTimeMillis();

        // Extract code portion from the prompt
        String code = extractCode(prompt);
        List<String> findings = new ArrayList<>();

        for (Rule rule : RULES) {
            if (rule.pattern().matcher(code).find()) {
                findings.add("[" + rule.category() + "] " + rule.message());
            }
        }

        String result = buildReport(findings);
        return AIResponse.success(result, System.currentTimeMillis() - start);
    }

    @Override
    public String getEngineName() {
        return "Rule-Based (install a GGUF model for AI-powered analysis)";
    }

    @Override
    public void close() {
        initialized = false;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String extractCode(String prompt) {
        // Pull the code section from the prompt
        int idx = prompt.indexOf("```java");
        if (idx >= 0) {
            int end = prompt.indexOf("```", idx + 7);
            return end > idx ? prompt.substring(idx + 7, end) : prompt.substring(idx);
        }
        idx = prompt.indexOf("Code:");
        return idx >= 0 ? prompt.substring(idx + 5) : prompt;
    }

    private String buildReport(List<String> findings) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Rule-Based Analysis ===\n\n");

        if (findings.isEmpty()) {
            sb.append("No common issues detected.\n\n");
            sb.append("Tip: Install a GGUF model for deeper AI-powered analysis.\n");
            sb.append("     Window → Preferences → AI Code Pilot → Model Settings");
        } else {
            sb.append("Found ").append(findings.size()).append(" issue(s):\n\n");
            for (int i = 0; i < findings.size(); i++) {
                sb.append(i + 1).append(". ").append(findings.get(i)).append("\n\n");
            }
            sb.append("---\n");
            sb.append("Install a GGUF model for AI-powered deep analysis with code suggestions.");
        }
        return sb.toString();
    }
}
