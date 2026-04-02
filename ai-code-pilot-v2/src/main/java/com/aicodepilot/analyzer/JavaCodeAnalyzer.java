package com.aicodepilot.analyzer;

import com.aicodepilot.engine.AIEngineManager;
import com.aicodepilot.model.AIRequest;
import com.aicodepilot.model.AIResponse;
import com.aicodepilot.util.PluginLogger;
import com.aicodepilot.util.PromptTemplates;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Analyzes Java source code at multiple levels:
 * <ol>
 *   <li><b>Structural analysis</b> — uses JavaParser AST to extract class structure,
 *       method signatures, annotations, and infer purpose (Controller, Service, Repository…)</li>
 *   <li><b>Static rule analysis</b> — detects anti-patterns without AI (fast, always available)</li>
 *   <li><b>AI-powered analysis</b> — sends structured context to the LLM for deep insights</li>
 * </ol>
 *
 * <p>Separating static and AI analysis lets results appear in two stages:
 * static findings appear immediately, while AI suggestions load asynchronously.
 */
public class JavaCodeAnalyzer {

    // -----------------------------------------------------------------------
    // Dependencies
    // -----------------------------------------------------------------------

    private final AIEngineManager engineManager;
    private final JavaParser javaParser;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    public JavaCodeAnalyzer(AIEngineManager engineManager) {
        this.engineManager = engineManager;
        this.javaParser    = new JavaParser();
    }

    // -----------------------------------------------------------------------
    // Main Analysis Entry Points
    // -----------------------------------------------------------------------

    /**
     * Full analysis of a Java source snippet or complete class.
     *
     * @param sourceCode the Java source text to analyze
     * @return {@link AnalysisResult} containing AST info, static findings, and AI suggestions
     */
    public AnalysisResult analyze(String sourceCode) {
        AnalysisResult result = new AnalysisResult(sourceCode);

        // Phase 1: AST-based structural analysis (fast, always runs)
        performStructuralAnalysis(sourceCode, result);

        // Phase 2: AI-powered deep analysis (requires engine to be ready)
        if (engineManager != null && engineManager.isReady()) {
            performAIAnalysis(sourceCode, result);
        } else {
            result.addWarning("AI engine not ready — static analysis only.");
        }

        return result;
    }

    // -----------------------------------------------------------------------
    // Phase 1: Structural analysis via JavaParser AST
    // -----------------------------------------------------------------------

    private void performStructuralAnalysis(String source, AnalysisResult result) {
        ParseResult<CompilationUnit> parseResult = javaParser.parse(source);

        if (!parseResult.isSuccessful()) {
            result.addParseError("Code has syntax errors — cannot perform AST analysis.");
            parseResult.getProblems().forEach(p ->
                    result.addParseError("Line " + p.getLocation().map(Object::toString).orElse("?")
                            + ": " + p.getMessage()));
            return;
        }

        CompilationUnit cu = parseResult.getResult().orElseThrow();

        // Infer class purpose from annotations and naming
        InferenceVisitor inferVisitor = new InferenceVisitor();
        inferVisitor.visit(cu, result);

        // Detect static anti-patterns
        AntiPatternVisitor antiPatternVisitor = new AntiPatternVisitor();
        antiPatternVisitor.visit(cu, result);

        // Collect metrics
        MetricsVisitor metricsVisitor = new MetricsVisitor();
        metricsVisitor.visit(cu, result);

        result.setStructuralAnalysisComplete(true);
    }

    // -----------------------------------------------------------------------
    // Phase 2: AI-powered analysis
    // -----------------------------------------------------------------------

    private void performAIAnalysis(String source, AnalysisResult result) {
        // Build a rich context-aware prompt from structural analysis results
        String contextualPrompt = buildAnalysisPrompt(source, result);

        AIRequest request = AIRequest.builder()
                .prompt(contextualPrompt)
                .systemPrompt(PromptTemplates.CODE_ANALYSIS_SYSTEM_PROMPT)
                .requestType(AIRequest.RequestType.ANALYZE_CODE)
                .maxNewTokens(600)
                .temperature(0.1f) // Low temperature for consistent analysis output
                .build();

        try {
            AIResponse aiResponse = engineManager.infer(request);
            if (aiResponse.isSuccess()) {
                result.setAISuggestions(aiResponse.getText());
                result.addMetric("ai_latency_ms", String.valueOf(aiResponse.getLatencyMs()));
                result.addMetric("cache_hit", String.valueOf(aiResponse.isCacheHit()));
            } else {
                result.addWarning("AI analysis failed: " + aiResponse.getErrorMessage());
            }
        } catch (Exception e) {
            PluginLogger.error("AI analysis threw exception", e);
            result.addWarning("AI analysis error: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Prompt builder — enriches prompt with structural context
    // -----------------------------------------------------------------------

    private String buildAnalysisPrompt(String source, AnalysisResult result) {
        StringBuilder sb = new StringBuilder();

        sb.append("Analyze the following Java code and provide:\n");
        sb.append("1. Identified purpose/role of this class\n");
        sb.append("2. Code quality issues and improvements\n");
        sb.append("3. Potential bugs or risks\n");
        sb.append("4. Architecture recommendations\n\n");

        // Include structural analysis context to help the LLM
        if (!result.getInferredRoles().isEmpty()) {
            sb.append("Context (detected): This appears to be a ")
              .append(String.join(", ", result.getInferredRoles()))
              .append(".\n\n");
        }

        if (!result.getStaticFindings().isEmpty()) {
            sb.append("Static analysis already detected:\n");
            result.getStaticFindings().forEach(f -> sb.append("- ").append(f).append("\n"));
            sb.append("\n");
        }

        sb.append("Code:\n```java\n").append(trimToTokenLimit(source, 1500)).append("\n```");
        return sb.toString();
    }

    /**
     * Prevents oversized prompts from exceeding the model's context window.
     */
    private String trimToTokenLimit(String code, int approxTokenLimit) {
        // Rough approximation: 1 token ≈ 4 characters
        int charLimit = approxTokenLimit * 4;
        if (code.length() <= charLimit) return code;
        return code.substring(0, charLimit)
                + "\n... [truncated to fit context window]";
    }

    // -----------------------------------------------------------------------
    // AST Visitors
    // -----------------------------------------------------------------------

    /**
     * Infers the class role (Controller, Service, Repository, Entity, etc.)
     * from Spring annotations and naming conventions.
     */
    private static class InferenceVisitor extends VoidVisitorAdapter<AnalysisResult> {

        @Override
        public void visit(ClassOrInterfaceDeclaration cls, AnalysisResult result) {
            String className = cls.getNameAsString();
            result.setClassName(className);

            // Annotation-based role detection
            cls.getAnnotations().forEach(ann -> {
                switch (ann.getNameAsString()) {
                    case "RestController", "Controller" -> result.addRole("Spring REST Controller");
                    case "Service"     -> result.addRole("Spring Service");
                    case "Repository"  -> result.addRole("Spring Repository/DAO");
                    case "Entity"      -> result.addRole("JPA Entity");
                    case "Component"   -> result.addRole("Spring Component");
                    case "Configuration" -> result.addRole("Spring Configuration");
                    case "KafkaListener" -> result.addRole("Kafka Consumer");
                }
            });

            // Name-based heuristics as fallback
            if (result.getInferredRoles().isEmpty()) {
                if (className.endsWith("Controller")) result.addRole("Controller (by name)");
                else if (className.endsWith("Service")) result.addRole("Service (by name)");
                else if (className.endsWith("Repository")) result.addRole("Repository (by name)");
                else if (className.endsWith("DTO") || className.endsWith("Request")
                        || className.endsWith("Response")) result.addRole("Data Transfer Object");
                else if (className.endsWith("Entity") || className.endsWith("Model"))
                    result.addRole("Domain Model/Entity");
            }

            super.visit(cls, result);
        }
    }

    /**
     * Detects common Java anti-patterns using AST matching.
     * Pattern matching on AST nodes is more accurate than regex.
     */
    private static class AntiPatternVisitor extends VoidVisitorAdapter<AnalysisResult> {

        @Override
        public void visit(MethodDeclaration method, AnalysisResult result) {
            // Detect methods with too many parameters (>5 suggests bad design)
            if (method.getParameters().size() > 5) {
                result.addFinding("Method '" + method.getNameAsString() + "' has "
                        + method.getParameters().size() + " parameters. "
                        + "Consider introducing a parameter object.");
            }

            // Detect overly long methods (>50 statements suggests too much responsibility)
            long stmtCount = method.getBody()
                    .map(b -> b.getStatements().stream().count())
                    .orElse(0L);
            if (stmtCount > 50) {
                result.addFinding("Method '" + method.getNameAsString()
                        + "' has " + stmtCount + " statements. "
                        + "Consider splitting into smaller, focused methods (SRP).");
            }

            super.visit(method, result);
        }

        @Override
        public void visit(CatchClause catchClause, AnalysisResult result) {
            // Empty catch blocks
            if (catchClause.getBody().isEmpty()) {
                result.addFinding("Empty catch block detected — exception is silently swallowed.");
            }

            // Catching raw Exception
            String exType = catchClause.getParameter().getTypeAsString();
            if ("Exception".equals(exType) || "Throwable".equals(exType)) {
                result.addFinding("Catching '" + exType + "' is too broad — catch specific exceptions.");
            }
            super.visit(catchClause, result);
        }

        @Override
        public void visit(FieldDeclaration field, AnalysisResult result) {
            // Check for public mutable fields (breaks encapsulation)
            if (field.isPublic() && !field.isFinal() && !field.isStatic()) {
                result.addFinding("Public mutable field '" + field.getVariable(0).getNameAsString()
                        + "' detected. Use private with getter/setter for encapsulation.");
            }

            // Detect @Autowired on field (prefer constructor injection)
            boolean hasAutowired = field.getAnnotations().stream()
                    .anyMatch(a -> "Autowired".equals(a.getNameAsString()));
            if (hasAutowired) {
                result.addFinding("Field injection via @Autowired on '"
                        + field.getVariable(0).getNameAsString()
                        + "'. Prefer constructor injection for testability.");
            }
            super.visit(field, result);
        }

        @Override
        public void visit(BinaryExpr expr, AnalysisResult result) {
            // Detect string equality via == instead of .equals()
            if (expr.getOperator() == BinaryExpr.Operator.EQUALS
                    || expr.getOperator() == BinaryExpr.Operator.NOT_EQUALS) {
                // Heuristic: if left side looks like a String literal
                if (expr.getLeft() instanceof StringLiteralExpr
                        || expr.getRight() instanceof StringLiteralExpr) {
                    result.addFinding("String compared with == / != instead of .equals(). "
                            + "This compares object references, not values.");
                }
            }
            super.visit(expr, result);
        }

        @Override
        public void visit(ObjectCreationExpr expr, AnalysisResult result) {
            // Detect new Thread() without thread pool
            if ("Thread".equals(expr.getTypeAsString())) {
                result.addFinding("Direct Thread creation detected. "
                        + "Use ExecutorService or Spring's @Async for better lifecycle management.");
            }
            super.visit(expr, result);
        }
    }

    /**
     * Collects code metrics: LOC, method count, complexity indicators.
     */
    private static class MetricsVisitor extends VoidVisitorAdapter<AnalysisResult> {
        private int methodCount = 0;
        private int fieldCount  = 0;

        @Override
        public void visit(MethodDeclaration m, AnalysisResult result) {
            methodCount++;
            super.visit(m, result);
        }

        @Override
        public void visit(FieldDeclaration f, AnalysisResult result) {
            fieldCount++;
            result.addMetric("method_count", String.valueOf(methodCount));
            result.addMetric("field_count", String.valueOf(fieldCount));
            super.visit(f, result);
        }
    }
}
