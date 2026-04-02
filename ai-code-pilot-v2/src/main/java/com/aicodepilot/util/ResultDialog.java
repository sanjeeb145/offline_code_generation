package com.aicodepilot.util;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.custom.StyledText;

/**
 * A resizable dialog for displaying AI-generated results.
 *
 * <p>Features:
 * <ul>
 *   <li>Scrollable, read-only text area with monospace font</li>
 *   <li>"Copy to Clipboard" button for easy code extraction</li>
 *   <li>"Insert into Editor" button (future — via callback)</li>
 *   <li>Resizable to accommodate long AI responses</li>
 * </ul>
 */
public class ResultDialog extends Dialog {

    private final String title;
    private final String content;
    private StyledText textWidget;

    public ResultDialog(Shell parent, String title, String content) {
        super(parent);
        this.title   = title;
        this.content = content;
        setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX | SWT.APPLICATION_MODAL);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("AI Code Pilot — " + title);
        shell.setMinimumSize(700, 500);
    }

    @Override
    protected Point getInitialSize() {
        return new Point(850, 650);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        area.setLayout(new GridLayout(1, false));

        // Title label
        Label titleLabel = new Label(area, SWT.NONE);
        titleLabel.setText(title);
        titleLabel.setFont(JFaceResources.getBannerFont());
        titleLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Separator
        Label sep = new Label(area, SWT.HORIZONTAL | SWT.SEPARATOR);
        sep.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Scrollable text area
        //textWidget = new Text(area, SWT.MULTI | SWT.READ_ONLY | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
        textWidget = new StyledText(area,
                SWT.MULTI | SWT.READ_ONLY | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
        textWidget.setFont(JFaceResources.getTextFont()); // Monospace
        textWidget.setText(content != null ? content : "");
        textWidget.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        textWidget.setLineSpacing(2);

        // Stats bar
        Label statsLabel = new Label(area, SWT.NONE);
        int lineCount = content != null ? content.split("\n").length : 0;
        statsLabel.setText("Lines: " + lineCount + "  |  Chars: " + (content != null ? content.length() : 0));
        statsLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        // Copy to clipboard button
        createButton(parent, IDialogConstants.CLIENT_ID + 1, "Copy to Clipboard", false);
        // Select all
        createButton(parent, IDialogConstants.CLIENT_ID + 2, "Select All", false);
        // Close
        createButton(parent, IDialogConstants.OK_ID, "Close", true);
    }

    @Override
    protected void buttonPressed(int buttonId) {
        if (buttonId == IDialogConstants.CLIENT_ID + 1) {
            // Copy entire content to clipboard
            Clipboard clipboard = new Clipboard(getShell().getDisplay());
            clipboard.setContents(
                    new Object[]{ content },
                    new Transfer[]{ TextTransfer.getInstance() });
            clipboard.dispose();
        } else if (buttonId == IDialogConstants.CLIENT_ID + 2) {
            textWidget.selectAll();
        } else {
            super.buttonPressed(buttonId);
        }
    }
}
