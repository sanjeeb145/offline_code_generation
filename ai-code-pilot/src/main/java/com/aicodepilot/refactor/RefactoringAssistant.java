package com.aicodepilot.refactor;

import com.aicodepilot.engine.AIEngineManager;
import com.aicodepilot.model.AIRequest;
import com.aicodepilot.model.AIResponse;
import com.aicodepilot.util.PromptTemplates;

/**
 * Suggests refactoring strategies for Java code using AI pattern recognition.
 *
 * <p>Handles three major refactoring dimensions:
 * <ol>
 *   <li><b>Design patterns</b> — identifies which GoF pattern fits the code</li>
 *   <li><b>Microservice decomposition</b> — identifies bounded contexts</li>
 *   <li><b>Code modernization</b> — upgrades legacy Java to modern idioms</li>
 * </ol>
 */
public class RefactoringAssistant {

    public enum RefactoringStrategy {
        DESIGN_PATTERN_SUGGESTION,
        MICROSERVICE_DECOMPOSITION,
        MODERNIZE_JAVA,
        SOLID_PRINCIPLES_REVIEW,
        EXTRACT_METHOD,
        EXTRACT_CLASS
    }

    private final AIEngineManager engineManager;

    public RefactoringAssistant(AIEngineManager engineManager) {
        this.engineManager = engineManager;
    }

    /**
     * Analyzes code and suggests the most appropriate refactoring strategy.
     *
     * @param sourceCode    the Java code to refactor
     * @param strategy      the type of refactoring to suggest
     * @return AIResponse with the refactoring plan and/or refactored code
     */
    public AIResponse suggest(String sourceCode, RefactoringStrategy strategy) {
        String prompt = switch (strategy) {
            case DESIGN_PATTERN_SUGGESTION  -> buildDesignPatternPrompt(sourceCode);
            case MICROSERVICE_DECOMPOSITION -> buildMicroservicePrompt(sourceCode);
            case MODERNIZE_JAVA             -> buildModernizationPrompt(sourceCode);
            case SOLID_PRINCIPLES_REVIEW    -> buildSOLIDReviewPrompt(sourceCode);
            case EXTRACT_METHOD             -> buildExtractMethodPrompt(sourceCode);
            case EXTRACT_CLASS              -> buildExtractClassPrompt(sourceCode);
        };

        return engineManager.infer(AIRequest.builder()
                .prompt(prompt)
                .systemPrompt(PromptTemplates.REFACTORING_SYSTEM_PROMPT)
                .requestType(AIRequest.RequestType.REFACTOR)
                .maxNewTokens(800)
                .temperature(0.2f)
                .build());
    }

    private String buildDesignPatternPrompt(String code) {
        return """
            Analyze the following Java code and suggest the most applicable Gang of Four (GoF) design patterns.
            
            For each suggested pattern:
            1. State which pattern (Factory, Strategy, Observer, Builder, etc.)
            2. Explain WHY this pattern fits this code
            3. Show a concrete before/after refactoring example
            4. Note any trade-offs
            
            Focus on patterns that address current problems, not patterns for their own sake.
            
            Code to analyze:
            ```java
            """ + code + """
            ```
            """;
    }

    private String buildMicroservicePrompt(String code) {
        return """
            Analyze the following Java code (likely a monolith component) and suggest how to decompose it
            into microservice-ready structure following Domain-Driven Design principles.
            
            Provide:
            1. Identified bounded contexts and their responsibilities
            2. Suggested microservice boundaries (service names and responsibilities)
            3. Event-driven communication points (which interactions should become events)
            4. Shared data concerns and how to handle them
            5. API contracts between proposed services
            
            Code:
            ```java
            """ + code + """
            ```
            """;
    }

    private String buildModernizationPrompt(String code) {
        return """
            Modernize the following Java code to use Java 17+ idioms and best practices.
            
            Apply where appropriate:
            - Records instead of POJOs with only getters
            - Sealed classes and pattern matching
            - Switch expressions instead of switch statements
            - Text blocks for multi-line strings
            - var for local variables where type is obvious
            - Stream API and functional interfaces
            - Optional instead of null checks
            - instanceof pattern matching
            
            Show the modernized version with comments explaining each change.
            
            Original code:
            ```java
            """ + code + """
            ```
            """;
    }

    private String buildSOLIDReviewPrompt(String code) {
        return """
            Review the following Java code against SOLID principles.
            For each principle, state whether the code adheres or violates it, with specific evidence.
            
            S - Single Responsibility Principle
            O - Open/Closed Principle
            L - Liskov Substitution Principle
            I - Interface Segregation Principle
            D - Dependency Inversion Principle
            
            For each violation, suggest a concrete fix.
            
            Code:
            ```java
            """ + code + """
            ```
            """;
    }

    private String buildExtractMethodPrompt(String code) {
        return """
            Identify sections of the following Java method that should be extracted into separate methods.
            
            For each extraction:
            1. Identify the lines to extract
            2. Suggest a descriptive method name
            3. Show the extracted method signature
            4. Show how the original method changes
            
            Apply the "Single Level of Abstraction" principle: each method should operate at one level of abstraction.
            
            Code:
            ```java
            """ + code + """
            ```
            """;
    }

    private String buildExtractClassPrompt(String code) {
        return """
            Analyze the following class and identify responsibilities that should be extracted into separate classes.
            
            Apply the Single Responsibility Principle. For each extraction:
            1. Name the responsibility and the new class
            2. List the methods/fields to move
            3. Describe the relationship between the original and new class
            4. Show the new class structure
            
            Code:
            ```java
            """ + code + """
            ```
            """;
    }
}
