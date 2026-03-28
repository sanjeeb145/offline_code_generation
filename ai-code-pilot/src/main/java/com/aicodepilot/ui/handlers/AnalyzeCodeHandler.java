package com.aicodepilot.ui.handlers;

import com.aicodepilot.Activator;
import com.aicodepilot.analyzer.AnalysisResult;
import com.aicodepilot.analyzer.JavaCodeAnalyzer;
import com.aicodepilot.ui.views.SuggestionsView;
import com.aicodepilot.util.EditorUtils;
import com.aicodepilot.util.PluginLogger;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Handles the "Analyze Code with AI" command.
 *
 * <p>Triggered from:
 * <ul>
 *   <li>Right-click context menu → AI Code Pilot → Analyze Code</li>
 *   <li>Keyboard shortcut: Ctrl+Shift+A (Cmd+Shift+A on macOS)</li>
 *   <li>Main menu: AI Pilot → Analyze Code</li>
 * </ul>
 *
 * <p>Reads selected text from the active Java editor (or entire file if no
 * selection), runs analysis in a background Job, and opens/updates the
 * AI Suggestions view with results.
 */
public class AnalyzeCodeHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        // Validate engine availability
        if (!Activator.getDefault().isEngineReady()) {
            EditorUtils.showInfo(HandlerUtil.getActiveShell(event),
                    "AI Engine Loading",
                    "The AI engine is still initializing. Please wait and try again.");
            return null;
        }

        // Get code from active editor
        String selectedCode = EditorUtils.getSelectedOrFullText(event);
        if (selectedCode == null || selectedCode.isBlank()) {
            EditorUtils.showWarning(HandlerUtil.getActiveShell(event),
                    "No Code Selected",
                    "Please select Java code in the editor or open a Java file.");
            return null;
        }

        // Open/show the suggestions view
        IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
        SuggestionsView view;
        try {
            view = (SuggestionsView) page.showView(SuggestionsView.VIEW_ID);
        } catch (PartInitException e) {
            PluginLogger.error("Cannot open Suggestions view", e);
            return null;
        }

        // Run analysis in background
        final String codeToAnalyze = selectedCode;
        Job analysisJob = Job.create("AI Code Analysis", (IProgressMonitor monitor) -> {
            monitor.beginTask("Analyzing code with AI...", IProgressMonitor.UNKNOWN);
            JavaCodeAnalyzer analyzer = new JavaCodeAnalyzer(
                    Activator.getDefault().getAIEngineManager());
            AnalysisResult result = analyzer.analyze(codeToAnalyze);

            // Update view on UI thread
            view.getSite().getShell().getDisplay().asyncExec(() ->
                    page.activate(view));

            PluginLogger.info("Analysis complete for " + result.getClassName());
            monitor.done();
            return Status.OK_STATUS;
        });

        analysisJob.setPriority(Job.INTERACTIVE);
        analysisJob.schedule();

        return null;
    }
}


// =============================================================================
// GenerateCodeHandler.java
// =============================================================================

package com.aicodepilot.ui.handlers;

import com.aicodepilot.Activator;
import com.aicodepilot.generator.CodeGenerator;
import com.aicodepilot.model.AIResponse;
import com.aicodepilot.ui.dialogs.GenerateCodeDialog;
import com.aicodepilot.util.EditorUtils;
import com.aicodepilot.util.PluginLogger;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.window.Window;
import org.eclipse.ui.handlers.HandlerUtil;

import java.util.Map;

/**
 * Opens the code generation dialog and generates boilerplate code.
 */
public class GenerateCodeHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        if (!Activator.getDefault().isEngineReady()) {
            EditorUtils.showInfo(HandlerUtil.getActiveShell(event),
                    "Engine Loading", "Please wait for the AI engine to initialize.");
            return null;
        }

        // Show generation dialog
        GenerateCodeDialog dialog = new GenerateCodeDialog(HandlerUtil.getActiveShell(event));
        if (dialog.open() != Window.OK) return null;

        String entityName = dialog.getEntityName();
        CodeGenerator.GenerationTarget target = dialog.getSelectedTarget();
        Map<String, String> context = dialog.getAdditionalContext();

        Job genJob = Job.create("AI Code Generation", monitor -> {
            CodeGenerator generator = new CodeGenerator(
                    Activator.getDefault().getAIEngineManager());
            AIResponse response = generator.generate(target, entityName, context);

            HandlerUtil.getActiveShell(event).getDisplay().asyncExec(() -> {
                if (response.isSuccess()) {
                    // Insert generated code into editor at cursor position, or show in dialog
                    EditorUtils.insertCodeAtCursor(event, response.getText());
                } else {
                    EditorUtils.showError(HandlerUtil.getActiveShell(event),
                            "Generation Failed", response.getErrorMessage());
                }
            });
            return org.eclipse.core.runtime.Status.OK_STATUS;
        });

        genJob.setPriority(Job.INTERACTIVE);
        genJob.schedule();
        return null;
    }
}


// =============================================================================
// DetectBugsHandler.java
// =============================================================================

package com.aicodepilot.ui.handlers;

import com.aicodepilot.Activator;
import com.aicodepilot.debug.DebugAssistant;
import com.aicodepilot.util.EditorUtils;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Runs bug detection on selected or full Java file.
 */
public class DetectBugsHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        String code = EditorUtils.getSelectedOrFullText(event);
        if (code == null || code.isBlank()) return null;

        Job bugJob = Job.create("AI Bug Detection", monitor -> {
            DebugAssistant assistant = new DebugAssistant(
                    Activator.getDefault().getAIEngineManager());
            DebugAssistant.DebugReport report = assistant.detect(code);

            HandlerUtil.getActiveShell(event).getDisplay().asyncExec(() ->
                EditorUtils.showResultDialog(
                        HandlerUtil.getActiveShell(event),
                        "Bug Detection Results — " + report.getBugs().size() + " issues",
                        report.toFormattedReport()));

            return org.eclipse.core.runtime.Status.OK_STATUS;
        });
        bugJob.schedule();
        return null;
    }
}


// =============================================================================
// RefactorCodeHandler.java
// =============================================================================

package com.aicodepilot.ui.handlers;

import com.aicodepilot.Activator;
import com.aicodepilot.model.AIResponse;
import com.aicodepilot.refactor.RefactoringAssistant;
import com.aicodepilot.util.EditorUtils;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Suggests refactoring improvements for selected code.
 */
public class RefactorCodeHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        String code = EditorUtils.getSelectedOrFullText(event);
        if (code == null || code.isBlank()) return null;

        Job job = Job.create("AI Refactoring Suggestions", monitor -> {
            RefactoringAssistant assistant = new RefactoringAssistant(
                    Activator.getDefault().getAIEngineManager());
            AIResponse response = assistant.suggest(code,
                    RefactoringAssistant.RefactoringStrategy.DESIGN_PATTERN_SUGGESTION);

            HandlerUtil.getActiveShell(event).getDisplay().asyncExec(() ->
                EditorUtils.showResultDialog(
                        HandlerUtil.getActiveShell(event),
                        "Refactoring Suggestions",
                        response.isSuccess() ? response.getText() : response.getErrorMessage()));

            return org.eclipse.core.runtime.Status.OK_STATUS;
        });
        job.schedule();
        return null;
    }
}


// =============================================================================
// DevOpsHandler.java
// =============================================================================

package com.aicodepilot.ui.handlers;

import com.aicodepilot.Activator;
import com.aicodepilot.generator.CodeGenerator;
import com.aicodepilot.model.AIResponse;
import com.aicodepilot.util.EditorUtils;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.handlers.HandlerUtil;

import java.util.Map;

/**
 * Generates DevOps artifacts: Dockerfile, Kubernetes YAML, Kafka configs.
 */
public class DevOpsHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        // Prompt user for app name and target type via simple input dialog
        String appName = EditorUtils.promptInput(
                HandlerUtil.getActiveShell(event),
                "DevOps Generation",
                "Enter application name:", "my-spring-app");
        if (appName == null || appName.isBlank()) return null;

        Job job = Job.create("AI DevOps Generation", monitor -> {
            CodeGenerator generator = new CodeGenerator(
                    Activator.getDefault().getAIEngineManager());

            Map<String, String> ctx = Map.of(
                    "appName", appName, "javaVersion", "17",
                    "namespace", "production", "replicas", "2");

            // Generate both Dockerfile and K8s manifest
            AIResponse dockerfile = generator.generate(
                    CodeGenerator.GenerationTarget.DOCKERFILE, appName, ctx);
            AIResponse k8sManifest = generator.generate(
                    CodeGenerator.GenerationTarget.K8S_DEPLOYMENT, appName, ctx);

            String combined = "# ===== Dockerfile =====\n\n"
                    + (dockerfile.isSuccess() ? dockerfile.getText() : dockerfile.getErrorMessage())
                    + "\n\n# ===== Kubernetes Manifests =====\n\n"
                    + (k8sManifest.isSuccess() ? k8sManifest.getText() : k8sManifest.getErrorMessage());

            HandlerUtil.getActiveShell(event).getDisplay().asyncExec(() ->
                EditorUtils.showResultDialog(
                        HandlerUtil.getActiveShell(event),
                        "Generated DevOps Artifacts for '" + appName + "'",
                        combined));

            return org.eclipse.core.runtime.Status.OK_STATUS;
        });
        job.schedule();
        return null;
    }
}


// =============================================================================
// ExplainCodeHandler.java
// =============================================================================

package com.aicodepilot.ui.handlers;

import com.aicodepilot.Activator;
import com.aicodepilot.engine.AIEngineManager;
import com.aicodepilot.model.AIRequest;
import com.aicodepilot.model.AIResponse;
import com.aicodepilot.util.EditorUtils;
import com.aicodepilot.util.PromptTemplates;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Explains selected Java code in plain English.
 */
public class ExplainCodeHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        String code = EditorUtils.getSelectedOrFullText(event);
        if (code == null || code.isBlank()) return null;

        Job job = Job.create("AI Code Explanation", monitor -> {
            AIEngineManager engine = Activator.getDefault().getAIEngineManager();
            AIResponse response = engine.infer(AIRequest.builder()
                    .prompt("Explain the following Java code in plain English. "
                            + "Describe what it does, how it works, and its key design decisions:\n\n"
                            + "```java\n" + code + "\n```")
                    .systemPrompt(PromptTemplates.EXPLAIN_SYSTEM_PROMPT)
                    .requestType(AIRequest.RequestType.EXPLAIN_CODE)
                    .maxNewTokens(500)
                    .temperature(0.3f)
                    .build());

            HandlerUtil.getActiveShell(event).getDisplay().asyncExec(() ->
                EditorUtils.showResultDialog(
                        HandlerUtil.getActiveShell(event),
                        "Code Explanation",
                        response.isSuccess() ? response.getText() : response.getErrorMessage()));

            return org.eclipse.core.runtime.Status.OK_STATUS;
        });
        job.schedule();
        return null;
    }
}
