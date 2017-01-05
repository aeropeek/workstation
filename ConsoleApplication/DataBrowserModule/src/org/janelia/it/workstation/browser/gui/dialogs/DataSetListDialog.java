package org.janelia.it.workstation.browser.gui.dialogs;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;

import org.janelia.it.jacs.model.domain.sample.DataSet;
import org.janelia.it.workstation.browser.ConsoleApp;
import org.janelia.it.workstation.browser.activity_logging.ActivityLogHelper;
import org.janelia.it.workstation.browser.api.ClientDomainUtils;
import org.janelia.it.workstation.browser.api.DomainMgr;
import org.janelia.it.workstation.browser.api.DomainModel;
import org.janelia.it.workstation.browser.gui.inspector.DomainInspectorPanel;
import org.janelia.it.workstation.browser.gui.support.Icons;
import org.janelia.it.workstation.browser.gui.table.DynamicColumn;
import org.janelia.it.workstation.browser.gui.table.DynamicRow;
import org.janelia.it.workstation.browser.gui.table.DynamicTable;
import org.janelia.it.workstation.browser.model.DomainModelViewConstants;
import org.janelia.it.workstation.browser.util.Utils;
import static org.janelia.it.workstation.browser.util.Utils.SUPPORT_NEURON_SEPARATION_PARTIAL_DELETION;
import org.janelia.it.workstation.browser.workers.SimpleWorker;

/**
 * Dialog for viewing and editing Data Sets. 
 *
 * @author <a href="mailto:schauderd@janelia.hhmi.org">David Schauder</a>
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class DataSetListDialog extends ModalDialog {

    private final JLabel loadingLabel;
    private final JPanel mainPanel;
    private final DynamicTable dynamicTable;
    private final DataSetDialog dataSetDialog;

    public DataSetListDialog() {
        setTitle("Data Sets");

        dataSetDialog = new DataSetDialog(this);

        loadingLabel = new JLabel();
        loadingLabel.setOpaque(false);
        loadingLabel.setIcon(Icons.getLoadingIcon());
        loadingLabel.setHorizontalAlignment(SwingConstants.CENTER);
        loadingLabel.setVerticalAlignment(SwingConstants.CENTER);

        mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(loadingLabel, BorderLayout.CENTER);

        add(mainPanel, BorderLayout.CENTER);

        dynamicTable = new DynamicTable(true, false) {
            @Override
            public Object getValue(Object userObject, DynamicColumn column) {
                DataSet dataSet = (DataSet) userObject;
                if (dataSet != null) {
                    if ((DomainModelViewConstants.DATASET_OWNER).equals(column.getName())) {
                        return dataSet.getOwnerName();
                    }
                    if (DomainModelViewConstants.DATASET_NAME.equals(column.getName())) {
                        return dataSet.getName();
                    }
                    else if ((DomainModelViewConstants.DATASET_PIPELINE_PROCESS).equals(column.getName())) {
                        List<String> processes = dataSet.getPipelineProcesses();
                        return processes == null || processes.isEmpty() ? null : dataSet.getPipelineProcesses().get(0);
                    }
                    else if ((DomainModelViewConstants.DATASET_SAMPLE_NAME).equals(column.getName())) {
                        return dataSet.getSampleNamePattern();
                    }
                    else if ((DomainModelViewConstants.DATASET_SAGE_SYNC).equals(column.getName())) {
                        return dataSet.isSageSync();
                    }
                    else if ((DomainModelViewConstants.DATASET_NEURON_SEPARATION).equals(column.getName())) {
                        return dataSet.isNeuronSeparationSupported();
                    }
                }
                return null;
            }

            @Override
            protected JPopupMenu createPopupMenu(MouseEvent e) {
                JPopupMenu menu = super.createPopupMenu(e);

                if (menu != null) {
                    JTable table = getTable();
                    ListSelectionModel lsm = table.getSelectionModel();
                    if (lsm.getMinSelectionIndex() != lsm.getMaxSelectionIndex()) {
                        return menu;
                    }

                    final DataSet dataSet = (DataSet) getRows().get(table.getSelectedRow()).getUserObject();

                    JMenuItem editItem = new JMenuItem("  View Details");
                    editItem.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            dataSetDialog.showForDataSet(dataSet);
                        }
                    });
                    menu.add(editItem);

                    JMenuItem permItem = new JMenuItem("  Change Permissions");
                    permItem.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            new DomainDetailsDialog().showForDomainObject(dataSet, DomainInspectorPanel.TAB_NAME_PERMISSIONS);
                        }
                    });
                    menu.add(permItem);

                    JMenuItem deleteItem = new JMenuItem("  Delete");
                    deleteItem.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {

                            int result = JOptionPane.showConfirmDialog(ConsoleApp.getMainFrame(), "Are you sure you want to delete data set '" + dataSet.getName()
                                            + "'? This will not delete the images associated with the data set.",
                                    "Delete Data Set", JOptionPane.OK_CANCEL_OPTION);
                            if (result != 0) {
                                return;
                            }

                            Utils.setWaitingCursor(DataSetListDialog.this);

                            SimpleWorker worker = new SimpleWorker() {

                                @Override
                                protected void doStuff() throws Exception {
                                    final DomainModel model = DomainMgr.getDomainMgr().getModel();
                                    model.remove(dataSet);
                                }

                                @Override
                                protected void hadSuccess() {
                                    Utils.setDefaultCursor(DataSetListDialog.this);
                                    loadDataSets();
                                }

                                @Override
                                protected void hadError(Throwable error) {
                                    ConsoleApp.handleException(error);
                                    Utils.setDefaultCursor(DataSetListDialog.this);
                                    loadDataSets();
                                }
                            };
                            worker.execute();
                        }
                    });
                    menu.add(deleteItem);
                    
                    if (!ClientDomainUtils.hasWriteAccess(dataSet)) {
                        permItem.setEnabled(false);
                        deleteItem.setEnabled(false);
                    }
                }

                return menu;
            }

            @Override
            protected void rowDoubleClicked(int row) {
                final DataSet dataSet = (DataSet) getRows().get(row).getUserObject();
                dataSetDialog.showForDataSet(dataSet);
            }

            @Override
            public Class<?> getColumnClass(int column) {
                DynamicColumn dc = getColumns().get(column);
                if (dc.getName().equals(DomainModelViewConstants.DATASET_SAGE_SYNC)  ||
                    dc.getName().equals(DomainModelViewConstants.DATASET_NEURON_SEPARATION)) {
                    return Boolean.class;
                }
                return super.getColumnClass(column);
            }

            @Override
            protected void valueChanged(final DynamicColumn dc, int row, Object data) {
                if (dc.getName().equals(DomainModelViewConstants.DATASET_SAGE_SYNC) ||
                    dc.getName().equals(DomainModelViewConstants.DATASET_NEURON_SEPARATION)) {
                    
                    final Boolean selected = data == null ? Boolean.FALSE : (Boolean) data;
                    DynamicRow dr = getRows().get(row);
                    final DataSet dataSet = (DataSet) dr.getUserObject();
                    
                    if (!ClientDomainUtils.hasWriteAccess(dataSet)) {
                        JOptionPane.showMessageDialog(ConsoleApp.getMainFrame(),
                                "You do not have permission to change this data set",
                                "Permission denied", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    
                    SimpleWorker worker = new SimpleWorker() {

                        @Override
                        protected void doStuff() throws Exception {
                            if (dc.getName().equals(DomainModelViewConstants.DATASET_SAGE_SYNC)) {
                                dataSet.setSageSync(selected);
                                DomainMgr.getDomainMgr().getModel().save(dataSet);
                            }
                            if (dc.getName().equals(DomainModelViewConstants.DATASET_NEURON_SEPARATION)) {
                                dataSet.setNeuronSeparationSupported(selected);
                                DomainMgr.getDomainMgr().getModel().save(dataSet);
                            }
                        }

                        @Override
                        protected void hadSuccess() {
                        }

                        @Override
                        protected void hadError(Throwable error) {
                            ConsoleApp.handleException(error);
                        }
                    };
                    worker.execute();
                }
            }
        };

        dynamicTable.addColumn(DomainModelViewConstants.DATASET_OWNER);
        dynamicTable.addColumn(DomainModelViewConstants.DATASET_NAME);
        dynamicTable.addColumn(DomainModelViewConstants.DATASET_PIPELINE_PROCESS);
        dynamicTable.addColumn(DomainModelViewConstants.DATASET_SAMPLE_NAME);
        dynamicTable.addColumn(DomainModelViewConstants.DATASET_SAGE_SYNC).setEditable(true);
        DynamicColumn neuSepCol = dynamicTable.addColumn(DomainModelViewConstants.DATASET_NEURON_SEPARATION);
        neuSepCol.setEditable(SUPPORT_NEURON_SEPARATION_PARTIAL_DELETION);

        JButton addButton = new JButton("Add new");
        addButton.setToolTipText("Add a new data set definition");
        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dataSetDialog.showForNewDataSet();
            }
        });

        JButton okButton = new JButton("OK");
        okButton.setToolTipText("Close this dialog");
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
            }
        });

        JPanel buttonPane = new JPanel();
        buttonPane.setLayout(new BoxLayout(buttonPane, BoxLayout.LINE_AXIS));
        buttonPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        buttonPane.add(addButton);
        buttonPane.add(Box.createHorizontalGlue());
        buttonPane.add(okButton);

        add(buttonPane, BorderLayout.SOUTH);
    }

    public void showDialog() {
        loadDataSets();

        Component mainFrame = ConsoleApp.getMainFrame();
        setPreferredSize(new Dimension((int) (mainFrame.getWidth() * 0.5), (int) (mainFrame.getHeight() * 0.4)));

        ActivityLogHelper.logUserAction("DataSetListDialog.showDialog");

        // Show dialog and wait
        packAndShow();
    }

    private void loadDataSets() {

        mainPanel.removeAll();
        mainPanel.add(loadingLabel, BorderLayout.CENTER);

        SimpleWorker worker = new SimpleWorker() {

            private List<DataSet> dataSetList = new ArrayList<>();

            @Override
            protected void doStuff() throws Exception {
                for (DataSet dataSet : DomainMgr.getDomainMgr().getModel().getDataSets()) {
                    dataSetList.add(dataSet);
                }
            }

            @Override
            protected void hadSuccess() {

                // Update the attribute table
                dynamicTable.removeAllRows();
                for (DataSet dataSet : dataSetList) {
                    dynamicTable.addRow(dataSet);
                }

                dynamicTable.updateTableModel();
                mainPanel.removeAll();
                mainPanel.add(dynamicTable, BorderLayout.CENTER);
                mainPanel.revalidate();
            }

            @Override
            protected void hadError(Throwable error) {
                ConsoleApp.handleException(error);
                mainPanel.removeAll();
                mainPanel.add(dynamicTable, BorderLayout.CENTER);
                mainPanel.revalidate();
            }
        };
        worker.execute();
    }
    
    public void refresh() {
        loadDataSets();
    }

    public void totalRefresh() {
        throw new UnsupportedOperationException();
    }
}