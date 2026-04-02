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
 * Explains selected Java code in plain English using the local AI engine.
 *
 * <p>Triggered from: AI Code Pilot → Explain Code
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

        job.setPriority(Job.INTERACTIVE);
        job.schedule();
        return null;
    }
}
