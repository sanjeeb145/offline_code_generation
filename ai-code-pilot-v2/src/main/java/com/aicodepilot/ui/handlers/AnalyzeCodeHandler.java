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

        // Run analysis in background — never block the UI thread
        final String codeToAnalyze = selectedCode;
        Job analysisJob = Job.create("AI Code Analysis", (IProgressMonitor monitor) -> {
            monitor.beginTask("Analyzing code with AI...", IProgressMonitor.UNKNOWN);

            JavaCodeAnalyzer analyzer = new JavaCodeAnalyzer(
                    Activator.getDefault().getAIEngineManager());
            AnalysisResult result = analyzer.analyze(codeToAnalyze);

            // Push result to view on UI thread
            view.getSite().getShell().getDisplay().asyncExec(() -> {
                page.activate(view);
                view.displayResult(result);
            });

            PluginLogger.info("Analysis complete for " + result.getClassName());
            monitor.done();
            return Status.OK_STATUS;
        });

        analysisJob.setPriority(Job.INTERACTIVE);
        analysisJob.schedule();

        return null;
    }
}
