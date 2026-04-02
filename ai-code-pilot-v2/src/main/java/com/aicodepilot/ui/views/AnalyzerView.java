package com.aicodepilot.ui.views;

import com.aicodepilot.Activator;
import com.aicodepilot.analyzer.AnalysisResult;
import com.aicodepilot.analyzer.JavaCodeAnalyzer;
import com.aicodepilot.util.EditorUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.part.ViewPart;

/**
 * Dedicated Eclipse view for deep code analysis.
 * Focuses on structural analysis, role detection, and metrics.
 */
public class AnalyzerView extends ViewPart {

    public static final String VIEW_ID = "com.aicodepilot.views.AnalyzerView";

    private StyledText inputArea;
    private StyledText outputArea;
    private Label statusLabel;
    private Button analyzeButton;

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        // --- Input group ---
        Group inputGroup = new Group(parent, SWT.NONE);
        inputGroup.setText("Java Code to Analyze");
        inputGroup.setLayout(new GridLayout(1, false));
        inputGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        inputArea = new StyledText(inputGroup,
                SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
        inputArea.setFont(JFaceResources.getTextFont());
        inputArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        inputArea.setText("// Paste Java code here for deep structural analysis...\n");

        // --- Button row ---
        Composite buttons = new Composite(parent, SWT.NONE);
        buttons.setLayout(new GridLayout(3, false));
        buttons.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        analyzeButton = new Button(buttons, SWT.PUSH);
        analyzeButton.setText("  Analyze Code  ");
        analyzeButton.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) { runAnalysis(); }
        });

        Button loadBtn = new Button(buttons, SWT.PUSH);
        loadBtn.setText("Load from Editor");
        loadBtn.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) { loadFromEditor(); }
        });

        Button clearBtn = new Button(buttons, SWT.PUSH);
        clearBtn.setText("Clear");
        clearBtn.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                inputArea.setText("");
                outputArea.setText("");
            }
        });

        // --- Output group ---
        Group outputGroup = new Group(parent, SWT.NONE);
        outputGroup.setText("Analysis Results");
        outputGroup.setLayout(new GridLayout(1, false));
        outputGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        outputArea = new StyledText(outputGroup,
                SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.READ_ONLY);
        outputArea.setFont(JFaceResources.getTextFont());
        outputArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        outputArea.setText("Results will appear here after analysis...");

        // --- Status bar ---
        statusLabel = new Label(parent, SWT.BORDER);
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        statusLabel.setText("Ready");
    }

    private void runAnalysis() {
        String code = inputArea.getText().trim();
        if (code.isBlank() || code.startsWith("//")) {
            outputArea.setText("Please paste valid Java code above.");
            return;
        }
        if (!Activator.getDefault().isEngineReady()) {
            outputArea.setText("AI engine is still loading — static analysis only.");
        }

        analyzeButton.setEnabled(false);
        statusLabel.setText("Analyzing...");

        Job job = Job.create("AI Analyzer", (IProgressMonitor m) -> {
            JavaCodeAnalyzer analyzer = new JavaCodeAnalyzer(
                    Activator.getDefault().getAIEngineManager());
            AnalysisResult result = analyzer.analyze(code);
            String report = result.toFormattedSummary();

            Display.getDefault().asyncExec(() -> {
                if (!outputArea.isDisposed()) {
                    outputArea.setText(report);
                    statusLabel.setText("Analysis complete — "
                            + result.getStaticFindings().size() + " static findings.");
                    analyzeButton.setEnabled(true);
                }
            });
            return Status.OK_STATUS;
        });
        job.schedule();
    }

    private void loadFromEditor() {
        // Pull text from active editor via IEditorPart
        try {
            var editor = getSite().getPage().getActiveEditor();
            if (editor != null) {
                var textEditor = editor.getAdapter(
                        org.eclipse.ui.texteditor.ITextEditor.class);
                if (textEditor != null) {
                    var doc = textEditor.getDocumentProvider()
                            .getDocument(textEditor.getEditorInput());
                    if (doc != null) {
                        inputArea.setText(doc.get());
                        statusLabel.setText("Loaded from: "
                                + editor.getTitle());
                    }
                }
            }
        } catch (Exception ex) {
            statusLabel.setText("Could not load from editor: " + ex.getMessage());
        }
    }

    @Override public void setFocus() { inputArea.setFocus(); }
}
