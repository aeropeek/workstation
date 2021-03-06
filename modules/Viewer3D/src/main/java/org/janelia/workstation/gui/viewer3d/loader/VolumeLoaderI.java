package org.janelia.workstation.gui.viewer3d.loader;

import org.janelia.workstation.gui.viewer3d.VolumeDataAcceptor;

/**
 * Created with IntelliJ IDEA.
 * User: fosterl
 * Date: 3/19/13
 * Time: 4:04 PM
 *
 * Implement this to make a class capable of pushing data into a volume data acceptor.
 */
public interface VolumeLoaderI {
    enum FileType {
        TIF, LSM, V3DMASK, V3DSIGNAL, MP4, H264, H265, UNKNOWN
    }

    void populateVolumeAcceptor(VolumeDataAcceptor dataAcceptor);
}
