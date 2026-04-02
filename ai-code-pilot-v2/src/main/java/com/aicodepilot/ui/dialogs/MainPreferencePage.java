package com.aicodepilot.ui.dialogs;

import com.aicodepilot.Activator;
import com.aicodepilot.util.PluginLogger;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.DirectoryFieldEditor;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

/**
 * Eclipse Preferences page for AI Code Pilot settings.
 *
 * <p>Accessible via: Window → Preferences → AI Code Pilot
 *
 * <p>Settings managed here:
 * <ul>
 *   <li>Model directory path</li>
 *   <li>Maximum token generation length</li>
 *   <li>Inference temperature</li>
 *   <li>Number of CPU threads for inference</li>
 *   <li>Feature toggles (RAG, auto-analyze, telemetry — always off)</li>
 * </ul>
 */
public class MainPreferencePage extends FieldEditorPreferencePage
        implements IWorkbenchPreferencePage {

    // -----------------------------------------------------------------------
    // Preference keys — referenced throughout the plugin
    // -----------------------------------------------------------------------

    public static final String KEY_MODEL_DIR        = "model.directory";
    public static final String KEY_MAX_TOKENS       = "inference.maxTokens";
    public static final String KEY_TEMPERATURE      = "inference.temperature";
    public static final String KEY_CPU_THREADS      = "inference.cpuThreads";
    public static final String KEY_RAG_ENABLED      = "rag.enabled";
    public static final String KEY_AUTO_ANALYZE     = "editor.autoAnalyze";
    public static final String KEY_ENGINE_TYPE      = "engine.preferredType";
    public static final String KEY_CONTEXT_SIZE     = "inference.contextSize";

    public MainPreferencePage() {
        super(GRID);
    }

    @Override
    public void init(IWorkbench workbench) {
        // Bind to plugin's preference store
        setPreferenceStore(Activator.getDefault().getPreferenceStore());
        setDescription("Configure AI Code Pilot settings.\n"
                + "All processing is 100% offline — no data leaves your machine.");
    }

    @Override
    protected void createFieldEditors() {
        // ---- Model Configuration ----
        addField(new DirectoryFieldEditor(KEY_MODEL_DIR,
                "Model Directory:", getFieldEditorParent()));

        addField(new ComboFieldEditor(KEY_ENGINE_TYPE,
                "Preferred Engine:",
                new String[][]{
                    {"Auto-detect (recommended)", "auto"},
                    {"llama.cpp (GGUF models)",   "llama"},
                    {"ONNX Runtime",               "onnx"},
                    {"DJL/PyTorch",                "djl"},
                    {"Rule-based (no model)",      "rules"}
                },
                getFieldEditorParent()));

        // ---- Inference Parameters ----
        IntegerFieldEditor maxTokensField = new IntegerFieldEditor(KEY_MAX_TOKENS,
                "Max Generated Tokens:", getFieldEditorParent());
        maxTokensField.setValidRange(64, 2048);
        addField(maxTokensField);

        IntegerFieldEditor contextField = new IntegerFieldEditor(KEY_CONTEXT_SIZE,
                "Context Window Size (tokens):", getFieldEditorParent());
        contextField.setValidRange(512, 4096);
        addField(contextField);

        IntegerFieldEditor threadsField = new IntegerFieldEditor(KEY_CPU_THREADS,
                "CPU Threads for Inference:", getFieldEditorParent());
        threadsField.setValidRange(1, Runtime.getRuntime().availableProcessors());
        addField(threadsField);

        addField(new StringFieldEditor(KEY_TEMPERATURE,
                "Temperature (0.0–1.0):", getFieldEditorParent()));

        // ---- Feature Toggles ----
        addField(new BooleanFieldEditor(KEY_RAG_ENABLED,
                "Enable RAG (project code context retrieval)",
                getFieldEditorParent()));

        addField(new BooleanFieldEditor(KEY_AUTO_ANALYZE,
                "Auto-analyze on file save",
                getFieldEditorParent()));
    }

    @Override
    protected void performDefaults() {
        super.performDefaults();
        // Set sensible defaults when user clicks "Restore Defaults"
        getPreferenceStore().setDefault(KEY_MAX_TOKENS,  512);
        getPreferenceStore().setDefault(KEY_TEMPERATURE, "0.2");
        getPreferenceStore().setDefault(KEY_CPU_THREADS,
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        getPreferenceStore().setDefault(KEY_CONTEXT_SIZE, 2048);
        getPreferenceStore().setDefault(KEY_RAG_ENABLED,  true);
        getPreferenceStore().setDefault(KEY_AUTO_ANALYZE, false);
        getPreferenceStore().setDefault(KEY_ENGINE_TYPE,  "auto");
    }

    @Override
    public boolean performOk() {
        boolean result = super.performOk();
        if (result) {
            PluginLogger.info("AI Code Pilot preferences saved.");
            // Notify engine of config change (it may need to reload)
            // This would trigger a reload in a production implementation
        }
        return result;
    }
}
