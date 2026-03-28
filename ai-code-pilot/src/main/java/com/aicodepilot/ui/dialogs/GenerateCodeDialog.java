package com.aicodepilot.ui.dialogs;

import com.aicodepilot.generator.CodeGenerator;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.layout.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Dialog for configuring code generation parameters.
 *
 * <p>Collects:
 * <ul>
 *   <li>Generation target type (Controller, Service, Entity, etc.)</li>
 *   <li>Entity/domain name</li>
 *   <li>Optional context (package, fields, base URL)</li>
 * </ul>
 */
public class GenerateCodeDialog extends Dialog {

    private Combo targetCombo;
    private Text entityNameText;
    private Text packageText;
    private Text fieldsText;
    private Text contextText;

    private CodeGenerator.GenerationTarget selectedTarget;
    private String entityName;
    private Map<String, String> additionalContext;

    private static final String[] TARGET_LABELS = {
        "Spring REST Controller",
        "Service Interface",
        "Service Implementation",
        "JPA Entity",
        "DTO Request",
        "DTO Response",
        "JPA Repository",
        "JUnit 5 Unit Tests",
        "Kafka Producer",
        "Kafka Consumer",
        "Dockerfile",
        "Kubernetes Deployment YAML"
    };

    public GenerateCodeDialog(Shell parent) {
        super(parent);
        setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.APPLICATION_MODAL);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("AI Code Pilot — Generate Code");
        shell.setMinimumSize(500, 400);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        area.setLayout(new GridLayout(2, false));

        // Generation target
        new Label(area, SWT.NONE).setText("Generate:");
        targetCombo = new Combo(area, SWT.READ_ONLY);
        targetCombo.setItems(TARGET_LABELS);
        targetCombo.select(0);
        targetCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Entity name
        new Label(area, SWT.NONE).setText("Entity / Class Name:");
        entityNameText = new Text(area, SWT.BORDER);
        entityNameText.setText("Product");
        entityNameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        entityNameText.setMessage("e.g. Product, Order, Customer");

        // Package
        new Label(area, SWT.NONE).setText("Base Package:");
        packageText = new Text(area, SWT.BORDER);
        packageText.setText("com.example");
        packageText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Fields (for Entity / DTO generation)
        new Label(area, SWT.NONE).setText("Fields (comma-separated):");
        fieldsText = new Text(area, SWT.BORDER);
        fieldsText.setMessage("e.g. String name, BigDecimal price, LocalDate createdAt");
        fieldsText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Additional context
        Label ctxLabel = new Label(area, SWT.NONE);
        ctxLabel.setText("Additional context:");
        ctxLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));

        contextText = new Text(area, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL);
        contextText.setMessage("Optional: describe requirements or constraints...");
        GridData ctxData = new GridData(SWT.FILL, SWT.FILL, true, true);
        ctxData.heightHint = 80;
        contextText.setLayoutData(ctxData);

        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, "⚡ Generate", true);
        createButton(parent, IDialogConstants.CANCEL_ID, "Cancel", false);
    }

    @Override
    protected void okPressed() {
        // Validate
        if (entityNameText.getText().isBlank()) {
            setErrorMessage("Entity name is required.");
            return;
        }

        // Extract values
        int idx = targetCombo.getSelectionIndex();
        selectedTarget = CodeGenerator.GenerationTarget.values()[idx];
        entityName     = entityNameText.getText().trim();

        additionalContext = new HashMap<>();
        additionalContext.put("package", packageText.getText().trim());
        additionalContext.put("fields",  fieldsText.getText().trim());
        additionalContext.put("context", contextText.getText().trim());

        super.okPressed();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void setErrorMessage(String msg) {
        // Simple inline error — in production use IInputValidator
        MessageBox mb = new MessageBox(getShell(), SWT.ICON_WARNING | SWT.OK);
        mb.setMessage(msg);
        mb.open();
    }

    // -----------------------------------------------------------------------
    // Result accessors
    // -----------------------------------------------------------------------

    public CodeGenerator.GenerationTarget getSelectedTarget() { return selectedTarget; }
    public String getEntityName()                             { return entityName; }
    public Map<String, String> getAdditionalContext()         { return additionalContext; }
}