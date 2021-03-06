package org.janelia.workstation.gui.large_volume_viewer;

import Jama.Matrix;
import org.janelia.it.jacs.shared.geom.CoordinateAxis;
import org.janelia.it.jacs.shared.geom.Vec3;
import org.janelia.it.jacs.shared.octree.ZoomLevel;
import org.janelia.it.jacs.shared.octree.ZoomedVoxelIndex;
import org.janelia.it.jacs.shared.viewer3d.BoundingBox3d;
import org.janelia.model.util.MatrixUtilities;
import org.janelia.rendering.RenderedVolumeMetadata;
import org.janelia.rendering.TileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Common metadata to back end tile data formats
 */
public class TileFormat {
    private static Logger log = LoggerFactory.getLogger(TileFormat.class);

    private static final int X_OFFS = 0;
    private static final int Y_OFFS = 1;
    private static final int Z_OFFS = 2;

    private int[] origin = {0, 0, 0}; // in voxel
    private int[] volumeSize = {0, 0, 0}; // in voxels
    private int[] tileSize = {1024, 1024, 1}; // in voxels (possibly)
    private double[] voxelMicrometers = {1, 1, 1}; // in micrometers per voxel
    private int zoomLevelCount = 0;
    private double[] zoomFactorCache = {1, 2, 4, 8};
    private int bitDepth = 8;
    private int channelCount = 1;
    private int intensityMax = 255;
    private int intensityMin = 0;
    private boolean srgb = false;
    private TileIndex.IndexStyle indexStyle = TileIndex.IndexStyle.QUADTREE;
    private boolean hasZSlices = true;
    private boolean hasXSlices = false;
    private boolean hasYSlices = false;
    private Matrix micronToVoxMatrix;
    private Matrix voxToMicronMatrix;

    public TileFormat() {
        setDefaultParameters();
    }

    // new methods to help convert between TileIndex and xyz micrometer coordinates

    void initializeFromRenderedVolumeMetadata(RenderedVolumeMetadata renderedVolumeMetadata) {
        setDefaultParameters();
        setZoomLevelCount(renderedVolumeMetadata.getNumZoomLevels());
        setVolumeSize(renderedVolumeMetadata.getVolumeSizeInVoxels());
        setVoxelMicrometers(renderedVolumeMetadata.getMicromsPerVoxel());
        setOrigin(renderedVolumeMetadata.getOriginVoxel());
        if (renderedVolumeMetadata.getYzTileInfo() != null) {
            setHasXSlices(true);
            updateTileFormatFromTileInfo(renderedVolumeMetadata.getYzTileInfo());
        }
        if (renderedVolumeMetadata.getZxTileInfo() != null) {
            setHasYSlices(true);
            updateTileFormatFromTileInfo(renderedVolumeMetadata.getZxTileInfo());
        }
        if (renderedVolumeMetadata.getXyTileInfo() != null) {
            setHasZSlices(true);
            updateTileFormatFromTileInfo(renderedVolumeMetadata.getXyTileInfo());
        }
    }

    private void updateTileFormatFromTileInfo(TileInfo tileInfo) {
        setChannelCount(tileInfo.getChannelCount());
        setTileSize(tileInfo.getVolumeSize());
        setSrgb(tileInfo.isSrgb());
        setBitDepth(tileInfo.getBitDepth());
        setIntensityMax((int) Math.pow(2, tileInfo.getBitDepth()) - 1);
    }

    /**
     * NOTE - it is possible for tileIndexForXyz to return
     * invalid TileIndexes, for example when the xyz coordinate lies
     * outside the volume.
     *
     * @param xyz
     * @param zoom
     * @param sliceDirection
     * @return
     */
    public TileIndex tileIndexForXyz(Vec3 xyz, int zoom, CoordinateAxis sliceDirection) {
        ZoomLevel zoomLevel = new ZoomLevel(zoom); // TODO put in arg list
        int zoomMax = getZoomLevelCount() - 1;
        // New way
        MicrometerXyz um = new MicrometerXyz(xyz.getX(), xyz.getY(), xyz.getZ());
        VoxelXyz vox = voxelXyzForMicrometerXyz(um);
        ZoomedVoxelIndex zvox = zoomedVoxelIndexForVoxelXyz(vox, zoomLevel, sliceDirection);
        TileXyz tileXyz = tileXyzForZoomedVoxelIndex(zvox, sliceDirection);
        return new TileIndex(
                tileXyz.getX(), tileXyz.getY(), tileXyz.getZ(),
                zoom, zoomMax, indexStyle, sliceDirection);
    }

    TileIndex tileIndexForZoomedVoxelIndex(ZoomedVoxelIndex ix,
                                           CoordinateAxis sliceDirection) {
        int zoom = ix.getZoomLevel().getLog2ZoomOutFactor();
        TileXyz tileXyz = tileXyzForZoomedVoxelIndex(
                ix,
                sliceDirection);
        int zoomMax = getZoomLevelCount() - 1;
        return new TileIndex(
                tileXyz.getX(), tileXyz.getY(), tileXyz.getZ(),
                zoom, zoomMax, indexStyle, sliceDirection);
    }

    public BoundingBox3d calcBoundingBox() {
        Vec3 b0 = new Vec3(calcLowerBBCoord(X_OFFS), calcLowerBBCoord(Y_OFFS), calcLowerBBCoord(Z_OFFS));
        Vec3 b1 = new Vec3(calcUpperBBCoord(X_OFFS), calcUpperBBCoord(Y_OFFS), calcUpperBBCoord(Z_OFFS));
        BoundingBox3d result = new BoundingBox3d();
        result.setMin(b0);
        result.setMax(b1);
        return result;
    }

    public int zoomLevelForCameraZoom(double pixelsPerSceneUnit) {
        // use slightly lower resolution in the interest of speed.
        final double zoomOffset = 0.5;
        //
        double[] vm = getVoxelMicrometers();
        double maxRes = Math.min(vm[X_OFFS], Math.min(vm[Y_OFFS], vm[Z_OFFS]));
        double voxelsPerPixel = 1.0 / (pixelsPerSceneUnit * maxRes);
        int zoomMax = getZoomLevelCount() - 1;
        int zoom = zoomMax; // default to very coarse zoom
        if (voxelsPerPixel > 0.0) {
            double topZoom = Math.log(voxelsPerPixel) / Math.log(2.0);
            zoom = (int) (topZoom + zoomOffset);
        }
        int zoomMin = 0;
        zoom = Math.max(zoom, zoomMin);
        zoom = Math.min(zoom, zoomMax);
        return zoom;
    }

    TileBoundingBox viewBoundsToTileBounds(int[] xyzFromWhd, ViewBoundingBox screenBounds0, int zoom) {

        double zoomFactor = Math.pow(2.0, zoom);
        // get tile pixel size 1024 from loadAdapter
        double resolution0 = getVoxelMicrometers()[xyzFromWhd[0]];
        double resolution1 = getVoxelMicrometers()[xyzFromWhd[1]];
        double tileWidth = tileSize[xyzFromWhd[0]] * zoomFactor * resolution0;
        double tileHeight = tileSize[xyzFromWhd[1]] * zoomFactor * resolution1;

        // Local copy of micrometer bounds before flipping
        BoundingBox3d bb = calcBoundingBox();
        double xMinViewUnit = screenBounds0.getwFMin() - bb.getMinX();
        double xMaxViewUnit = screenBounds0.getwFMax() - bb.getMinX();
        double yMinViewUnit = screenBounds0.gethFMin() - bb.getMinY();
        double yMaxViewUnit = screenBounds0.gethFMax() - bb.getMinY();

        // Invert Y for target coordinate system.
        double bottomY = bb.getMax().getY() - bb.getMinY();

        // Correct for bottom Y origin of Raveler tile coordinate system
        // (everything else is top Y origin: image, our OpenGL, user facing coordinate system)
        if (xyzFromWhd[X_OFFS] == Y_OFFS) { // Y axis left-right
            double temp = xMinViewUnit;
            xMinViewUnit = bottomY - xMaxViewUnit;
            xMaxViewUnit = bottomY - temp;
        } else if (xyzFromWhd[Y_OFFS] == Y_OFFS) { // Y axis top-bottom
            double temp = yMinViewUnit;
            yMinViewUnit = bottomY - yMaxViewUnit;
            yMaxViewUnit = bottomY - temp;
        }

        int wMin = (int) Math.floor(xMinViewUnit / tileWidth);
        int wMax = (int) Math.floor(xMaxViewUnit / tileWidth);

        int hMin = (int) Math.floor(yMinViewUnit / tileHeight);
        int hMax = (int) Math.floor(yMaxViewUnit / tileHeight);

        TileBoundingBox tileUnits = new TileBoundingBox();
        tileUnits.sethMax(hMax);
        tileUnits.sethMin(hMin);
        tileUnits.setwMax(wMax);
        tileUnits.setwMin(wMin);

        return tileUnits;
    }

    /**
     * This will use the meaningful intersection of the bounding box and
     * the view, and take 1/2-unit-over-boundary into consideration.  It will
     * produce a set of width and height bounds that contain the focus
     * point, within the stated width and height.
     * Output includes view-width and view-height sized rectangle, with focus as
     * near to center as possible
     *
     * @param viewWidth         how wide to make surrounding view in wide axis.
     * @param viewHeight        how wide to make surrounding view in high axis.
     * @param focus             output must include (ideally, centered) this point.
     * @param pixelsPerViewUnit used for calculating surrounding rectangle.
     * @param xyzFromWhd        indirection for axial sequence numbers.
     * @return min/max-delimiting box.
     */
    ViewBoundingBox findViewBounds(int viewWidth, int viewHeight, Vec3 focus, double pixelsPerViewUnit, int[] xyzFromWhd) {
        BoundingBox3d bb = calcBoundingBox();
        // bb = originAdjustBoundingBox(bb, xyzFromWhd);
        // focus = originAdjustCameraFocus(focus, xyzFromWhd);

        // Clip to bounded space
        double xMinView = focus.get(xyzFromWhd[X_OFFS]) - 0.5 * viewWidth / pixelsPerViewUnit;
        double xMaxView = focus.get(xyzFromWhd[X_OFFS]) + 0.5 * viewWidth / pixelsPerViewUnit;

        double yMinView = focus.get(xyzFromWhd[Y_OFFS]) - 0.5 * viewHeight / pixelsPerViewUnit;
        double yMaxView = focus.get(xyzFromWhd[Y_OFFS]) + 0.5 * viewHeight / pixelsPerViewUnit;

        // Subtract one half pixel to avoid loading an extra layer of tiles
        double dw = 0.25 * getVoxelMicrometers()[xyzFromWhd[X_OFFS]];
        double dh = 0.25 * getVoxelMicrometers()[xyzFromWhd[Y_OFFS]];

        // Clip to bounding box
        double xMinViewUnit = Math.max(xMinView, bb.getMin().get(xyzFromWhd[X_OFFS]) + dw);
        double yMinViewUnit = Math.max(yMinView, bb.getMin().get(xyzFromWhd[Y_OFFS]) + dh);

        double xMaxViewUnit = Math.min(xMaxView, bb.getMax().get(xyzFromWhd[X_OFFS]) - dw);
        double yMaxViewUnit = Math.min(yMaxView, bb.getMax().get(xyzFromWhd[Y_OFFS]) - dh);

        ViewBoundingBox viewBoundaries = new ViewBoundingBox();
        viewBoundaries.sethFMax(yMaxViewUnit);
        viewBoundaries.sethFMin(yMinViewUnit);

        viewBoundaries.setwFMax(xMaxViewUnit);
        viewBoundaries.setwFMin(xMinViewUnit);
        return viewBoundaries;
    }


    int calcRelativeTileDepth(int[] xyzFromWhd, double focusDepth, BoundingBox3d bb) {
        // Bounding box is actually 0.5 voxels bigger than number of slices at each end
        int dMin = (int) (bb.getMin().get(xyzFromWhd[Z_OFFS]) / getVoxelMicrometers()[xyzFromWhd[Z_OFFS]] + 0.5);
        int dMax = (int) (bb.getMax().get(xyzFromWhd[Z_OFFS]) / getVoxelMicrometers()[xyzFromWhd[Z_OFFS]] - 0.5);
        int absoluteTileDepth = (int) Math.round(focusDepth / getVoxelMicrometers()[xyzFromWhd[Z_OFFS]] - 0.5);
        absoluteTileDepth = Math.max(absoluteTileDepth, dMin);
        absoluteTileDepth = Math.min(absoluteTileDepth, dMax);
        int relativeTileDepth = absoluteTileDepth - getOrigin()[xyzFromWhd[Z_OFFS]];
        return relativeTileDepth;
    }

    public TileIndex.IndexStyle getIndexStyle() {
        return indexStyle;
    }

    void setIndexStyle(TileIndex.IndexStyle indexStyle) {
        this.indexStyle = indexStyle;
    }

    /**
     * Upper left front corner of volume, in units of voxels
     *
     * @return
     */
    public int[] getOrigin() {
        return origin;
    }


    /**
     * (maximum) Size of individual tiles, in units of voxels.
     * Includes extent in all X/Y/Z directions, even though any particular
     * 2D tile will extend in only two dimensions.
     * Tiles on the right/upper/front edges of some volumes might be less than
     * this full tile size, if the total volume size is not a multiple of the
     * tile size.
     *
     * @return
     */
    int[] getTileSize() {
        return tileSize;
    }

    /**
     * Total X/Y/Z size of the represented volume, in units of voxels.
     *
     * @return
     */
    public int[] getVolumeSize() {
        return volumeSize;
    }

    /**
     * X/Y/Z size of one voxel, in units of micrometers per voxel.
     *
     * @return
     */
    public double[] getVoxelMicrometers() {
        return voxelMicrometers;
    }

    /**
     * Number of power-of-two zoom levels available for this zoomable volume
     * representation.
     *
     * @return
     */
    public int getZoomLevelCount() {
        return zoomLevelCount;
    }

    /**
     * Number of bits of image intensity information, per color channel.
     * Should be 8 or 16 (?).
     *
     * @return
     */
    int getBitDepth() {
        return bitDepth;
    }

    /**
     * Number of color channels in image data.
     *
     * @return
     */
    public int getChannelCount() {
        return channelCount;
    }

    /**
     * Largest intensity in the image. For example, for 12-bit data in a 16-bit
     * container, getBitDepth() == 16, and getIntensityMax() == 4095.0.
     *
     * @return
     */
    int getIntensityMax() {
        return intensityMax;
    }

    /**
     * Smallest intensity in the image. Usually zero.
     * (or one? Zero means "missing" or "invalid")
     *
     * @return
     */
    int getIntensityMin() {
        return intensityMin;
    }

    /**
     * Whether the image intensity data are already pre-corrected for display
     * on a computer monitor. Usually false, meaning that the intensities
     * are linearly related to luminous intensity.
     *
     * @return
     */
    public boolean isSrgb() {
        return srgb;
    }

    public void setOrigin(int[] origin) {
        this.origin = origin;
    }

    void setVolumeSize(int[] volumeSize) {
        // In case somewhere, the original array is being passed around
        // and used directly, prior to having been reset from defaults.
        if (this.volumeSize != null) {
            for (int i = 0; i < volumeSize.length; i++) {
                this.volumeSize[i] = volumeSize[i];
            }
        }
        this.volumeSize = volumeSize;
    }

    /**
     * @return the micronToVoxMatrix
     */
    public Matrix getMicronToVoxMatrix() {
        establishConversionMatrices();
        return micronToVoxMatrix;
    }

    /**
     * @param micronToVoxMatrix the micronToVoxMatrix to set
     */
    public void setMicronToVoxMatrix(Matrix micronToVoxMatrix) {
        this.micronToVoxMatrix = micronToVoxMatrix;
    }

    /**
     * @return the voxToMicronMatrix
     */
    public Matrix getVoxToMicronMatrix() {
        establishConversionMatrices();
        return voxToMicronMatrix;
    }

    /**
     * @param voxToMicronMatrix the voxToMicronMatrix to set
     */
    public void setVoxToMicronMatrix(Matrix voxToMicronMatrix) {
        this.voxToMicronMatrix = voxToMicronMatrix;
    }

    void setTileSize(int[] tileSize) {
        // In case somewhere, the original array is being passed around
        // and used directly, prior to having been reset from defaults.
        if (this.tileSize != null) {
            for (int i = 0; i < tileSize.length; i++) {
                this.tileSize[i] = tileSize[i];
            }
        } else {
            this.tileSize = tileSize;
        }
    }

    void setVoxelMicrometers(double[] voxelMicrometers) {
        this.voxelMicrometers = voxelMicrometers;
    }

    void setZoomLevelCount(int zoomLevelCount) {
        if (this.zoomLevelCount == zoomLevelCount)
            return; // no change
        this.zoomLevelCount = zoomLevelCount;
        // Avoid calling Math.pow() every single time
        zoomFactorCache = new double[zoomLevelCount];
        for (int z = 0; z < zoomLevelCount; ++z)
            zoomFactorCache[z] = Math.pow(2, z);
    }

    void setBitDepth(int bitDepth) {
        this.bitDepth = bitDepth;
    }

    void setChannelCount(int channelCount) {
        this.channelCount = channelCount;
    }

    void setIntensityMax(int intensityMax) {
        this.intensityMax = intensityMax;
    }

    void setIntensityMin(int intensityMin) {
        this.intensityMin = intensityMin;
    }

    /**
     * Populates an initial non-pathological set of image parameters:
     * origin = [0,0,0]
     * volumeSize = [512,512,512]
     * tileSize = [512,512,1]
     * zoomLevelCount = 1
     * bitDepth = 8
     * channelCount = 3
     * intensityMin = 0
     * intensityMax = 255
     * sRgb = false
     */
    final void setDefaultParameters() {
        for (int i = 0; i < 3; ++i) {
            origin[i] = 0;
            volumeSize[i] = 512; // whatever
            tileSize[i] = 512; // X, Y, but not Z...
            voxelMicrometers[i] = 1.0;
        }
        tileSize[Z_OFFS] = 1; // tiles are 512x512x1
        setZoomLevelCount(1); // like small images
        bitDepth = 8;
        channelCount = 3; // rgb
        intensityMin = 0;
        intensityMax = 255;
        srgb = false;
        hasXSlices = false;
        hasYSlices = false;
        hasZSlices = true;
    }

    void setSrgb(boolean srgb) {
        this.srgb = srgb;
    }

    boolean isHasZSlices() {
        return hasZSlices;
    }

    boolean isHasXSlices() {
        return hasXSlices;
    }

    boolean isHasYSlices() {
        return hasYSlices;
    }

    void setHasZSlices(boolean hasZSlices) {
        this.hasZSlices = hasZSlices;
    }

    void setHasXSlices(boolean hasXSlices) {
        this.hasXSlices = hasXSlices;
    }

    void setHasYSlices(boolean hasYSlices) {
        this.hasYSlices = hasYSlices;
    }

    // Aug 26, 2013 attempt at greater type safety between different x/y/z units
    /// Unit conversion methods:
    public MicrometerXyz micrometerXyzForVoxelXyz(VoxelXyz v, CoordinateAxis sliceDirection) {
        // return point at upper-left corner of voxel, but centered in slice direction
        double xyz[] = {
                v.getX() + origin[X_OFFS],
                v.getY() + origin[Y_OFFS],
                v.getZ() + origin[Z_OFFS],
        };
        xyz[sliceDirection.index()] += 0.5;
        return new MicrometerXyz(
                xyz[0] * getVoxelMicrometers()[X_OFFS],
                xyz[1] * getVoxelMicrometers()[Y_OFFS],
                xyz[2] * getVoxelMicrometers()[Z_OFFS]);
    }

    MicrometerXyz micrometerXyzForVoxelXyzMatrix(VoxelXyz v, CoordinateAxis sliceDirection) {
        establishConversionMatrices();
        double[] rawVoxels = new double[]{
                v.getX(), v.getY(), v.getZ(), 1.0
        };
        rawVoxels[sliceDirection.index()] += 0.5;
        Matrix voxels = new Matrix(rawVoxels, 4);
        Matrix result = getVoxToMicronMatrix().times(voxels);
        double[][] resultArr = result.getArray();
        MicrometerXyz m = new MicrometerXyz(
                resultArr[X_OFFS][0],
                resultArr[Y_OFFS][0],
                resultArr[Z_OFFS][0]
        );
        return m;
    }

    public VoxelXyz voxelXyzForMicrometerXyz(MicrometerXyz m) {
        return new VoxelXyz(
                (int) Math.floor(m.getX() / getVoxelMicrometers()[X_OFFS]) - origin[X_OFFS],
                (int) Math.floor(m.getY() / getVoxelMicrometers()[Y_OFFS]) - origin[Y_OFFS],
                (int) Math.floor(m.getZ() / getVoxelMicrometers()[Z_OFFS]) - origin[Z_OFFS]);
    }

    public VoxelXyz voxelXyzForMicrometerXyzMatrix(MicrometerXyz m) {
        establishConversionMatrices();
        double[] rawMicrons = new double[]{
                m.getX(), m.getY(), m.getZ(), 1.0
        };
        Matrix microns = new Matrix(rawMicrons, 4);
        Matrix result = getMicronToVoxMatrix().times(microns);
        return new VoxelXyz((int) result.get(0, 0), (int) result.get(1, 0), (int) result.get(2, 0));
    }

    VoxelXyz voxelXyzForZoomedVoxelIndex(ZoomedVoxelIndex z, CoordinateAxis sliceAxis) {
        int zoomFactor = z.getZoomLevel().getZoomOutFactor();
        int xyz[] = {z.getX(), z.getY(), z.getZ()};
        int depthAxis = sliceAxis.index();
        for (int i = 0; i < 3; ++i) {
            if ((i == depthAxis) && (indexStyle == TileIndex.IndexStyle.QUADTREE || indexStyle == TileIndex.IndexStyle.OCTREE))
                continue; // don't zoom on slice axis in quadtree mode
            xyz[i] *= zoomFactor;
        }
        return new VoxelXyz(xyz[X_OFFS], xyz[Y_OFFS], xyz[Z_OFFS]);
    }

    public Vec3 voxelVec3ForMicronVec3(Vec3 micronVec3) {
        MicrometerXyz micrometerXyz = new MicrometerXyz(micronVec3.getX(), micronVec3.getY(), micronVec3.getZ());
        VoxelXyz vox = voxelXyzForMicrometerXyz(micrometerXyz);
        return new Vec3(vox.getX(), vox.getY(), vox.getZ());
    }

    public TileFormat.MicrometerXyz micrometerXyzForZoomedVoxelIndex(ZoomedVoxelIndex zv, CoordinateAxis axis) {
        TileFormat.VoxelXyz vx = voxelXyzForZoomedVoxelIndex(zv, axis);
        return micrometerXyzForVoxelXyz(vx, axis);
    }

    private Vec3 micronVec3ForVoxelVec3Cornered(Vec3 voxelVec3) {
        TileFormat.VoxelXyz vox = new TileFormat.VoxelXyz(voxelVec3);
        TileFormat.MicrometerXyz micron = micrometerXyzForVoxelXyz(vox, CoordinateAxis.Z);
        return new Vec3(micron.getX(), micron.getY(), micron.getZ());
    }

    public Vec3 micronVec3ForVoxelVec3Centered(Vec3 voxelVec3) {
        return micronVec3ForVoxelVec3Cornered(voxelVec3).plus(new Vec3(0.5 * voxelMicrometers[0], 0.5 * voxelMicrometers[1], -0.5 * voxelMicrometers[2]));
    }

    public ZoomedVoxelIndex zoomedVoxelIndexForVoxelXyz(VoxelXyz v, ZoomLevel zoomLevel, CoordinateAxis sliceAxis) {
        int zoomFactor = zoomLevel.getZoomOutFactor();
        int xyz[] = {v.getX(), v.getY(), v.getZ()};
        int depthAxis = sliceAxis.index();
        for (int i = 0; i < 3; ++i) {
            if ((i == depthAxis) && (indexStyle == TileIndex.IndexStyle.QUADTREE || indexStyle == TileIndex.IndexStyle.OCTREE))
                continue; // don't zoom on slice axis in quadtree mode
            xyz[i] /= zoomFactor;
        }
        return new ZoomedVoxelIndex(zoomLevel, xyz[X_OFFS], xyz[Y_OFFS], xyz[Z_OFFS]);
    }

    /**
     * ZoomedVoxel at upper left front corner of tile
     *
     * @param t
     * @param sliceAxis
     * @return
     */
    ZoomedVoxelIndex zoomedVoxelIndexForTileXyz(TileXyz t, ZoomLevel zoomLevel, CoordinateAxis sliceAxis) {
        int xyz[] = {t.getX(), t.getY(), t.getZ()};
        int depthAxis = sliceAxis.index();
        for (int i = 0; i < 3; ++i) {
            if (i == depthAxis)
                continue; // Don't scale depth axis
            else
                xyz[i] = xyz[i] * getTileSize()[i]; // scale horizontal and vertical
        }
        // Invert Y axis to convert to Raveler convention from image convention.
        int zoomFactor = zoomLevel.getZoomOutFactor();
        int maxZoomVoxelY = volumeSize[Y_OFFS] / zoomFactor;
        xyz[1] = maxZoomVoxelY - xyz[Y_OFFS] - getTileSize()[Y_OFFS];
        //
        return new ZoomedVoxelIndex(zoomLevel, xyz[X_OFFS], xyz[Y_OFFS], xyz[Z_OFFS]);
    }

    /**
     * TileIndex xyz containing ZoomedVoxel
     */
    TileXyz tileXyzForZoomedVoxelIndex(ZoomedVoxelIndex z, CoordinateAxis sliceAxis) {
        int xyz[] = {z.getX(), z.getY(), z.getZ()};
        // Invert Y axis to convert to Raveler convention from image convention.
        int zoomFactor = z.getZoomLevel().getZoomOutFactor();
        int maxZoomVoxelY = volumeSize[Y_OFFS] / zoomFactor - 1;
        xyz[1] = maxZoomVoxelY - xyz[1];
        int depthAxis = sliceAxis.index();
        for (int i = 0; i < 3; ++i) {
            if (i == depthAxis)
                continue; // Don't scale depth axis
            else
                xyz[i] = xyz[i] / getTileSize()[i]; // scale horizontal and vertical
        }
        return new TileXyz(xyz[X_OFFS], xyz[Y_OFFS], xyz[Z_OFFS]);
    }

    /**
     * convenience: return a centered-up version of the micrometer value.
     * Use this whenever micrometer values need to be pushed onto the screen.
     */
    public Vec3 centerJustifyMicrometerCoordsAsVec3(MicrometerXyz microns) {
        Vec3 v = new Vec3(
                // Translate from upper left front corner of voxel to center of voxel
                microns.getX() + 0.5 * voxelMicrometers[0],
                microns.getY() + 0.5 * voxelMicrometers[1],
                microns.getZ() - 0.5 * voxelMicrometers[2]);
        return v;
    }

    // Volume units can be one of 4 interconvertible types
    // These classes are intended to enforce type safety between different unit types
    interface Unit {
    }

    // Base unit
    static class MicrometerUnit implements Unit {
    }

    // 1 um
    static class VoxelUnit implements Unit {
    }

    static class TileUnit implements Unit {
    }

    public static class UnittedVec3Int<U extends Unit> implements Cloneable {
        private int[] data = new int[3];

        UnittedVec3Int(int x, int y, int z) {
            data[X_OFFS] = x;
            data[Y_OFFS] = y;
            data[Z_OFFS] = z;
        }

        @Override
        public UnittedVec3Int<U> clone() {
            return new UnittedVec3Int<U>(data[X_OFFS], data[1], data[2]);
        }

        public int getX() {
            return data[X_OFFS];
        }

        public int getY() {
            return data[Y_OFFS];
        }

        public int getZ() {
            return data[Z_OFFS];
        }
    }

    public static class UnittedVec3Double<U extends Unit> implements Cloneable {
        private double[] data = new double[3];

        UnittedVec3Double(double x, double y, double z) {
            data[X_OFFS] = x;
            data[Y_OFFS] = y;
            data[Z_OFFS] = z;
        }

        @Override
        public UnittedVec3Double<U> clone() {
            return new UnittedVec3Double<U>(data[X_OFFS], data[Y_OFFS], data[Z_OFFS]);
        }

        public double getX() {
            return data[X_OFFS];
        }

        public double getY() {
            return data[Y_OFFS];
        }

        public double getZ() {
            return data[Z_OFFS];
        }
    }

    // Base
    public static class MicrometerXyz extends UnittedVec3Double<MicrometerUnit> {
        public MicrometerXyz(Vec3 coords) {
            super(coords.getX(), coords.getY(), coords.getZ());
        }

        public MicrometerXyz(double x, double y, double z) {
            super(x, y, z);
        }

        public Vec3 asVec3() {
            return new Vec3(getX(), getY(), getZ());
        }
    }

    public static class VoxelXyz extends UnittedVec3Int<VoxelUnit> {
        public VoxelXyz(Vec3 coords) {
            super((int) coords.getX(), (int) coords.getY(), (int) coords.getZ());
        }

        public VoxelXyz(int x, int y, int z) {
            super(x, y, z);
        }

        public VoxelXyz(int[] xyz) {
            super(xyz[X_OFFS], xyz[Y_OFFS], xyz[Z_OFFS]);
        }

        public Vec3 asVec3() {
            return new Vec3(getX(), getY(), getZ());
        }
    }

    ; // 2

    static class TileXyz extends UnittedVec3Int<TileUnit> {
        TileXyz(int x, int y, int z) {
            super(x, y, z);
        }
    } // 4

    @SuppressWarnings("unused")
    private void bbToScreenScenarioDump(ViewBoundingBox screenBoundaries, BoundingBox3d bb, int viewWidth, int viewHeight) {
        log.info("================================================");
        log.info("SCENARIO: TileFormat.boundingBoxToScreenBounds()");
        log.info("View width=" + viewWidth + ", View Height=" + viewHeight);
        log.info("" + screenBoundaries);
        log.info("" + bb);
    }

    private double calcLowerBBCoord(int index) {
        return getVoxelMicrometers()[index] * getOrigin()[index];
    }

    private double calcUpperBBCoord(int index) {
        return getVoxelMicrometers()[index] * (getOrigin()[index] + getVolumeSize()[index]);
    }

    /**
     * Lazily initialize matrices to move between voxel and stage/micron.
     */
    private void establishConversionMatrices() {
        if (this.voxToMicronMatrix == null || this.micronToVoxMatrix == null) {
            Matrix voxToMicronMatrix = MatrixUtilities.buildVoxToMicron(voxelMicrometers, origin);
            setVoxToMicronMatrix(voxToMicronMatrix);
            Matrix micronToVoxMatrix = MatrixUtilities.buildMicronToVox(voxelMicrometers, origin);
            setMicronToVoxMatrix(micronToVoxMatrix);
        }
    }
}
