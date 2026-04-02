package com.aicodepilot.debug;

import com.aicodepilot.engine.AIEngineManager;
import com.aicodepilot.model.AIRequest;
import com.aicodepilot.model.AIResponse;
import com.aicodepilot.util.PromptTemplates;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects bugs, security vulnerabilities, and performance anti-patterns.
 *
 * <p>Uses a two-pass approach:
 * <ol>
 *   <li>Fast AST-based bug detection — instant results, no AI needed</li>
 *   <li>AI-powered analysis — deeper semantic understanding of bugs</li>
 * </ol>
 *
 * <p>Bug categories covered:
 * <ul>
 *   <li>Null safety (NPE risks, missing null checks)</li>
 *   <li>Resource leaks (unclosed streams, connections)</li>
 *   <li>Thread safety (shared mutable state, visibility)</li>
 *   <li>Security (SQL injection, log injection, path traversal)</li>
 *   <li>Performance (N+1 queries, premature boxing, inefficient collections)</li>
 *   <li>JPA pitfalls (N+1, detached entities, transaction boundaries)</li>
 * </ul>
 */
public class DebugAssistant {

    // -----------------------------------------------------------------------
    // Bug record — one entry per detected issue
    // -----------------------------------------------------------------------

    public record Bug(
            String category,
            String severity,     // CRITICAL, HIGH, MEDIUM, LOW
            String title,
            String description,
            String suggestedFix,
            int lineNumber       // -1 if unknown
    ) {}

    // -----------------------------------------------------------------------
    // Dependencies
    // -----------------------------------------------------------------------

    private final AIEngineManager engineManager;
    private final JavaParser javaParser;

    public DebugAssistant(AIEngineManager engineManager) {
        this.engineManager = engineManager;
        this.javaParser    = new JavaParser();
    }

    // -----------------------------------------------------------------------
    // Main API
    // -----------------------------------------------------------------------

    /**
     * Full bug detection: static AST scan + AI semantic analysis.
     *
     * @param sourceCode Java source to analyze
     * @return DebugReport with all detected bugs and suggestions
     */
    public DebugReport detect(String sourceCode) {
        DebugReport report = new DebugReport(sourceCode);

        // Phase 1: Fast static detection (always runs)
        runStaticBugDetection(sourceCode, report);

        // Phase 2: AI analysis (if engine ready)
        if (engineManager != null && engineManager.isReady()) {
            runAIBugAnalysis(sourceCode, report);
        }

        return report;
    }

    /**
     * Asks the AI to explain a specific exception stack trace and suggest fixes.
     *
     * @param stackTrace  the exception stack trace text
     * @param sourceCode  optional relevant source code snippet
     */
    public AIResponse explainException(String stackTrace, String sourceCode) {
        String prompt = String.format("""
            Analyze this Java exception stack trace and provide:
            1. Root cause explanation (plain English)
            2. The specific code location most likely responsible
            3. Step-by-step fix instructions
            4. How to prevent this in future (defensive programming tips)
            
            Stack trace:
            ```
            %s
            ```
            
            %s
            """,
            stackTrace,
            sourceCode != null && !sourceCode.isBlank()
                ? "Relevant source code:\n```java\n" + sourceCode + "\n```"
                : "");

        return engineManager.infer(AIRequest.builder()
                .prompt(prompt)
                .systemPrompt(PromptTemplates.DEBUG_SYSTEM_PROMPT)
                .requestType(AIRequest.RequestType.DETECT_BUGS)
                .maxNewTokens(600)
                .temperature(0.1f)
                .build());
    }

    // -----------------------------------------------------------------------
    // Phase 1: Static bug detection via AST
    // -----------------------------------------------------------------------

    private void runStaticBugDetection(String source, DebugReport report) {
        var parseResult = javaParser.parse(source);
        if (!parseResult.isSuccessful()) return;

        CompilationUnit cu = parseResult.getResult().orElseThrow();
        new BugDetectorVisitor().visit(cu, report);
    }

    private static class BugDetectorVisitor extends VoidVisitorAdapter<DebugReport> {

        @Override
        public void visit(MethodCallExpr call, DebugReport report) {
            String methodName = call.getNameAsString();

            // NPE: calling method on potentially-null result without check
            if (methodName.equals("get") && call.getScope().isPresent()) {
                String scope = call.getScope().get().toString();
                // Heuristic: Optional.get() without guard
                if (scope.endsWith("Optional") || scope.startsWith("Optional")) {
                    report.addBug(new Bug(
                        "NULL_SAFETY", "HIGH",
                        "Unsafe Optional.get()",
                        "Calling .get() on an Optional without checking isPresent() "
                        + "will throw NoSuchElementException if empty.",
                        "Replace with .orElseThrow() or .orElse(defaultValue) or .ifPresent().",
                        call.getBegin().map(p -> p.line).orElse(-1)
                    ));
                }
            }

            // String.format with user input — potential log injection
            if ((methodName.equals("format") || methodName.equals("printf"))
                    && call.getArguments().size() > 0) {
                String firstArg = call.getArguments().get(0).toString();
                if (!firstArg.startsWith("\"")) {
                    report.addBug(new Bug(
                        "SECURITY", "MEDIUM",
                        "Possible Log/Format Injection",
                        "The format string for String.format() is not a literal — "
                        + "if user-controlled, this is a format string vulnerability.",
                        "Ensure the format string is a compile-time constant literal.",
                        call.getBegin().map(p -> p.line).orElse(-1)
                    ));
                }
            }

            // Thread.sleep in production code
            if (methodName.equals("sleep")
                    && call.getScope().map(s -> s.toString().contains("Thread")).orElse(false)) {
                report.addBug(new Bug(
                    "PERFORMANCE", "LOW",
                    "Thread.sleep() detected",
                    "Thread.sleep() blocks a thread and wastes resources. "
                    + "In reactive or Spring apps, use non-blocking delays.",
                    "Consider ScheduledExecutorService, CompletableFuture.delayedExecutor(), "
                    + "or Reactor's Mono.delay().",
                    call.getBegin().map(p -> p.line).orElse(-1)
                ));
            }

            super.visit(call, report);
        }

        @Override
        public void visit(BinaryExpr expr, DebugReport report) {
            // String == comparison
            if ((expr.getOperator() == BinaryExpr.Operator.EQUALS
                 || expr.getOperator() == BinaryExpr.Operator.NOT_EQUALS)
                    && (expr.getLeft() instanceof StringLiteralExpr
                        || expr.getRight() instanceof StringLiteralExpr)) {
                report.addBug(new Bug(
                    "BUG", "HIGH",
                    "String compared with == / !=",
                    "String comparison with == checks reference equality, not value equality. "
                    + "This will fail for non-interned strings.",
                    "Use .equals() or Objects.equals() for safe null-handling.",
                    expr.getBegin().map(p -> p.line).orElse(-1)
                ));
            }
            super.visit(expr, report);
        }

        @Override
        public void visit(TryStmt tryStmt, DebugReport report) {
            // Try block without catch or finally — resource leaks
            if (tryStmt.getCatchClauses().isEmpty()
                    && tryStmt.getFinallyBlock().isEmpty()
                    && tryStmt.getResources().isEmpty()) {
                report.addBug(new Bug(
                    "BUG", "MEDIUM",
                    "Try block with no catch or finally",
                    "A try block with no catch clause and no finally block will not "
                    + "handle exceptions. This is likely incomplete code.",
                    "Add appropriate catch clauses and/or a finally block.",
                    tryStmt.getBegin().map(p -> p.line).orElse(-1)
                ));
            }
            super.visit(tryStmt, report);
        }

        @Override
        public void visit(ObjectCreationExpr expr, DebugReport report) {
            // Detect new Random() — not thread-safe and weak seeding
            if ("Random".equals(expr.getTypeAsString())) {
                report.addBug(new Bug(
                    "THREAD_SAFETY", "LOW",
                    "java.util.Random is not thread-safe",
                    "Sharing a java.util.Random instance across threads causes race conditions "
                    + "and poor random distribution.",
                    "Use ThreadLocalRandom.current() for single-threaded use, "
                    + "or SecureRandom for security-sensitive contexts.",
                    expr.getBegin().map(p -> p.line).orElse(-1)
                ));
            }
            super.visit(expr, report);
        }
    }

    // -----------------------------------------------------------------------
    // Phase 2: AI-powered analysis
    // -----------------------------------------------------------------------

    private void runAIBugAnalysis(String source, DebugReport report) {
        String knownIssues = report.getBugs().isEmpty() ? "none" :
                report.getBugs().stream()
                        .map(b -> "- [" + b.category() + "] " + b.title())
                        .reduce("", (a, b) -> a + b + "\n");

        String prompt = String.format("""
            Perform a deep security and correctness review of this Java code.
            
            The following issues were already detected by static analysis:
            %s
            
            Focus on finding ADDITIONAL issues not listed above, especially:
            - Concurrency bugs (race conditions, deadlocks, visibility)
            - JPA pitfalls (N+1, lazy initialization, transaction boundary issues)
            - Memory leaks (unclosed resources, retained references)
            - Business logic errors
            - Edge cases not handled
            
            For each issue found:
            SEVERITY: [CRITICAL|HIGH|MEDIUM|LOW]
            TITLE: one-line description
            EXPLANATION: 2-3 sentence explanation
            FIX: concrete fix code or instructions
            
            Code:
            ```java
            %s
            ```
            """, knownIssues, trimSource(source));

        AIResponse aiResponse = engineManager.infer(AIRequest.builder()
                .prompt(prompt)
                .systemPrompt(PromptTemplates.DEBUG_SYSTEM_PROMPT)
                .requestType(AIRequest.RequestType.DETECT_BUGS)
                .maxNewTokens(700)
                .temperature(0.1f)
                .build());

        if (aiResponse.isSuccess()) {
            report.setAIAnalysis(aiResponse.getText());
        }
    }

    private String trimSource(String source) {
        return source.length() > 6000 ? source.substring(0, 6000) + "\n// [truncated]" : source;
    }

    // -----------------------------------------------------------------------
    // DebugReport — holds all findings from one analysis run
    // -----------------------------------------------------------------------

    public static class DebugReport {
        private final String source;
        private final List<Bug> bugs = new ArrayList<>();
        private String aiAnalysis = "";

        public DebugReport(String source) { this.source = source; }

        public void addBug(Bug bug)         { bugs.add(bug); }
        public void setAIAnalysis(String a) { this.aiAnalysis = a; }

        public List<Bug> getBugs()   { return List.copyOf(bugs); }
        public String getAIAnalysis() { return aiAnalysis; }

        public String toFormattedReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("═══════════════════════════════════════\n");
            sb.append(" AI Code Pilot — Debug Report\n");
            sb.append("═══════════════════════════════════════\n\n");
            sb.append("🐛 Static Bugs Found: ").append(bugs.size()).append("\n\n");

            for (int i = 0; i < bugs.size(); i++) {
                Bug b = bugs.get(i);
                sb.append(i + 1).append(". [").append(b.severity()).append("] ")
                  .append(b.title()).append("\n");
                sb.append("   Category: ").append(b.category()).append("\n");
                if (b.lineNumber() > 0) sb.append("   Line: ").append(b.lineNumber()).append("\n");
                sb.append("   ").append(b.description()).append("\n");
                sb.append("   Fix: ").append(b.suggestedFix()).append("\n\n");
            }

            if (!aiAnalysis.isBlank()) {
                sb.append("🧠 AI Deep Analysis:\n");
                sb.append("──────────────────\n");
                sb.append(aiAnalysis).append("\n");
            }

            return sb.toString();
        }
    }
}
