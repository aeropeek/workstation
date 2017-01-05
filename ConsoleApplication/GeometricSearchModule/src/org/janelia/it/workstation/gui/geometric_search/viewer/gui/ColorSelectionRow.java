package org.janelia.it.workstation.gui.geometric_search.viewer.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import org.janelia.it.workstation.browser.gui.support.MouseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by murphys on 8/28/2015.
 */
public class ColorSelectionRow extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(ColorSelectionRow.class);

    private static final int COLOR_STATUS_WIDTH = 20;
    private static final int COLOR_STATUS_HEIGHT = 20;
    private static final int MAX_NAME_CHARS = 10;
    private static final int ROW_WIDTH = 385;
    private static final int ROW_HEIGHT = 45;

    JCheckBox visibleCheckBox;
    JPanel groupManagementPanel;
    JPanel groupSelectionPanel;
    JLabel nameLabel;
    GroupSelectionButton allButton;
    GroupSelectionButton noneButton;
    GroupSelectionButton soloButton;
    ColorPanel colorStatusPanel;
    ColorSelectionPanel colorSelectionPanel;
    SyncedCallback colorSelectionCallback;
    ColorSelectionRow thisColorSelectionRow;
    ScrollableColorRowPanel parentRowPanel;


    public ColorSelectionRow(String name, ScrollableColorRowPanel parentRowPanel) {
        thisColorSelectionRow=this;
        this.parentRowPanel=parentRowPanel;

        try {

            setName(name);
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setPreferredSize(new Dimension(ROW_WIDTH, ROW_HEIGHT));
            setMaximumSize(new Dimension(ROW_WIDTH, ROW_HEIGHT));

            visibleCheckBox = new JCheckBox();
            visibleCheckBox.setSelected(true);
            visibleCheckBox.addMouseListener(getRightClickListener());

            groupManagementPanel = new JPanel();
            groupManagementPanel.setLayout(new BoxLayout(groupManagementPanel, BoxLayout.Y_AXIS));

            groupSelectionPanel = new JPanel();
            groupSelectionPanel.setLayout(new BoxLayout(groupSelectionPanel, BoxLayout.X_AXIS));

            String normalizedName = getNormalizedName(name);
            nameLabel = new JLabel(normalizedName, JLabel.LEFT);
            nameLabel.setFont(new Font("Arial", Font.BOLD, 10));
            JPanel nameBox = new JPanel();
            nameBox.setLayout(new BoxLayout(nameBox, BoxLayout.X_AXIS));
            nameBox.add(nameLabel);

            allButton = new GroupSelectionButton(GroupSelectionButton.A_TYPE);
            noneButton = new GroupSelectionButton(GroupSelectionButton.N_TYPE);
            soloButton = new GroupSelectionButton(GroupSelectionButton.S_TYPE);

            groupSelectionPanel.add(allButton);
            groupSelectionPanel.add(noneButton);
            groupSelectionPanel.add(soloButton);

            groupManagementPanel.add(nameBox);
            groupManagementPanel.add(groupSelectionPanel);

            colorStatusPanel = new ColorPanel(COLOR_STATUS_WIDTH, COLOR_STATUS_HEIGHT, new Color(0, 0, 0));
            colorSelectionPanel = new ColorSelectionPanel();
            colorSelectionPanel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    Point panelPoint = e.getPoint();
                    Point imgPoint = colorSelectionPanel.toImageContext(panelPoint);
                    Color selectedColor = colorSelectionPanel.getColorFromClickCoordinate(imgPoint);
                    if (colorSelectionCallback != null) {
                        colorSelectionCallback.performAction(selectedColor);
                    }
                }
            });

            add(visibleCheckBox);
            add(colorStatusPanel);
            add(groupManagementPanel);
            add(colorSelectionPanel);

        }
        catch (Exception ex) {
            ex.printStackTrace();
            logger.error(ex.toString());
        }

    }

    public JCheckBox getVisibleCheckBox() {
        return visibleCheckBox;
    }

    public void setColorStatus(final Color color) {
        colorStatusPanel.setColor(color);
    }

    public void setColorSelectionCallback(SyncedCallback callback) {
        this.colorSelectionCallback = callback;
    }

    public String getNormalizedName(String name) {
        int initialLength = name.length();
        if (initialLength > MAX_NAME_CHARS) {
            return name.substring(0, MAX_NAME_CHARS);
        } else if (initialLength < MAX_NAME_CHARS) {
            String pad = "";
            for (int i = 0; i < (MAX_NAME_CHARS - initialLength); i++) {
                pad += " ";
            }
            return name + pad;
        }
        return name;
    }

    private MouseListener getRightClickListener() {
        MouseListener rightClickMouseListener = new MouseHandler() {

            @Override
            protected void popupTriggered(MouseEvent e) {
                if (e.isConsumed()) {
                    return;
                }
                getRightClickPopupMenu().show(e.getComponent(), e.getX(), e.getY());
                e.consume();
            }

        };
        return rightClickMouseListener;
    }

    private JPopupMenu getRightClickPopupMenu() {

        JPopupMenu rightClickPopupMenu = new JPopupMenu();

        JMenuItem selectAllItem = new JMenuItem("Select All");
        JMenuItem selectNoneItem = new JMenuItem("Select None");

        selectAllItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                parentRowPanel.selectAllRows();
            }
        });

        selectNoneItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                parentRowPanel.deselectAllRows();
            }
        });

        rightClickPopupMenu.add(selectAllItem);
        rightClickPopupMenu.add(selectNoneItem);

        return rightClickPopupMenu;
    }

    public GroupSelectionButton getAllButton() {
        return allButton;
    }

    public GroupSelectionButton getNoneButton() {
        return noneButton;
    }

    public GroupSelectionButton getSoloButton() {
        return soloButton;
    }
}