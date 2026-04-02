package com.aicodepilot.util;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Utility methods for interacting with the Eclipse editor and UI framework.
 *
 * <p>Centralizes common operations:
 * <ul>
 *   <li>Extracting selected or full text from the active editor</li>
 *   <li>Inserting generated code at the cursor position</li>
 *   <li>Showing standardized dialogs (info, warning, error, result)</li>
 * </ul>
 *
 * <p>All methods that touch SWT widgets must be called from the UI thread,
 * except {@code getSelectedOrFullText()} which is thread-safe.
 */
public final class EditorUtils {

    private EditorUtils() {} // Utility class

    // -----------------------------------------------------------------------
    // Editor text extraction
    // -----------------------------------------------------------------------

    /**
     * Gets the selected text from the active editor.
     * If nothing is selected, returns the entire document content.
     *
     * @param event the current command execution event
     * @return the selected text, full document, or null if no editor is active
     */
    public static String getSelectedOrFullText(ExecutionEvent event) {
        IEditorPart editor = HandlerUtil.getActiveEditor(event);
        if (editor == null) return null;

        ITextEditor textEditor = editor.getAdapter(ITextEditor.class);
        if (textEditor == null) return null;

        var selection = textEditor.getSelectionProvider().getSelection();
        if (selection instanceof org.eclipse.jface.text.ITextSelection ts) {
            String selected = ts.getText();
            if (selected != null && !selected.isBlank()) {
                return selected;
            }
        }

        // No selection — return full document
        var doc = textEditor.getDocumentProvider()
                .getDocument(textEditor.getEditorInput());
        return doc != null ? doc.get() : null;
    }

    /**
     * Inserts the generated code at the current cursor position in the active editor.
     * Replaces selected text if there is an active selection.
     *
     * @param event         the command execution event
     * @param generatedCode the code text to insert
     */
    public static void insertCodeAtCursor(ExecutionEvent event, String generatedCode) {
        IEditorPart editor = HandlerUtil.getActiveEditor(event);
        if (editor == null) return;

        ITextEditor textEditor = editor.getAdapter(ITextEditor.class);
        if (textEditor == null) return;

        var selection = textEditor.getSelectionProvider().getSelection();
        if (selection instanceof org.eclipse.jface.text.ITextSelection ts) {
            var doc = textEditor.getDocumentProvider()
                    .getDocument(textEditor.getEditorInput());
            if (doc != null) {
                try {
                    // Replace selection or insert at cursor
                    doc.replace(ts.getOffset(), ts.getLength(), generatedCode);
                } catch (org.eclipse.jface.text.BadLocationException e) {
                    PluginLogger.error("Failed to insert code at cursor", e);
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Dialog helpers
    // -----------------------------------------------------------------------

    public static void showInfo(Shell shell, String title, String message) {
        MessageDialog.openInformation(shell, title, message);
    }

    public static void showWarning(Shell shell, String title, String message) {
        MessageDialog.openWarning(shell, title, message);
    }

    public static void showError(Shell shell, String title, String message) {
        MessageDialog.openError(shell, title, message);
    }

    /**
     * Shows a scrollable result dialog with the AI output.
     * Preferred over MessageDialog for multi-line, formatted AI responses.
     */
    public static void showResultDialog(Shell shell, String title, String content) {
        ResultDialog dialog = new ResultDialog(shell, title, content);
        dialog.open();
    }

    /**
     * Shows an input dialog and returns the entered value, or null if cancelled.
     */
    public static String promptInput(Shell shell, String title, String label, String defaultValue) {
        InputDialog dialog = new InputDialog(shell, title, label, defaultValue, null);
        if (dialog.open() == Window.OK) {
            return dialog.getValue();
        }
        return null;
    }
}
