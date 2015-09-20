package org.janelia.it.workstation.gui.large_volume_viewer;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.media.jai.JAI;
import javax.media.jai.ParameterBlockJAI;
import org.janelia.it.workstation.cache.large_volume.CacheController;
import org.janelia.it.workstation.cache.large_volume.CacheFacadeI;
import org.janelia.it.workstation.geom.CoordinateAxis;
import org.janelia.it.workstation.gui.large_volume_viewer.exception.DataSourceInitializeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This load adapter will employ 3D, not 2D caching.
 * 
 * @author fosterl
 */
public class Cache3DOctreeLoadAdapter extends AbstractTextureLoadAdapter {

    private static final Logger log = LoggerFactory.getLogger(Cache3DOctreeLoadAdapter.class);

    // Metadata: file location required for local system as mount point.
    private File topFolder;
    private String remoteBasePath;
    private int standardFileSize;
    private boolean acceptNullDecoders = true;
    private int sliceSize = -1;
    
    @Override
    public TextureData2dGL loadToRam(TileIndex tileIndex) throws TileLoadError, MissingTileException {
        return loadToRam(tileIndex, true);
    }
    
    public TextureData2dGL loadToRam(TileIndex tileIndex, boolean zOriginNegativeShift) throws TileLoadError, MissingTileException {
        // Create a local load timer to measure timings just in this thread
        LoadTimer localLoadTimer = new LoadTimer();
        localLoadTimer.mark("starting slice load");
        final File octreeFilePath = OctreeMetadataSniffer.getOctreeFilePath(tileIndex, tileFormat, zOriginNegativeShift);
        if (octreeFilePath == null) {
            return null;
        }
		// TODO - generalize to URL, if possible
        // (though TIFF requires seek, right?)
        // Compute octree path from Raveler-style tile indices
        File folder = new File(topFolder, octreeFilePath.toString());

		// TODO for debugging, show file name for X tiles
        // Compute local z slice
        int zoomScale = (int) Math.pow(2, tileIndex.getZoom());
        int axisIx = tileIndex.getSliceAxis().index();
        int tileDepth = tileFormat.getTileSize()[axisIx];
        int absoluteSlice = (tileIndex.getCoordinate(axisIx)) / zoomScale;
        int relativeSlice = absoluteSlice % tileDepth;
        // Raveller y is flipped so flip when slicing in Y (right?)
        if (axisIx == 1) {
            relativeSlice = tileDepth - relativeSlice - 1;
        }

        return loadSlice(relativeSlice, tileIndex, folder, tileIndex.getSliceAxis());
    }

    public File getTopFolder() {
        return topFolder;
    }
    
    /** Order dependency: call this before setTopFolder. */
    public void setRemoteBasePath( String remoteBasePath ) {
        this.remoteBasePath = remoteBasePath;
    }    

    public void setTopFolder(File topFolder) throws DataSourceInitializeException {
        this.topFolder = topFolder;
        final OctreeMetadataSniffer octreeMetadataSniffer = new OctreeMetadataSniffer(topFolder, tileFormat);
        octreeMetadataSniffer.setRemoteBasePath(remoteBasePath);
        octreeMetadataSniffer.sniffMetadata(topFolder);
        standardFileSize = octreeMetadataSniffer.getStandardVolumeSize();
        sliceSize = octreeMetadataSniffer.getSliceSize(); //tileFormat.getTileSize()[0] * tileFormat.getTileSize()[1] * (tileFormat.getBitDepth()/8);
		// Don't launch pre-fetch yet.
        // That must occur AFTER volume initialized signal is sent.
    }
    
    public int getStandardFileSize() {
        return standardFileSize;
    }
    
    private TextureData2dGL loadSlice(int relativeZ, TileIndex tileIndex, File folder, CoordinateAxis axis) {
        
        TextureData2dGL tex = new TextureData2dGL();
        final int sc = tileFormat.getChannelCount();
        Collection<byte[]> byteArrays = new ArrayList<>();
        
        String tiffBase = OctreeMetadataSniffer.getTiffBase(axis);
        StringBuilder missingTiffs = new StringBuilder();
        StringBuilder requestedTiffs = new StringBuilder();
        CacheFacadeI cacheManager = CacheController.getInstance().getManager();
        if (cacheManager == null) {
            return null;
        }

        int totalBufferSize = 0;
        for (int c = 0; c < sc; ++c) {
            // Need to establish the channels, out of data extracted from cache.
            File tiff = new File(folder, OctreeMetadataSniffer.getFilenameForChannel(tiffBase, c));
            if (requestedTiffs.length() > 0) {
                requestedTiffs.append("; ");
            }
            requestedTiffs.append(tiff);
            if (!tiff.exists()) {
                if (acceptNullDecoders) {
                    if (missingTiffs.length() > 0) {
                        missingTiffs.append(", ");
                    }
                    missingTiffs.append(tiff);
                }
            }
            else {
                byte[] tiffBytes = cacheManager.getBytes(tiff);
                if ( tiffBytes != null ) {
                    // Must carve out just the right portion.
                    try {
                        byte[] slice = new byte[sliceSize];
                        System.arraycopy(tiffBytes, sliceSize * relativeZ, slice, 0, sliceSize);
                        //    final int sliceSize = loader.getSx() * loader.getSy() * 2;
                        //    System.arraycopy( finalTiffBytes, sliceSize * i, slice, 0, sliceSize );                        
                        org.janelia.it.workstation.cache.large_volume.Utilities.zeroScan(tiffBytes, "Cache3DOctreeLoadAdapter.loadSlice()::basetiff", folder.toString());
                        if (!org.janelia.it.workstation.cache.large_volume.Utilities.zeroScan(slice, "Cache3DOctreeLoadAdapter.loadSlice()::slicecopy", folder.toString())) {
                            log.info("Slice size = {}", sliceSize);
                            for (int i = 0; i < 200; i++) {
                                slice = new byte[sliceSize];
                                System.arraycopy(tiffBytes, sliceSize * i, slice, 0, sliceSize);
                                org.janelia.it.workstation.cache.large_volume.Utilities.zeroScan(slice, "Cache3DOctreeLoadAdapter.loadSlice()::slicecopy", folder+" slice #" + i);
                            }
                        }
                        totalBufferSize += sliceSize;
                        byteArrays.add(slice);
                    } catch ( RuntimeException rte ) {
                        log.error("System exception during read of bytes");
                        rte.printStackTrace();
                    }
                }
                else {
                    log.error("Tiff bytes are null.");
                }
            }
        }
        
        // Combine channels into one image
//        RenderedImage composite = channels[0];
//        if (sc > 1) {
//            try {
//                ParameterBlockJAI pb = new ParameterBlockJAI("bandmerge");
//                for (int c = 0; c < sc; ++c) {
//                    pb.addSource(channels[c]);
//                }
//                composite = JAI.create("bandmerge", pb);
//            } catch (NoClassDefFoundError exc) {
//                exc.printStackTrace();
//                return null;
//            }
//            // localLoadTimer.mark("merged channels");
//        }

        ByteBuffer pixels = ByteBuffer.allocate(totalBufferSize);
        for (byte[] bytes: byteArrays) {
            org.janelia.it.workstation.cache.large_volume.Utilities.zeroScan(bytes, "Cache3DOctreeLoadAdapter.loadSlice()::bufferbuild", folder.toString());
            pixels.put(bytes);
        }
        tex.setPixels(pixels);
        //tex.loadRenderedImage(composite);
        return tex;
    }
    
}
