package org.janelia.workstation.gui.large_volume_viewer;

/**
 * Created by murphys on 11/6/2015.
 */
public class VolumeCache {

    private static boolean volumeCache = false;

    public static boolean useVolumeCache() {
        return volumeCache;
    }

    static void setVolumeCache(boolean volumeCache) {
        VolumeCache.volumeCache=volumeCache;
    }

}


