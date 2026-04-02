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
 * Suggests AI-powered refactoring improvements for selected code.
 *
 * <p>Triggered from: AI Code Pilot → Suggest Refactoring
 */
public class RefactorCodeHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        String code = EditorUtils.getSelectedOrFullText(event);
        if (code == null || code.isBlank()) return null;

        Job job = Job.create("AI Refactoring Suggestions", monitor -> {
            RefactoringAssistant assistant = new RefactoringAssistant(
                    Activator.getDefault().getAIEngineManager());
            AIResponse response = assistant.suggest(
                    code,
                    RefactoringAssistant.RefactoringStrategy.DESIGN_PATTERN_SUGGESTION);

            HandlerUtil.getActiveShell(event).getDisplay().asyncExec(() ->
                EditorUtils.showResultDialog(
                        HandlerUtil.getActiveShell(event),
                        "Refactoring Suggestions",
                        response.isSuccess() ? response.getText() : response.getErrorMessage()));

            return org.eclipse.core.runtime.Status.OK_STATUS;
        });

        job.setPriority(Job.INTERACTIVE);
        job.schedule();
        return null;
    }
}
