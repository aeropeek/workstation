package org.janelia.workstation.gui.large_volume_viewer.action;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;

import org.janelia.workstation.common.gui.support.Icons;
import org.janelia.workstation.gui.large_volume_viewer.controller.MouseWheelModeListener;

public class TraceMouseModeAction extends AbstractAction {
	private static final long serialVersionUID = 1L;
    private MouseWheelModeListener mwmListener;
	
	public TraceMouseModeAction() {
		putValue(NAME, "Trace");
		putValue(SMALL_ICON, Icons.getIcon("nib.png"));
		String acc = "P";
		KeyStroke accelerator = KeyStroke.getKeyStroke(acc);
		putValue(ACCELERATOR_KEY, accelerator);
		putValue(SHORT_DESCRIPTION, 
				"<html>"
				+"Set mouse mode to trace neurons ["+acc+"]<br>"
				+"<br>"
				+"SHIFT-click to place a new anchor<br>" // done
				+"Click and drag to move anchor<br>" // done
				+"Click to designate parent anchor<br>" // done
				+"Middle-button drag to Pan XY<br>" // done
				+"Scroll wheel to scan Z<br>" // done
				+"SHIFT-scroll wheel to zoom<br>" // done
				+"Double-click to recenter on a point<br>"
				+"Right-click for context menu"
				+"</html>");
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
        mwmListener.setMode(MouseMode.Mode.TRACE);
		putValue(SELECTED_KEY, true);
	}

    /**
     * @param mwmListener the mwmListener to set
     */
    public void setMwmListener(MouseWheelModeListener mwmListener) {
        this.mwmListener = mwmListener;
    }

}
