package org.janelia.workstation.browser.gui.dialogs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;

import org.janelia.workstation.core.actions.Action;
import org.janelia.workstation.core.activity_logging.ActivityLogHelper;
import org.janelia.workstation.common.gui.dialogs.ModalDialog;
import org.janelia.workstation.core.keybind.KeyBindings;
import org.janelia.workstation.core.keybind.KeyboardShortcut;
import org.janelia.workstation.core.keybind.ShortcutTextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A dialog for setting a key binding to an action. THe dialog should be shown with showForAction.
 *
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class KeyBindDialog extends ModalDialog {

    private final static Logger log = LoggerFactory.getLogger(KeyBindDialog.class);
    
    private JTextArea conflictInfoArea;
    private ShortcutTextField shortcutField;
    private Action actionToBind;

    public KeyBindDialog() {

        setTitle("Enter Keyboard Shortcut");

        shortcutField = new ShortcutTextField() {
            protected void updateCurrentKeyStrokeInfo() {

                KeyboardShortcut keyboardShortcut = getKeyboardShortcut();
                if (keyboardShortcut == null) {
                    return;
                }

                Action conflictingAction = KeyBindings.getKeyBindings().getConflict(keyboardShortcut);
                if (conflictingAction != null && !conflictingAction.equals(actionToBind)) {
                    conflictInfoArea.setForeground(Color.red);
                    conflictInfoArea.setText("Will replace key binding for '" + conflictingAction.getName() + "'");
                }
                else {
                    conflictInfoArea.setForeground(Color.black);
                    conflictInfoArea.setText("No conflicts");
                }
            }
        };
        shortcutField.setColumns(10);

        JPanel inputPane = new JPanel(new BorderLayout());
        inputPane.add(shortcutField, BorderLayout.CENTER);
        inputPane.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Key Stroke"), BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        add(inputPane, BorderLayout.NORTH);

        conflictInfoArea = new JTextArea(5, 20);
        conflictInfoArea.setEditable(false);
        conflictInfoArea.setLineWrap(true);
        conflictInfoArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(conflictInfoArea);

        JPanel conflictPane = new JPanel(new BorderLayout());
        conflictPane.add(scrollPane, BorderLayout.CENTER);
        conflictPane.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Conflicts"), BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        add(conflictPane, BorderLayout.CENTER);

        JButton okButton = new JButton("OK");
        okButton.setToolTipText("Close and save changes");
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (actionToBind == null) {
                    throw new IllegalStateException("No action to bind");
                }

                KeyboardShortcut keyboardShortcut = getKeyboardShortcut();
                KeyBindings.getKeyBindings().setBinding(keyboardShortcut, actionToBind);
                log.info("Assigned key bind {} to action {}", keyboardShortcut, actionToBind.getName());
                setVisible(false);
            }
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.setToolTipText("Close without saving changes");
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
            }
        });

        JPanel buttonPane = new JPanel();
        buttonPane.setLayout(new BoxLayout(buttonPane, BoxLayout.LINE_AXIS));
        buttonPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        buttonPane.add(Box.createHorizontalGlue());
        buttonPane.add(okButton);
        buttonPane.add(Box.createRigidArea(new Dimension(10, 0)));
        buttonPane.add(cancelButton);
        add(buttonPane, BorderLayout.SOUTH);

        addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent ce) {
                shortcutField.requestFocusInWindow();
            }
        });
    }

    public void showForAction(Action action) {
        setActionToBind(action);
        ActivityLogHelper.logUserAction("KeyBindDialog.showForAction", action.getName());
        packAndShow();
    }

    public void setActionToBind(Action action) {
        this.actionToBind = action;
        KeyboardShortcut shortcut = KeyBindings.getKeyBindings().getBinding(action);
        if (shortcut != null) {
            shortcutField.setKeyStroke(shortcut.getFirstKeyStroke());
        }
        else {
            shortcutField.setKeyStroke(null);
        }
        conflictInfoArea.setText(null);
    }

    public KeyboardShortcut getKeyboardShortcut() {
        KeyStroke firstStroke = shortcutField.getKeyStroke();
        if (firstStroke == null) {
            return null;
        }
        return new KeyboardShortcut(firstStroke);
    }

}
