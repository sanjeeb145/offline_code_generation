package com.aicodepilot.ui.views;

import com.aicodepilot.Activator;
import com.aicodepilot.analyzer.AnalysisResult;
import com.aicodepilot.analyzer.JavaCodeAnalyzer;
import com.aicodepilot.debug.DebugAssistant;
import com.aicodepilot.generator.CodeGenerator;
import com.aicodepilot.model.AIResponse;
import com.aicodepilot.refactor.RefactoringAssistant;
import com.aicodepilot.util.PluginLogger;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.part.ViewPart;

import java.util.Map;

/**
 * Primary Eclipse view for AI code assistance.
 *
 * <p>Layout (top-to-bottom):
 * <pre>
 *  ┌──────────────────────────────────────────────────┐
 *  │ [TabFolder]                                      │
 *  │  [Analyze] [Generate] [Debug] [Refactor]         │
 *  ├──────────────────────────────────────────────────┤
 *  │ [Code Input Area]                                │
 *  │   (editable text — paste code or auto-populated) │
 *  ├──────────────────────────────────────────────────┤
 *  │ [Action Buttons Row]                             │
 *  ├──────────────────────────────────────────────────┤
 *  │ [Results / Suggestions Panel]                    │
 *  │   (read-only, formatted output from AI)          │
 *  ├──────────────────────────────────────────────────┤
 *  │ Status bar: engine info, latency, cache          │
 *  └──────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>All AI operations run in Eclipse background Jobs.
 * The UI thread is never blocked — results are dispatched via
 * {@code Display.asyncExec()}.
 */
public class SuggestionsView extends ViewPart {

    public static final String VIEW_ID = "com.aicodepilot.views.SuggestionsView";

    // -----------------------------------------------------------------------
    // SWT widgets
    // -----------------------------------------------------------------------

    private TabFolder tabFolder;
    private StyledText codeInputArea;
    private StyledText resultsArea;
    private Label statusLabel;
    private ProgressBar progressBar;
    private Button runButton;
    private Combo generationTargetCombo;

    // -----------------------------------------------------------------------
    // Services
    // -----------------------------------------------------------------------

    private JavaCodeAnalyzer codeAnalyzer;
    private CodeGenerator codeGenerator;
    private DebugAssistant debugAssistant;
    private RefactoringAssistant refactoringAssistant;

    // Current active tab index
    private int activeTab = 0;

    // -----------------------------------------------------------------------
    // ViewPart lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void createPartControl(Composite parent) {
        // Initialize services (engine may still be loading)
        initializeServices();

        // Set up main layout
        parent.setLayout(new GridLayout(1, false));

        createTabFolder(parent);
        createCodeInputArea(parent);
        createButtonRow(parent);
        createResultsArea(parent);
        createStatusBar(parent);

        // Wire up toolbar actions
        configureToolbar();

        // Listen for editor selection changes to auto-populate code input
        getSite().getPage().addSelectionListener(this::onEditorSelectionChanged);

        // Update status based on engine state
        updateEngineStatus();
    }

    // -----------------------------------------------------------------------
    // UI construction
    // -----------------------------------------------------------------------

    private void createTabFolder(Composite parent) {
        tabFolder = new TabFolder(parent, SWT.TOP);
        tabFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        String[] tabs = {"🧠 Analyze", "⚡ Generate", "🐛 Debug", "🔄 Refactor"};
        for (String tabName : tabs) {
            TabItem item = new TabItem(tabFolder, SWT.NONE);
            item.setText(tabName);
        }

        tabFolder.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                activeTab = tabFolder.getSelectionIndex();
                updateUIForTab(activeTab);
            }
        });
    }

    private void createCodeInputArea(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("Java Code Input");
        group.setLayout(new GridLayout(1, false));
        group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // Toolbar above code area
        Composite toolbar = new Composite(group, SWT.NONE);
        toolbar.setLayout(new RowLayout(SWT.HORIZONTAL));

        Button clearBtn = new Button(toolbar, SWT.PUSH);
        clearBtn.setText("Clear");
        clearBtn.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                codeInputArea.setText("");
            }
        });

        Button fromEditorBtn = new Button(toolbar, SWT.PUSH);
        fromEditorBtn.setText("📋 From Active Editor");
        fromEditorBtn.setToolTipText("Load code from the active Java editor");
        fromEditorBtn.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                loadFromActiveEditor();
            }
        });

        // Code editor with syntax highlighting (basic)
        codeInputArea = new StyledText(group,
                SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
        codeInputArea.setFont(JFaceResources.getTextFont()); // Monospace font
        codeInputArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        codeInputArea.setLineSpacing(2);
        codeInputArea.setText("// Paste Java code here, or click 'From Active Editor'");
    }

    private void createButtonRow(Composite parent) {
        Composite buttonRow = new Composite(parent, SWT.NONE);
        buttonRow.setLayout(new RowLayout(SWT.HORIZONTAL));
        buttonRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Generation target combo (visible only on Generate tab)
        Label targetLabel = new Label(buttonRow, SWT.NONE);
        targetLabel.setText("Generate:");

        generationTargetCombo = new Combo(buttonRow, SWT.READ_ONLY);
        generationTargetCombo.setItems(
                "Controller", "Service Interface", "Service Impl",
                "JPA Entity", "DTO Request", "DTO Response",
                "Repository", "Unit Tests", "Kafka Producer",
                "Kafka Consumer", "Dockerfile", "K8s Deployment");
        generationTargetCombo.select(0);

        Label entityLabel = new Label(buttonRow, SWT.NONE);
        entityLabel.setText("Entity:");

        Text entityNameInput = new Text(buttonRow, SWT.BORDER);
        entityNameInput.setText("Product");
        entityNameInput.setLayoutData(new RowData(120, SWT.DEFAULT));
        entityNameInput.setData("entityInput"); // Tag for lookup

        // Main Run button
        runButton = new Button(buttonRow, SWT.PUSH);
        runButton.setText("▶ Run AI Analysis");
        runButton.setLayoutData(new RowData(150, SWT.DEFAULT));
        runButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onRunButtonClicked(entityNameInput.getText());
            }
        });

        // Copy results button
        Button copyBtn = new Button(buttonRow, SWT.PUSH);
        copyBtn.setText("📋 Copy Results");
        copyBtn.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (!resultsArea.getText().isBlank()) {
                    resultsArea.selectAll();
                    resultsArea.copy();
                    setStatus("Results copied to clipboard.");
                }
            }
        });

        // Update combo visibility on tab change
        updateUIForTab(0);
    }

    private void createResultsArea(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("AI Suggestions & Results");
        group.setLayout(new GridLayout(1, false));
        group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        resultsArea = new StyledText(group,
                SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.READ_ONLY);
        resultsArea.setFont(JFaceResources.getTextFont());
        resultsArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        resultsArea.setLineSpacing(3);
        resultsArea.setText("AI suggestions will appear here...");
        resultsArea.setEditable(false);
    }

    private void createStatusBar(Composite parent) {
        Composite statusBar = new Composite(parent, SWT.NONE);
        statusBar.setLayout(new GridLayout(3, false));
        statusBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        statusLabel = new Label(statusBar, SWT.NONE);
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        statusLabel.setText("Initializing AI engine...");

        progressBar = new ProgressBar(statusBar, SWT.SMOOTH | SWT.INDETERMINATE);
        progressBar.setLayoutData(new GridData(120, 12));
        progressBar.setVisible(false);
    }

    // -----------------------------------------------------------------------
    // Business logic
    // -----------------------------------------------------------------------

    private void onRunButtonClicked(String entityName) {
        String code = codeInputArea.getText().trim();
        if (code.isBlank() || code.startsWith("//")) {
            MessageDialog.openWarning(getSite().getShell(), "No Code",
                    "Please paste Java code or load from the active editor.");
            return;
        }

        if (!Activator.getDefault().isEngineReady()) {
            MessageDialog.openInformation(getSite().getShell(), "Engine Loading",
                    "The AI engine is still loading. Please wait a moment and try again.");
            return;
        }

        switch (activeTab) {
            case 0 -> runAnalysis(code);
            case 1 -> runGeneration(entityName);
            case 2 -> runDebugDetection(code);
            case 3 -> runRefactoring(code);
        }
    }

    /** Runs code analysis in a background Job, updates UI on completion. */
    private void runAnalysis(String code) {
        setStatus("Analyzing code...");
        showProgress(true);
        runButton.setEnabled(false);

        Job job = Job.create("AI Code Analysis", (IProgressMonitor monitor) -> {
            monitor.beginTask("Analyzing...", IProgressMonitor.UNKNOWN);
            AnalysisResult result = codeAnalyzer.analyze(code);
            String output = result.toFormattedSummary();
            displayResult(output, "Analysis complete — " + result.getStaticFindings().size()
                    + " static findings.");
            monitor.done();
            return Status.OK_STATUS;
        });

        job.addJobChangeListener(new JobChangeAdapter(runButton));
        job.schedule();
    }

    private void runGeneration(String entityName) {
        if (entityName.isBlank()) {
            MessageDialog.openWarning(getSite().getShell(), "Entity Name Required",
                    "Please enter an entity name (e.g., 'Product', 'Order').");
            return;
        }

        int targetIndex = generationTargetCombo.getSelectionIndex();
        CodeGenerator.GenerationTarget target = CodeGenerator.GenerationTarget.values()[targetIndex];

        setStatus("Generating " + target.name().toLowerCase() + " for '" + entityName + "'...");
        showProgress(true);
        runButton.setEnabled(false);

        Job job = Job.create("AI Code Generation", monitor -> {
            AIResponse response = codeGenerator.generate(
                    target, entityName, Map.of("package", "com.example"));
            String output = response.isSuccess()
                    ? "// Generated by AI Code Pilot\n\n" + response.getText()
                    : "❌ Generation failed: " + response.getErrorMessage();
            displayResult(output, "Code generated in " + response.getLatencyMs() + "ms");
            return Status.OK_STATUS;
        });

        job.addJobChangeListener(new JobChangeAdapter(runButton));
        job.schedule();
    }

    private void runDebugDetection(String code) {
        setStatus("Scanning for bugs...");
        showProgress(true);
        runButton.setEnabled(false);

        Job job = Job.create("AI Bug Detection", monitor -> {
            DebugAssistant.DebugReport report = debugAssistant.detect(code);
            displayResult(report.toFormattedReport(),
                    "Bug scan complete — " + report.getBugs().size() + " issues found.");
            return Status.OK_STATUS;
        });

        job.addJobChangeListener(new JobChangeAdapter(runButton));
        job.schedule();
    }

    private void runRefactoring(String code) {
        setStatus("Analyzing refactoring opportunities...");
        showProgress(true);
        runButton.setEnabled(false);

        Job job = Job.create("AI Refactoring", monitor -> {
            AIResponse response = refactoringAssistant.suggest(
                    code, RefactoringAssistant.RefactoringStrategy.DESIGN_PATTERN_SUGGESTION);
            String output = response.isSuccess()
                    ? response.getText()
                    : "❌ Refactoring analysis failed: " + response.getErrorMessage();
            displayResult(output, "Refactoring analysis complete.");
            return Status.OK_STATUS;
        });

        job.addJobChangeListener(new JobChangeAdapter(runButton));
        job.schedule();
    }

    // -----------------------------------------------------------------------
    // UI helpers
    // -----------------------------------------------------------------------

    /**
     * Updates UI asynchronously — safe to call from background threads.
     */
    private void displayResult(String text, String statusMsg) {
        Display.getDefault().asyncExec(() -> {
            if (resultsArea.isDisposed()) return;
            resultsArea.setText(text);
            setStatus(statusMsg);
            showProgress(false);
        });
    }
    
    public void displayResult(AnalysisResult result) {
    displayResult(result.toFormattedSummary(), "Analysis complete — ...");
}
    

    private void setStatus(String message) {
        Display.getDefault().asyncExec(() -> {
            if (!statusLabel.isDisposed()) statusLabel.setText(message);
        });
    }

    private void showProgress(boolean show) {
        Display.getDefault().asyncExec(() -> {
            if (!progressBar.isDisposed()) progressBar.setVisible(show);
        });
    }

    private void updateUIForTab(int tabIndex) {
        boolean isGenerateTab = (tabIndex == 1);
        generationTargetCombo.setVisible(isGenerateTab);
        runButton.setText(switch (tabIndex) {
            case 0 -> "▶ Analyze Code";
            case 1 -> "⚡ Generate Code";
            case 2 -> "🐛 Scan for Bugs";
            case 3 -> "🔄 Suggest Refactoring";
            default -> "▶ Run";
        });
    }

    private void loadFromActiveEditor() {
        IEditorPart editor = PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow().getActivePage().getActiveEditor();
        if (editor == null) {
            setStatus("No active editor.");
            return;
        }

        // Try to get selected text or full document from Java editor
        IEditorInput input = editor.getEditorInput();
        // Use ITextEditor adapter for accessing document
        var textEditor = editor.getAdapter(org.eclipse.ui.texteditor.ITextEditor.class);
        if (textEditor != null) {
            var selection = textEditor.getSelectionProvider().getSelection();
            if (selection instanceof org.eclipse.jface.text.ITextSelection ts
                    && !ts.getText().isBlank()) {
                codeInputArea.setText(ts.getText());
                setStatus("Loaded " + ts.getLength() + " chars from editor selection.");
                return;
            }
            // Load full document if no selection
            var doc = textEditor.getDocumentProvider()
                    .getDocument(input);
            if (doc != null) {
                codeInputArea.setText(doc.get());
                setStatus("Loaded full file from editor.");
            }
        }
    }

    private void onEditorSelectionChanged(IWorkbenchPart part,
                                          org.eclipse.jface.viewers.ISelection selection) {
        // Auto-populate on meaningful selections (>50 chars)
        if (selection instanceof org.eclipse.jface.text.ITextSelection ts
                && ts.getLength() > 50) {
            Display.getDefault().asyncExec(() -> {
                if (!codeInputArea.isDisposed()) {
                    codeInputArea.setText(ts.getText());
                }
            });
        }
    }

    private void updateEngineStatus() {
        Job pollJob = Job.create("Engine Status Check", monitor -> {
            // Poll until engine is ready (max 30 seconds)
            for (int i = 0; i < 30; i++) {
                if (Activator.getDefault().isEngineReady()) {
                    String engineType = Activator.getDefault()
                            .getAIEngineManager().getEngineType();
                    setStatus("✅ Engine ready: " + engineType);
                    return Status.OK_STATUS;
                }
                try { Thread.sleep(1000); } catch (InterruptedException ignored) { break; }
            }
            setStatus("⚠️ AI engine not loaded. Install a model in ~/.aicodepilot/models/");
            return Status.OK_STATUS;
        });
        pollJob.setSystem(true);
        pollJob.schedule(500);
    }

    private void initializeServices() {
        var em = Activator.getDefault().getAIEngineManager();
        codeAnalyzer         = new JavaCodeAnalyzer(em);
        codeGenerator        = new CodeGenerator(em);
        debugAssistant       = new DebugAssistant(em);
        refactoringAssistant = new RefactoringAssistant(em);
    }

    private void configureToolbar() {
        IToolBarManager tbm = getViewSite().getActionBars().getToolBarManager();
        tbm.add(new Action("Refresh") {
            @Override
            public void run() { updateEngineStatus(); }
        });
        tbm.add(new Separator());
        tbm.add(new Action("Clear Results") {
            @Override
            public void run() { resultsArea.setText(""); }
        });
    }

    @Override
    public void setFocus() {
        codeInputArea.setFocus();
    }

    @Override
    public void dispose() {
        getSite().getPage().removeSelectionListener(this::onEditorSelectionChanged);
        super.dispose();
    }

    // -----------------------------------------------------------------------
    // Inner class: Job change listener to re-enable button
    // -----------------------------------------------------------------------

    private class JobChangeAdapter extends org.eclipse.core.runtime.jobs.JobChangeAdapter {
        private final Button button;
        JobChangeAdapter(Button b) { this.button = b; }

        @Override
        public void done(org.eclipse.core.runtime.jobs.IJobChangeEvent event) {
            Display.getDefault().asyncExec(() -> {
                if (!button.isDisposed()) {
                    button.setEnabled(true);
                    showProgress(false);
                }
            });
        }
    }
}
