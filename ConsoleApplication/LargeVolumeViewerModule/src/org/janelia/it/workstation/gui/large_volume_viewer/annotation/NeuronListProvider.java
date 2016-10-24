package org.janelia.it.workstation.gui.large_volume_viewer.annotation;

import java.util.List;

import org.janelia.it.jacs.model.domain.tiledMicroscope.TmNeuronMetadata;

/**
 * used to pass a list of neurons between UI elements that don't
 * need to know about the details
 */
public interface NeuronListProvider {
    public List<TmNeuronMetadata> getNeuronList();
}
