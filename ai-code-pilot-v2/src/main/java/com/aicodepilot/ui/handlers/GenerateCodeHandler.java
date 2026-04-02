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
 *
 * <p>Triggered from: AI Code Pilot → Generate Code Here
 */
public class GenerateCodeHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        if (!Activator.getDefault().isEngineReady()) {
            EditorUtils.showInfo(HandlerUtil.getActiveShell(event),
                    "Engine Loading", "Please wait for the AI engine to initialize.");
            return null;
        }

        // Show generation dialog to collect user input
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
