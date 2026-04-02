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
 *
 * <p>Triggered from: AI Code Pilot → Generate DevOps Artifacts
 */
public class DevOpsHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        // Prompt user for the application name
        String appName = EditorUtils.promptInput(
                HandlerUtil.getActiveShell(event),
                "DevOps Generation",
                "Enter application name:", "my-spring-app");
        if (appName == null || appName.isBlank()) return null;

        Job job = Job.create("AI DevOps Generation", monitor -> {
            CodeGenerator generator = new CodeGenerator(
                    Activator.getDefault().getAIEngineManager());

            Map<String, String> ctx = Map.of(
                    "appName",    appName,
                    "javaVersion","21",
                    "namespace",  "production",
                    "replicas",   "2");

            AIResponse dockerfile  = generator.generate(
                    CodeGenerator.GenerationTarget.DOCKERFILE, appName, ctx);
            AIResponse k8sManifest = generator.generate(
                    CodeGenerator.GenerationTarget.K8S_DEPLOYMENT, appName, ctx);

            String combined =
                    "# ===== Dockerfile =====\n\n"
                    + (dockerfile.isSuccess()
                            ? dockerfile.getText()
                            : dockerfile.getErrorMessage())
                    + "\n\n# ===== Kubernetes Manifests =====\n\n"
                    + (k8sManifest.isSuccess()
                            ? k8sManifest.getText()
                            : k8sManifest.getErrorMessage());

            HandlerUtil.getActiveShell(event).getDisplay().asyncExec(() ->
                EditorUtils.showResultDialog(
                        HandlerUtil.getActiveShell(event),
                        "Generated DevOps Artifacts for '" + appName + "'",
                        combined));

            return org.eclipse.core.runtime.Status.OK_STATUS;
        });

        job.setPriority(Job.INTERACTIVE);
        job.schedule();
        return null;
    }
}
