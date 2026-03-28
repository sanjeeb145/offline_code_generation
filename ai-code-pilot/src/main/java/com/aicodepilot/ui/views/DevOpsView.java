package com.aicodepilot.ui.views;

import com.aicodepilot.Activator;
import com.aicodepilot.generator.CodeGenerator;
import com.aicodepilot.model.AIResponse;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.part.ViewPart;

import java.util.Map;

/**
 * Eclipse view for generating DevOps artifacts:
 *   - Dockerfile (multi-stage, Spring Boot optimized)
 *   - Kubernetes Deployment + Service + HPA YAML
 *   - Kafka producer/consumer configuration
 *   - application.yml for Spring Boot profiles
 */
public class DevOpsView extends ViewPart {

    public static final String VIEW_ID = "com.aicodepilot.views.DevOpsView";

    private Text appNameText;
    private Text namespaceText;
    private Text replicasText;
    private Combo artifactCombo;
    private StyledText outputArea;
    private Label statusLabel;
    private Button generateButton;

    private static final String[] ARTIFACT_TYPES = {
        "Dockerfile (multi-stage Spring Boot)",
        "Kubernetes Deployment + Service + HPA",
        "Kafka Producer Configuration",
        "Kafka Consumer Configuration",
        "Spring Boot application.yml (prod profile)"
    };

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        // --- Config panel ---
        Group configGroup = new Group(parent, SWT.NONE);
        configGroup.setText("DevOps Configuration");
        configGroup.setLayout(new GridLayout(2, false));
        configGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        new Label(configGroup, SWT.NONE).setText("Application Name:");
        appNameText = new Text(configGroup, SWT.BORDER);
        appNameText.setText("my-spring-app");
        appNameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(configGroup, SWT.NONE).setText("K8s Namespace:");
        namespaceText = new Text(configGroup, SWT.BORDER);
        namespaceText.setText("production");
        namespaceText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(configGroup, SWT.NONE).setText("Replicas:");
        replicasText = new Text(configGroup, SWT.BORDER);
        replicasText.setText("2");
        replicasText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(configGroup, SWT.NONE).setText("Generate:");
        artifactCombo = new Combo(configGroup, SWT.READ_ONLY);
        artifactCombo.setItems(ARTIFACT_TYPES);
        artifactCombo.select(0);
        artifactCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // --- Buttons ---
        Composite btns = new Composite(parent, SWT.NONE);
        btns.setLayout(new RowLayout(SWT.HORIZONTAL));

        generateButton = new Button(btns, SWT.PUSH);
        generateButton.setText("  Generate  ");
        generateButton.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) { runGeneration(); }
        });

        Button copyBtn = new Button(btns, SWT.PUSH);
        copyBtn.setText("Copy to Clipboard");
        copyBtn.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                Clipboard cb = new Clipboard(Display.getDefault());
                cb.setContents(new Object[]{outputArea.getText()},
                               new Transfer[]{TextTransfer.getInstance()});
                cb.dispose();
                statusLabel.setText("Copied to clipboard!");
            }
        });

        // --- Output ---
        Group outGroup = new Group(parent, SWT.NONE);
        outGroup.setText("Generated Artifact");
        outGroup.setLayout(new GridLayout(1, false));
        outGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        outputArea = new StyledText(outGroup,
                SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.READ_ONLY);
        outputArea.setFont(JFaceResources.getTextFont());
        outputArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        outputArea.setText("Generated artifact will appear here...");

        statusLabel = new Label(parent, SWT.BORDER);
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        statusLabel.setText("Ready to generate DevOps artifacts");
    }

    private void runGeneration() {
        String appName = appNameText.getText().trim();
        if (appName.isBlank()) {
            statusLabel.setText("Application name is required.");
            return;
        }

        int idx = artifactCombo.getSelectionIndex();
        generateButton.setEnabled(false);
        statusLabel.setText("Generating...");

        Map<String, String> ctx = Map.of(
            "appName",     appName,
            "namespace",   namespaceText.getText().trim(),
            "replicas",    replicasText.getText().trim(),
            "javaVersion", "17"
        );

        Job job = Job.create("AI DevOps Generation", m -> {
            CodeGenerator gen = new CodeGenerator(
                    Activator.getDefault().getAIEngineManager());

            CodeGenerator.GenerationTarget target = switch (idx) {
                case 0 -> CodeGenerator.GenerationTarget.DOCKERFILE;
                case 1 -> CodeGenerator.GenerationTarget.K8S_DEPLOYMENT;
                case 2 -> CodeGenerator.GenerationTarget.KAFKA_PRODUCER;
                case 3 -> CodeGenerator.GenerationTarget.KAFKA_CONSUMER;
                default -> CodeGenerator.GenerationTarget.DOCKERFILE;
            };

            AIResponse response = gen.generate(target, appName, ctx);
            String text = response.isSuccess()
                    ? response.getText()
                    : "Error: " + response.getErrorMessage();

            Display.getDefault().asyncExec(() -> {
                if (!outputArea.isDisposed()) {
                    outputArea.setText(text);
                    statusLabel.setText("Done! Generated in "
                            + response.getLatencyMs() + "ms");
                    generateButton.setEnabled(true);
                }
            });
            return Status.OK_STATUS;
        });
        job.schedule();
    }

    @Override public void setFocus() { appNameText.setFocus(); }
}
