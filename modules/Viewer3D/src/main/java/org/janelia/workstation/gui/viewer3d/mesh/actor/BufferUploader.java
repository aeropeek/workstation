package org.janelia.workstation.gui.viewer3d.mesh.actor;

import javax.media.opengl.GL2GL3;

/**
 * Implement this to take on capability of pushing buffers to GPU.
 *
 * @author fosterl
 */
public interface BufferUploader {
    void uploadBuffers (GL2GL3 gl) throws BufferStateException;
    /**
     * @return the vtxAttribBufferHandle
     */
    int getVtxAttribBufferHandle();

    /**
     * @return the inxBufferHandle
     */
    int getInxBufferHandle();

    /**
     * @return the indexCount
     */
    int getIndexCount();

}
