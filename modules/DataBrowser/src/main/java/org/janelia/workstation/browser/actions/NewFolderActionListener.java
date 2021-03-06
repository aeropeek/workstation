package org.janelia.workstation.browser.actions;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.janelia.workstation.integration.util.FrameworkAccess;
import org.janelia.it.jacs.shared.utils.StringUtils;
import org.janelia.workstation.browser.gui.components.DomainExplorerTopComponent;
import org.janelia.workstation.core.api.DomainMgr;
import org.janelia.workstation.core.api.DomainModel;
import org.janelia.workstation.core.activity_logging.ActivityLogHelper;
import org.janelia.workstation.common.nodes.NodeUtils;
import org.janelia.workstation.common.nodes.TreeNodeNode;
import org.janelia.workstation.core.workers.SimpleWorker;
import org.janelia.model.domain.workspace.Node;
import org.janelia.model.domain.workspace.TreeNode;

public final class NewFolderActionListener implements ActionListener {

    private TreeNodeNode parentNode;

    public NewFolderActionListener() {
    }

    public NewFolderActionListener(TreeNodeNode parentNode) {
        this.parentNode = parentNode;
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        ActivityLogHelper.logUserAction("NewFolderActionListener.actionPerformed");

        final DomainExplorerTopComponent explorer = DomainExplorerTopComponent.getInstance();
        final DomainModel model = DomainMgr.getDomainMgr().getModel();

        if (parentNode==null) {
            // If there is no parent node specified, we'll just use the default workspace.
            parentNode = explorer.getWorkspaceNode();
        }
        
        if (parentNode==null) {
            JOptionPane.showMessageDialog(FrameworkAccess.getMainFrame(),
                    "Folders have not been loaded. Try refreshing the Explorer view.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final String name = (String) JOptionPane.showInputDialog(FrameworkAccess.getMainFrame(), "Folder Name:\n",
                "Create new folder", JOptionPane.PLAIN_MESSAGE, null, null, null);
        if (StringUtils.isEmpty(name)) {
            return;
        }

        // Save the set and select it in the explorer so that it opens
        SimpleWorker worker = new SimpleWorker() {

            private TreeNode folder;

            @Override
            protected void doStuff() throws Exception {
                folder = new TreeNode();
                folder.setName(name);
                folder = model.create(folder);
                Node parentFolder = parentNode.getNode();
                model.addChild(parentFolder, folder);
            }

            @Override
            protected void hadSuccess() {
                final Long[] idPath = NodeUtils.createIdPath(parentNode, folder);
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        explorer.selectAndNavigateNodeByPath(idPath);
                    }
                });
            }

            @Override
            protected void hadError(Throwable error) {
                FrameworkAccess.handleException(error);
            }
        };

        worker.execute();
    }
}
