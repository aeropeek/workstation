package org.janelia.it.workstation.browser.gui.options;

import static org.janelia.it.workstation.browser.gui.options.OptionConstants.ANNOTATION_TABLES_HEIGHT_PROPERTY;
import static org.janelia.it.workstation.browser.gui.options.OptionConstants.SANITIZE_FILENAMES_PROPERTY;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileNotFoundException;

import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.apache.commons.lang3.StringUtils;
import org.janelia.it.jacs.integration.FrameworkImplProvider;
import org.janelia.it.workstation.browser.gui.support.GroupedKeyValuePanel;
import org.janelia.it.workstation.browser.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class FilePathsPanel extends javax.swing.JPanel {

    private static final Logger log = LoggerFactory.getLogger(FilePathsPanel.class);

    private final FilePathsOptionsPanelController controller;

    private final GroupedKeyValuePanel mainPanel;

    private JTextField downloadsDirField;
    private JTextField concurrentDownloadsField;
    private JCheckBox sanitizeFilenamesCheckbox;

    DocumentListener listener = new DocumentListener() {
        @Override
        public void insertUpdate(DocumentEvent e) {
            controller.changed();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            controller.changed();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            controller.changed();
        }
    };
    
    FilePathsPanel(FilePathsOptionsPanelController controller) {
        this.controller = controller;
        initComponents();

        this.mainPanel = new GroupedKeyValuePanel();
        add(mainPanel, BorderLayout.CENTER);
    }

    /**
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setLayout(new java.awt.BorderLayout());
    }// </editor-fold>//GEN-END:initComponents

    void load() {

        log.info("Loading file path settings...");

        mainPanel.removeAll();

        // Downloads Dir
        
        downloadsDirField = new JTextField(40);
        downloadsDirField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                controller.changed();
            }
        });
        
        downloadsDirField.setText(FilePathsOptions.getInstance().getDownloadsDir());

        String chooseFileText = null;
        ImageIcon chooseFileIcon = null;
        try {
            chooseFileIcon = Utils.getClasspathImage("magnifier.png");
        } catch (FileNotFoundException e) {
            log.warn("Failed to load button icon", e);
            chooseFileText = "...";
        }

        JButton chooseFileButton = new JButton(chooseFileText, chooseFileIcon);
        chooseFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                File currentDir = new File(downloadsDirField.getText());
                if (! currentDir.exists()) {
                    currentDir = null;
                }
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                fileChooser.setCurrentDirectory(currentDir);
                int returnVal = fileChooser.showOpenDialog(FilePathsPanel.this);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    downloadsDirField.setText(fileChooser.getSelectedFile().getAbsolutePath());
                    controller.changed();
                }
            }
        });

        JPanel downloadDirPanel = new JPanel();
        downloadDirPanel.setLayout(new BoxLayout(downloadDirPanel, BoxLayout.X_AXIS));
        downloadDirPanel.add(downloadsDirField);
        downloadDirPanel.add(chooseFileButton);

        mainPanel.addItem("Downloads Dir", downloadDirPanel);

        // Concurrent downloads
        
        this.concurrentDownloadsField = new JTextField(10);
        concurrentDownloadsField.getDocument().addDocumentListener(listener);
        concurrentDownloadsField.setText(FilePathsOptions.getInstance().getNumConcurrentDownloads()+"");
        JPanel concurrentDownloadPanel = new JPanel();
        concurrentDownloadPanel.setLayout(new BoxLayout(concurrentDownloadPanel, BoxLayout.X_AXIS));
        concurrentDownloadPanel.add(concurrentDownloadsField);
        concurrentDownloadPanel.add(new JLabel(" (requires restart)"));
        
        mainPanel.addItem("Concurrent downloads", concurrentDownloadPanel);

        // Sanitize filenames for external use
        
        sanitizeFilenamesCheckbox = new JCheckBox();
        sanitizeFilenamesCheckbox.setText("Sanitize filenames for external use");
        sanitizeFilenamesCheckbox.addActionListener((e) -> {
            controller.changed();
        });
        sanitizeFilenamesCheckbox.setSelected(FilePathsOptions.getInstance().getSanitizeDownloads());

        mainPanel.addItem(sanitizeFilenamesCheckbox);
    }

    void store() {

        FilePathsOptions.getInstance().setDownloadsDir(downloadsDirField.getText().trim());
        
        String concurrentDownloadsStr = concurrentDownloadsField.getText();
        try {
            if (!StringUtils.isBlank(concurrentDownloadsStr)) {
                int numConcurrentDownloads = Integer.parseInt(concurrentDownloadsStr);
                FilePathsOptions.getInstance().setNumConcurrentDownloads(numConcurrentDownloads);
            }
        }
        catch (NumberFormatException e) {
            log.warn("Cannot parse num concurrent downloads as integer: {}", concurrentDownloadsStr, e);
        }
        
        FilePathsOptions.getInstance().setSanitizeDownloads(sanitizeFilenamesCheckbox.isSelected());
    }

    boolean valid() {
        try {
            if (!StringUtils.isBlank(concurrentDownloadsField.getText())) {
                Integer.parseInt(concurrentDownloadsField.getText());
            }
        }
        catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
