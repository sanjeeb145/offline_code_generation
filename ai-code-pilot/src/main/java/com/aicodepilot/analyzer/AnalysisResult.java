package com.aicodepilot.analyzer;

import java.util.*;

/**
 * Mutable accumulator for all findings from a single code analysis run.
 *
 * <p>Populated by the structural visitors and AI inference.
 * Immutable view returned via {@link #snapshot()} after analysis completes.
 */
public class AnalysisResult {

    // -----------------------------------------------------------------------
    // State (built during analysis phases)
    // -----------------------------------------------------------------------

    private final String originalSource;
    private String className = "Unknown";
    private final List<String> inferredRoles   = new ArrayList<>();
    private final List<String> staticFindings  = new ArrayList<>(); // AST-based
    private final List<String> parseErrors     = new ArrayList<>();
    private final List<String> warnings        = new ArrayList<>();
    private final Map<String, String> metrics  = new LinkedHashMap<>();
    private String aiSuggestions               = "";
    private boolean structuralAnalysisComplete = false;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    public AnalysisResult(String originalSource) {
        this.originalSource = originalSource;
    }

    // -----------------------------------------------------------------------
    // Mutation methods (called by visitors and analyzers)
    // -----------------------------------------------------------------------

    public void addRole(String role)      { inferredRoles.add(role); }
    public void addFinding(String f)      { staticFindings.add(f); }
    public void addParseError(String e)   { parseErrors.add(e); }
    public void addWarning(String w)      { warnings.add(w); }
    public void setClassName(String n)    { this.className = n; }
    public void setAISuggestions(String s) { this.aiSuggestions = s; }
    public void setStructuralAnalysisComplete(boolean v) {
        this.structuralAnalysisComplete = v;
    }
    public void addMetric(String k, String v) { metrics.put(k, v); }

    // -----------------------------------------------------------------------
    // Read accessors
    // -----------------------------------------------------------------------

    public String getOriginalSource()     { return originalSource; }
    public String getClassName()          { return className; }
    public List<String> getInferredRoles()   { return Collections.unmodifiableList(inferredRoles); }
    public List<String> getStaticFindings()  { return Collections.unmodifiableList(staticFindings); }
    public List<String> getParseErrors()     { return Collections.unmodifiableList(parseErrors); }
    public List<String> getWarnings()        { return Collections.unmodifiableList(warnings); }
    public Map<String, String> getMetrics()  { return Collections.unmodifiableMap(metrics); }
    public String getAISuggestions()         { return aiSuggestions; }
    public boolean isStructuralAnalysisComplete() { return structuralAnalysisComplete; }

    public boolean hasIssues() {
        return !staticFindings.isEmpty() || !parseErrors.isEmpty();
    }

    /**
     * Returns a formatted summary suitable for display in the plugin UI.
     */
    public String toFormattedSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append(" AI Code Pilot — Analysis Report\n");
        sb.append("═══════════════════════════════════════\n\n");

        sb.append("📦 Class: ").append(className).append("\n");

        if (!inferredRoles.isEmpty()) {
            sb.append("🏷️  Role: ").append(String.join(", ", inferredRoles)).append("\n");
        }

        if (!metrics.isEmpty()) {
            sb.append("📊 Metrics: ");
            metrics.forEach((k, v) -> sb.append(k).append("=").append(v).append(" "));
            sb.append("\n");
        }
        sb.append("\n");

        if (!parseErrors.isEmpty()) {
            sb.append("❌ Parse Errors:\n");
            parseErrors.forEach(e -> sb.append("   • ").append(e).append("\n"));
            sb.append("\n");
        }

        if (!staticFindings.isEmpty()) {
            sb.append("⚠️  Static Analysis Findings (").append(staticFindings.size()).append("):\n");
            for (int i = 0; i < staticFindings.size(); i++) {
                sb.append("   ").append(i + 1).append(". ").append(staticFindings.get(i)).append("\n");
            }
            sb.append("\n");
        } else {
            sb.append("✅ No static issues found.\n\n");
        }

        if (!aiSuggestions.isBlank()) {
            sb.append("🧠 AI-Powered Analysis:\n");
            sb.append("───────────────────────\n");
            sb.append(aiSuggestions).append("\n");
        }

        if (!warnings.isEmpty()) {
            sb.append("\n⚠️  Warnings:\n");
            warnings.forEach(w -> sb.append("   • ").append(w).append("\n"));
        }

        return sb.toString();
    }

    /**
     * Returns an immutable snapshot copy (safe to pass across thread boundaries).
     */
    public AnalysisResult snapshot() {
        AnalysisResult copy = new AnalysisResult(this.originalSource);
        copy.className = this.className;
        copy.inferredRoles.addAll(this.inferredRoles);
        copy.staticFindings.addAll(this.staticFindings);
        copy.parseErrors.addAll(this.parseErrors);
        copy.warnings.addAll(this.warnings);
        copy.metrics.putAll(this.metrics);
        copy.aiSuggestions = this.aiSuggestions;
        copy.structuralAnalysisComplete = this.structuralAnalysisComplete;
        return copy;
    }
}
