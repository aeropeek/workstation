package org.janelia.horta.actions;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import org.janelia.horta.NeuronTracerTopComponent;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ActionID(
        category = "Horta",
        id = "org.janelia.horta.actions.LoadHortaTileAtFocusAction"
)
@ActionRegistration(
        iconBase = "org/janelia/horta/images/neuronTracerCubic16.png",
        displayName = "#CTL_LoadHortaTileAtFocusAction",
        key = "loadTileAtFocus"
)
@ActionReferences({
    @ActionReference(path = "Menu/View", position = 1300, separatorBefore = 1250),
    @ActionReference(path = "Shortcuts", name = "D-T")
})
@Messages("CTL_LoadHortaTileAtFocusAction=Load Horta Tile At Focus")

public final class LoadHortaTileAtFocusAction implements ActionListener {
    private final Logger LOG = LoggerFactory.getLogger(this.getClass());

    @Override
    public void actionPerformed(ActionEvent e) {
        LOG.info("Load KTX Central Tile Action invoked");
        NeuronTracerTopComponent nttc = NeuronTracerTopComponent.getInstance();
        if (nttc == null)
            return;
        try {
            nttc.setPreferKtx(true);
            nttc.loadPersistentTileAtFocus();
        } catch (IOException ex) {
            LOG.info("Tile load failed", ex);
        }
    }
}

