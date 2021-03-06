package org.janelia.workstation.tracing;

import java.util.List;

public interface AnchoredVoxelPath {
    Long getNeuronID();
    SegmentIndex getSegmentIndex();
    List<VoxelPosition> getPath();
}
