package com.aicodepilot.ui.dialogs;

import com.aicodepilot.Activator;
import org.eclipse.jface.preference.*;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

/**
 * Model-specific settings sub-page under AI Code Pilot preferences.
 * Window → Preferences → AI Code Pilot → Model Settings
 */
public class ModelPreferencePage extends FieldEditorPreferencePage
        implements IWorkbenchPreferencePage {

    public static final String KEY_MODEL_PATH   = "model.filePath";
    public static final String KEY_LLAMA_BINARY = "llama.binaryPath";
    public static final String KEY_NUM_THREADS  = "llama.numThreads";
    public static final String KEY_MAX_TOKENS   = "llama.maxTokens";
    public static final String KEY_TEMPERATURE  = "llama.temperature";
    public static final String KEY_CTX_SIZE     = "llama.ctxSize";

    public ModelPreferencePage() {
        super(GRID);
    }

    @Override
    public void init(IWorkbench workbench) {
        setPreferenceStore(Activator.getDefault().getPreferenceStore());
        setDescription("Configure local LLM model settings.\n"
            + "Point to your downloaded .gguf model file for best results.");
    }

    @Override
    protected void createFieldEditors() {
        addField(new FileFieldEditor(KEY_MODEL_PATH,
                "GGUF Model File (.gguf):",
                getFieldEditorParent()));

        addField(new FileFieldEditor(KEY_LLAMA_BINARY,
                "llama.cpp Binary (llama-cli):",
                getFieldEditorParent()));

        IntegerFieldEditor threads = new IntegerFieldEditor(KEY_NUM_THREADS,
                "CPU Threads:",
                getFieldEditorParent());
        threads.setValidRange(1, Runtime.getRuntime().availableProcessors());
        addField(threads);

        IntegerFieldEditor maxTokens = new IntegerFieldEditor(KEY_MAX_TOKENS,
                "Max New Tokens:",
                getFieldEditorParent());
        maxTokens.setValidRange(64, 2048);
        addField(maxTokens);

        IntegerFieldEditor ctx = new IntegerFieldEditor(KEY_CTX_SIZE,
                "Context Window (tokens):",
                getFieldEditorParent());
        ctx.setValidRange(512, 8192);
        addField(ctx);

        addField(new StringFieldEditor(KEY_TEMPERATURE,
                "Temperature (0.0 – 1.0):",
                getFieldEditorParent()));
    }

    @Override
    protected void performDefaults() {
        super.performDefaults();
        getPreferenceStore().setDefault(KEY_NUM_THREADS,
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        getPreferenceStore().setDefault(KEY_MAX_TOKENS, 512);
        getPreferenceStore().setDefault(KEY_CTX_SIZE, 2048);
        getPreferenceStore().setDefault(KEY_TEMPERATURE, "0.2");
    }
}
