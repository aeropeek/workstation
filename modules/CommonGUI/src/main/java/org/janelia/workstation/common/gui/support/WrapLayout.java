package org.janelia.workstation.common.gui.support;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FlowLayout subclass that fully supports wrapping of components.
 * Borrowed from http://tips4java.wordpress.com/2008/11/06/wrap-layout
 */
public class WrapLayout extends FlowLayout {

    private static final Logger log = LoggerFactory.getLogger(WrapLayout.class);
    
    private boolean useSiblingWidth;

    /**
     * Constructs a new <code>WrapLayout</code> with a left
     * alignment and a default 5-unit horizontal and vertical gap.
     */
    public WrapLayout() {
        this(true);
    }

    public WrapLayout(boolean useSiblingWidth) {
        super();
        this.useSiblingWidth = useSiblingWidth;
    }

    /**
     * Constructs a new <code>FlowLayout</code> with the specified
     * alignment and a default 5-unit horizontal and vertical gap.
     * The value of the alignment argument must be one of
     * <code>WrapLayout.LEFT</code>, <code>WrapLayout.CENTER</code>,
     * or <code>WrapLayout.RIGHT</code>.
     *
     * @param align the alignment value
     */
    public WrapLayout(boolean useSiblingWidth, int align) {
        super(align);
        this.useSiblingWidth = useSiblingWidth;
    }

    /**
     * Creates a new flow layout manager with the indicated alignment
     * and the indicated horizontal and vertical gaps.
     * <p/>
     * The value of the alignment argument must be one of
     * <code>WrapLayout</code>, <code>WrapLayout</code>,
     * or <code>WrapLayout</code>.
     *
     * @param align the alignment value
     * @param hgap the horizontal gap between components
     * @param vgap the vertical gap between components
     */
    public WrapLayout(boolean useSiblingWidth, int align, int hgap, int vgap) {
        super(align, hgap, vgap);
        this.useSiblingWidth = useSiblingWidth;
    }

    /**
     * Returns the preferred dimensions for this layout given the
     * <i>visible</i> components in the specified target container.
     *
     * @param target the component which needs to be laid out
     * @return the preferred dimensions to lay out the
     * subcomponents of the specified container
     */
    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    /**
     * Returns the minimum dimensions needed to layout the <i>visible</i>
     * components contained in the specified target container.
     *
     * @param target the component which needs to be laid out
     * @return the minimum dimensions to lay out the
     * subcomponents of the specified container
     */
    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= (getHgap() + 1);
        return minimum;
    }

    /**
     * Returns the minimum or preferred dimension needed to layout the target
     * container.
     *
     * @param target target to get layout size for
     * @param preferred should preferred size be calculated
     * @return the dimension to layout the target container
     */
    private Dimension layoutSize(Container target, boolean preferred) {

        synchronized (target.getTreeLock()) {

            int targetWidth = 0;
            if (useSiblingWidth) {
                // Use the max width of the other components
                for (Component c : target.getParent().getComponents()) {
                    if (c == target) {
                        continue;
                    }
                    if (!c.isVisible()) {
                        continue;
                    }
                    int cw = c.getPreferredSize().width;
                    if (cw > targetWidth) {
                        targetWidth = cw;
                    }
                    int cw2 = c.getSize().width;
                    if (cw2 > targetWidth) {
                        targetWidth = cw2;
                    }
                }
            }
            
            if (targetWidth == 0) {
                //  Each row must fit with the width allocated to the containter.
                //  When the container width = 0, the preferred width of the container
                //  has not yet been calculated so lets ask for the maximum.
                targetWidth = target.getParent().getSize().width;
                if (targetWidth == 0) {
                    log.debug("Warning, using max value for target width");
                    targetWidth = Integer.MAX_VALUE;
                }
            }

            int hgap = getHgap();
            int vgap = getVgap();
            Insets insets = target.getInsets();
            int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
            int maxWidth = targetWidth - horizontalInsetsAndGap;

            //  Fit components into the allowed width
            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            int nmembers = target.getComponentCount();

            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);

                if (m.isVisible()) {
                    Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();

                    //  Can't add the component to current row. Start a new row.
                    if (rowWidth + d.width > maxWidth) {
                        addRow(dim, rowWidth, rowHeight);
                        rowWidth = 0;
                        rowHeight = 0;
                    }

                    //  Add a horizontal gap for all components after the first
                    if (rowWidth != 0) {
                        rowWidth += hgap;
                    }

                    rowWidth += d.width;
                    rowHeight = Math.max(rowHeight, d.height);
                }
            }

            addRow(dim, rowWidth, rowHeight);

            dim.width += horizontalInsetsAndGap;
            dim.height += insets.top + insets.bottom + vgap * 2;

            //	When using a scroll pane or the DecoratedLookAndFeel we need to
            //  make sure the preferred size is less than the size of the
            //  target containter so shrinking the container size works
            //  correctly. Removing the horizontal gap is an easy way to do this.
            Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);

            if (scrollPane != null) {
                dim.width -= (hgap + 1);
            }

            return dim;
        }
    }

    /*
     *  A new row has been completed. Use the dimensions of this row
     *  to update the preferred size for the container.
     *
     *  @param dim update the width and height when appropriate
     *  @param rowWidth the width of the row to add
     *  @param rowHeight the height of the row to add
     */
    private void addRow(Dimension dim, int rowWidth, int rowHeight) {
        dim.width = Math.max(dim.width, rowWidth);

        if (dim.height > 0) {
            dim.height += getVgap();
        }

        dim.height += rowHeight;
    }
}
