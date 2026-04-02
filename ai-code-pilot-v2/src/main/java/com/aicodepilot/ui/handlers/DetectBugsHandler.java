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
 * Runs AI-powered bug detection on the selected code or entire open file.
 *
 * <p>Triggered from: AI Code Pilot → Detect Bugs
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

        bugJob.setPriority(Job.INTERACTIVE);
        bugJob.schedule();
        return null;
    }
}
