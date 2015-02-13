/* 
 * Licensed under the Janelia Farm Research Campus Software Copyright 1.1
 * 
 * Copyright (c) 2014, Howard Hughes Medical Institute, All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 
 *     1. Redistributions of source code must retain the above copyright notice, 
 *        this list of conditions and the following disclaimer.
 *     2. Redistributions in binary form must reproduce the above copyright 
 *        notice, this list of conditions and the following disclaimer in the 
 *        documentation and/or other materials provided with the distribution.
 *     3. Neither the name of the Howard Hughes Medical Institute nor the names 
 *        of its contributors may be used to endorse or promote products derived 
 *        from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, ANY 
 * IMPLIED WARRANTIES OF MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR A 
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR 
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; 
 * REASONABLE ROYALTIES; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY 
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT 
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS 
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.janelia.horta;

import com.jogamp.opengl.util.awt.AWTGLReadBufferUtil;
// import com.jogamp.opengl.util.awt.TextRenderer;
// import com.jogamp.opengl.util.awt.Screenshot;
import org.janelia.geometry3d.BrightnessModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import javax.imageio.ImageIO;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.janelia.geometry.util.PerformanceTimer;
import org.janelia.horta.volume.BrickInfo;
import org.janelia.horta.volume.BrickInfoSet;
import org.janelia.horta.volume.MouseLightYamlBrickSource;
import org.janelia.horta.volume.StaticVolumeBrickSource;
import org.janelia.geometry3d.AbstractCamera;
import org.janelia.geometry3d.ConstVector3;
import org.janelia.geometry3d.Matrix4;
import org.janelia.geometry3d.MeshGeometry;
import org.janelia.geometry3d.PerspectiveCamera;
import org.janelia.geometry3d.Quaternion;
import org.janelia.geometry3d.Rotation;
import org.janelia.geometry3d.Vantage;
import org.janelia.geometry3d.Vector3;
import org.janelia.geometry3d.Vector4;
import org.janelia.geometry3d.Viewport;
import org.janelia.gltools.GL3Actor;
import org.janelia.gltools.MeshActor;
import org.janelia.gltools.MultipassRenderer;
import org.janelia.gltools.texture.Texture3d;
import org.janelia.gltools.material.VolumeMipMaterial;
import org.janelia.scenewindow.OrbitPanZoomInteractor;
import org.janelia.scenewindow.SceneRenderer;
import org.janelia.scenewindow.SceneRenderer.CameraType;
import org.janelia.scenewindow.SceneWindow;
import org.janelia.scenewindow.fps.FrameTracker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.MouseUtils;
import org.openide.awt.StatusDisplayer;
import org.openide.util.Exceptions;
import org.openide.windows.TopComponent;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.Lookups;

/**
 * Top component which displays something.
 */
@ConvertAsProperties(
        dtd = "-//org.janelia.horta//NeuronTracer//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "NeuronTracerTopComponent",
        iconBase = "org/janelia/horta/neuronTracerCubic16.png",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "editor", openAtStartup = false)
@ActionID(category = "Window", id = "org.janelia.horta.NeuronTracerTopComponent")
@ActionReference(path = "Menu/Window/Horta" /*, position = 333 */)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_NeuronTracerAction",
        preferredID = "NeuronTracerTopComponent"
)
@Messages({
    "CTL_NeuronTracerAction=Horta Tracer",
    "CTL_NeuronTracerTopComponent=Horta Tracer",
    "HINT_NeuronTracerTopComponent=Horta Neuron Tracer window"
})
public final class NeuronTracerTopComponent extends TopComponent
        implements VolumeProjection {

    private SceneWindow sceneWindow;
    private OrbitPanZoomInteractor interactor;
    // private MultipassVolumeActor mprActor;
    private VolumeMipMaterial volumeMipMaterial;

    // Avoid letting double clicks move twice
    private long previousClickTime = Long.MIN_VALUE;
    private final long minClickInterval = 400 * 1000000;

    // Cache latest hover information
    private Vector3 mouseStageLocation = null;
    private final Observer cursorCacheDestroyer;
    
    // load new volumes based on camera postion
    private final Observer volumeLoadTrigger;

    private TracingInteractor tracingInteractor;
    private StaticVolumeBrickSource volumeSource;
    private CenterCrossHairActor crossHairActor;
        
    private NeuronMPRenderer neuronMPRenderer;
    
    private boolean doCubifyVoxels = false;
    
    public NeuronTracerTopComponent() {
        // This block is what the wizard created
        initComponents();
        setName(Bundle.CTL_NeuronTracerTopComponent());
        setToolTipText(Bundle.HINT_NeuronTracerTopComponent());

        // Below is custom methods by me CMB
        // Drag a YML tilebase file to put some data in the viewer
        setupDragAndDropYml();

        // Insert a specialized SceneWindow into the component
        initialize3DViewer();

        // Change default rotation to Y-down, like large-volume viewer
        sceneWindow.getVantage().setDefaultRotation(new Rotation().setFromAxisAngle(
                new Vector3(1, 0, 0), (float) Math.PI));
        sceneWindow.getVantage().resetRotation();

        setupMouseNavigation();

        // Create right-click context menu
        setupContextMenu(sceneWindow.getInnerComponent());

        // When the camera changes, that blows our cached cursor information
        cursorCacheDestroyer = new Observer() {
            @Override
            public void update(Observable o, Object arg) {
                mouseStageLocation = null; // clear cursor cache
            }
        };
        sceneWindow.getCamera().addObserver(cursorCacheDestroyer);

        // When the camera focus changes, consider updating the tiles displayed
        volumeLoadTrigger = new Observer() {
            private ConstVector3 cachedFocus = null;
            @Override
            public void update(Observable o, Object arg) {
                ConstVector3 newFocus = new Vector3(sceneWindow.getCamera().getVantage().getFocusPosition());
                if (newFocus.equals(cachedFocus))
                    return; // no change
                // System.out.println("focus changed"); // TODO
                cachedFocus = newFocus;
            }
        };
        sceneWindow.getCamera().getVantage().addObserver(volumeLoadTrigger);
        
        // Repaint when color map changes
        brightnessModel.addObserver(new Observer() {
            @Override
            public void update(Observable o, Object arg) {
                // System.out.println("Camera changed");
                sceneWindow.getInnerComponent().repaint();
            }
        });

        setUpActors();
        
    }

    private void setUpActors() 
    {
        
        // TODO - refactor all stages to use multipass renderer, like this
        neuronMPRenderer = new NeuronMPRenderer(sceneWindow.getGLAutoDrawable(), brightnessModel);
        List<MultipassRenderer> renderers = sceneWindow.getRenderer().getMultipassRenderers();
        renderers.clear();
        renderers.add(neuronMPRenderer);
        
        // OLD WAY : TODO - remove below
        
        // 1) First actor in the list creates a background color gradient
        // sceneWindow.getRenderer().addActor(new ColorBackgroundActor(topColor, bottomColor));
        
        // 2) Second actor draws volume images
        /* 
        mprActor = new MultipassVolumeActor(
                null,
                sceneWindow.getGLAutoDrawable(),
                brightnessModel
        );
        sceneWindow.getRenderer().addActor(mprActor);
        */
        
        // 3) Neurite model
        for (NeuriteActor tracingActor : tracingInteractor.createActors()) {
            sceneWindow.getRenderer().addActor(tracingActor);
            tracingActor.getModel().addObserver(new Observer() {
                @Override
                public void update(Observable o, Object arg) {
                    sceneWindow.getInnerComponent().repaint();
                }
            });
        }

        // 4) Scale bar
        sceneWindow.getRenderer().addActor(new ScaleBar());
        
        // 5) Cross hair
        /* */
        crossHairActor = new CenterCrossHairActor();
        sceneWindow.getRenderer().addActor(crossHairActor);
        /* */
    }
    
    public void autoContrast() {
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        Point p = new Point();
        for (int x = 0; x < this.getWidth(); ++x) {
            for (int y = 0; y < this.getHeight(); ++y) {
                p.x = x;
                p.y = y;
                float i = this.getIntensity(p);
                if (i <= 0) {
                    continue;
                }
                min = Math.min(min, i);
                max = Math.max(max, i);
            }
        }
        // System.out.println("Min = "+min+"; Max = "+max);
        if (max == Float.MIN_VALUE) {
            return; // no valid intensities found
        }
        brightnessModel.setMinimum(min / 65535f);
        brightnessModel.setMaximum(max / 65535f);
        brightnessModel.notifyObservers();
    }

    private void setupMouseNavigation() {
        // 1) Delegate tracing interaction to customized class
        tracingInteractor = new TracingInteractor(this);

        // 2) Setup 3D viewer mouse interaction
        interactor = new OrbitPanZoomInteractor(
                sceneWindow.getCamera(),
                sceneWindow.getInnerComponent()) {

                    // Show/hide crosshair on enter/exit
                    @Override
                    public void mouseEntered(MouseEvent event) {
                        super.mouseEntered(event);
                        crossHairActor.setVisible(true);
                        sceneWindow.getGLAutoDrawable().display();
                    }
                    @Override
                    public void mouseExited(MouseEvent event) {
                        super.mouseExited(event);
                        crossHairActor.setVisible(false);
                        sceneWindow.getGLAutoDrawable().display();
                    }
                    
                    // Click to center on position
                    @Override
                    public void mouseClicked(MouseEvent event) 
                    {
                        // Click to center on position
                        if ((event.getClickCount() == 1) && (event.getButton() == MouseEvent.BUTTON1)) {
                            if (System.nanoTime() < (previousClickTime + minClickInterval)) {
                                return;
                            }

                            // Use neuron cursor position, if available, rather than hardware mouse position.
                            Vector3 xyz = null;
                            NeuriteAnchor hoverAnchor = tracingInteractor.getHoverLocation();
                            if (hoverAnchor == null) {
                                xyz = worldXyzForScreenXy(event.getPoint());
                            } else {
                                xyz = hoverAnchor.getLocationUm();
                                // System.out.println("Using neuron cursor XYZ "+xyz);
                            }

                            // System.out.println(xyz);
                            previousClickTime = System.nanoTime();
                            PerspectiveCamera pCam = (PerspectiveCamera) camera;
                            animateToFocusXyz(xyz, pCam.getVantage(), 150);
                        }
                    }

                    // Hover to show location in status bar
                    @Override
                    public void mouseMoved(MouseEvent event) {
                        super.mouseMoved(event);

                        // Print out screen X, Y (pixels)
                        StringBuilder msg = new StringBuilder();
                        final boolean showWindowCoords = false;
                        if (showWindowCoords) {
                            msg.append("Window position (pixels):");
                            msg.append(String.format("[% 4d", event.getX()));
                            msg.append(String.format(", % 4d", event.getY()));
                            msg.append("]");
                        }

                        reportIntensity(msg, event);

                        reportPickItem(msg, event);

                        if (msg.length() > 0) {
                            StatusDisplayer.getDefault().setStatusText(msg.toString());
                        }
                    }

                };

    }

    /**
     * Animates to next point in 3D space TODO - run this in another thread
     *
     * @param xyz
     * @param vantage
     */
    private void animateToFocusXyz(Vector3 xyz, Vantage vantage, int milliseconds) {
        Vector3 startPos = new Vector3(vantage.getFocusPosition());
        Vector3 endPos = new Vector3(xyz);
        long startTime = System.nanoTime();
        long targetTime = milliseconds * 1000000;
        final int stepCount = 40;
        boolean didMove = false;
        for (int s = 0; s < stepCount - 1; ++s) {
            // skip frames to match expected time
            float alpha = s / (float) (stepCount - 1);
            double deltaTime = (System.nanoTime() - startTime) / 1e6;
            double desiredTime = (alpha * targetTime) / 1e6;
            // System.out.println("Elapsed = "+deltaTime+" ms; desired = "+desiredTime+" ms");
            if (deltaTime > desiredTime) {
                continue; // skip this frame
            }
            Vector3 a = new Vector3(startPos).multiplyScalar(1.0f - alpha);
            Vector3 b = new Vector3(endPos).multiplyScalar(alpha);
            a = a.add(b);
            if (vantage.setFocusPosition(a)) {
                didMove = true;
                vantage.notifyObservers();
                sceneWindow.getGLAutoDrawable().display();
                // sceneWindow.getInnerComponent().repaint();
            }
        }
        double elapsed = (System.nanoTime() - startTime) * 1e-6;
        // System.out.println("Animation took " + elapsed + " ms");
        // never skip the final frame
        if (vantage.setFocusPosition(endPos)) {
            didMove = true;
        }
        if (didMove) {
            vantage.notifyObservers();
            sceneWindow.getGLAutoDrawable().display();
            // sceneWindow.getInnerComponent().repaint();
        }
    }

    private void animateToCameraRotation(Rotation rot, Vantage vantage, int milliseconds) {
        Quaternion startRot = vantage.getRotationInGround().convertRotationToQuaternion();
        Quaternion endRot = rot.convertRotationToQuaternion();
        long startTime = System.nanoTime();
        long totalTime = milliseconds * 1000000;
        final int stepCount = 40;
        boolean didMove = false;
        for (int s = 0; s < stepCount - 1; ++s) {
            // skip frames to match expected time
            float alpha = s / (float) (stepCount - 1);
            double expectedTime = startTime + alpha * totalTime;
            if ((long) expectedTime < System.nanoTime()) {
                continue; // skip this frame
            }
            Quaternion mid = startRot.slerp(endRot, alpha);
            if (vantage.setRotationInGround(new Rotation().setFromQuaternion(mid))) {
                didMove = true;
                vantage.notifyObservers();
                sceneWindow.getGLAutoDrawable().display();
            }
        }
        // never skip the final frame
        if (vantage.setRotationInGround(rot)) {
            didMove = true;
        }
        if (didMove) {
            vantage.notifyObservers();
            sceneWindow.getGLAutoDrawable().display();
        }
    }

    // Append a message about the item under the cursor
    private void reportPickItem(StringBuilder msg, MouseEvent event) {
        int itemId = neuronMPRenderer.pickIdForScreenXy(event.getPoint());
        msg.append("  Item index under cursor = " + itemId);
    }

    private void reportIntensity(StringBuilder msg, MouseEvent event) {
        // Use neuron cursor position, if available, rather than hardware mouse position.
        Vector3 worldXyz = null;
        int intensity = 0;
        NeuriteAnchor hoverAnchor = tracingInteractor.getHoverLocation();
        if (hoverAnchor != null) {
            worldXyz = hoverAnchor.getLocationUm();
            intensity = hoverAnchor.getIntensity();
        } else {
            PerspectiveCamera camera = (PerspectiveCamera) sceneWindow.getCamera();
            float relDepthF = depthOffsetForScreenXy(event.getPoint(), camera);
            worldXyz = worldXyzForScreenXy(event.getPoint(), camera, relDepthF);
            intensity = neuronMPRenderer.intensityForScreenXy(event.getPoint());
        }

        mouseStageLocation = worldXyz;
        msg.append(String.format("[% 7.1f, % 7.1f, % 7.1f] \u00B5m",
                worldXyz.get(0), worldXyz.get(1), worldXyz.get(2)));
        if (intensity > 0) {
            msg.append(String.format("  Intensity: % 5d", intensity));
        }
        // TODO - print out tile X, Y, Z (voxels)
        // TODO - print out tile identifier       
    }

    /*
    private int intensityForScreenXy(Point2D xy) {
        int result = neuronMPRenderer.valueForScreenXy(xy, GL3.GL_COLOR_ATTACHMENT0);
        if (result <= 0) {
            return -1;
        }
        return result;
    }

    /*
    private int pickIdForScreenXy(Point2D xy) {
        return valueForScreenXy(xy, GL3.GL_COLOR_ATTACHMENT1);
    }

    private int valueForScreenXy(Point2D xy, int glAttachment) {
        int result = -1;
        if (mprActor == null) {
            return result;
        }
        RenderTarget target = mprActor.getRenderTarget(glAttachment);
        if (target == null) {
            return result;
        }
        int intensity = target.getIntensity(
                sceneWindow.getGLAutoDrawable(),
                (int) Math.round(xy.getX()),
                // y convention is opposite between screen and texture buffer
                target.getHeight() - (int) Math.round(xy.getY()),
                0); // channel index
        return intensity;
    }
    */

    private float depthOffsetForScreenXy(Point2D xy, AbstractCamera camera) {
        float result = neuronMPRenderer.relativeDepthOffsetForScreenXy(xy, camera);
        if (volumeMipMaterial == null) {
            return result;
        }
        result *= 0.5f * volumeMipMaterial.getViewSlabThickness(camera);
        return result;
    }

    /**
     * TODO this could be a member of PerspectiveCamera
     *
     * @param xy in window pixels, as reported by MouseEvent.getPoint()
     * @param camera
     * @param depthOffset in scene units (NOT PIXELS)
     * @return
     */
    private Vector3 worldXyzForScreenXy(Point2D xy, PerspectiveCamera camera, float depthOffset) {
        // Camera frame coordinates
        float screenResolution
                = camera.getVantage().getSceneUnitsPerViewportHeight()
                / (float) camera.getViewport().getHeightPixels();
        float cx = 2.0f * ((float) xy.getX() / (float) camera.getViewport().getWidthPixels() - 0.5f);
        cx *= screenResolution * 0.5f * camera.getViewport().getWidthPixels();
        float cy = -2.0f * ((float) xy.getY() / (float) camera.getViewport().getHeightPixels() - 0.5f);
        cy *= screenResolution * 0.5f * camera.getViewport().getHeightPixels();

        // TODO Adjust cx, cy for foreshortening
        float screenDepth = camera.getCameraFocusDistance();
        float itemDepth = screenDepth + depthOffset;
        float foreshortening = itemDepth / screenDepth;
        cx *= foreshortening;
        cy *= foreshortening;

        float cz = -itemDepth;
        Matrix4 modelViewMatrix = camera.getViewMatrix();
        Matrix4 camera_X_world = modelViewMatrix.inverse(); // TODO - cache this invers
        Vector4 worldXyz = camera_X_world.multiply(new Vector4(cx, cy, cz, 1));
        return new Vector3(worldXyz.get(0), worldXyz.get(1), worldXyz.get(2));
    }

    private final BrightnessModel brightnessModel = new BrightnessModel();

    private void initialize3DViewer() {

        // Insert 3D viewer component
        Vantage vantage = new Vantage(null);
        vantage.setUpInWorld(new Vector3(0, 0, -1));
        vantage.setConstrainedToUpDirection(true);
        // vantage.setSceneUnitsPerViewportHeight(100); // TODO - resize to fit object

        // We want camera change events to register in volume Viewer BEFORE
        // they do in SceneWindow. So Create volume viewer first.
        vantage.addObserver(new Observer() {
            @Override
            public void update(Observable o, Object arg) {
                neuronMPRenderer.setIntensityBufferDirty();
                /*
                if (mprActor == null) {
                    return;
                }
                neuronMPRenderer.setIntensityBufferDirty();
                 */
            }
        });
        brightnessModel.addObserver(new Observer() {
            @Override
            public void update(Observable o, Object arg) {
                neuronMPRenderer.setIntensityBufferDirty();
                /*
                if (mprActor == null) {
                    return;
                }
                neuronMPRenderer.setIntensityBufferDirty();
                */
            }
        });

        this.setLayout(new BorderLayout());
        sceneWindow = new SceneWindow(vantage, CameraType.PERSPECTIVE);

       // associateLookup(Lookups.singleton(vantage)); // ONE item in lookup
        // associateLookup(Lookups.fixed(vantage, brightnessModel)); // TWO items in lookup
        FrameTracker frameTracker = sceneWindow.getRenderer().getFrameTracker();
        associateLookup(Lookups.fixed(
                vantage, 
                brightnessModel, 
                frameTracker));

        // Tooltips cannot be used, because there might be a AWT Component inside..
        // sceneWindow.getOuterComponent().setToolTipText("Tool tip test...");
        // sceneWindow.getRenderer().setAutoSrgb(false); // TODO sRGB correctness...
        // Draw scale bar...
        // TODO - TextRenderer does not work with GL3
        /*
         sceneWindow.getGLAutoDrawable().addGLEventListener(new GLEventListener() {
         TextRenderer renderer;
            
         @Override
         public void init(GLAutoDrawable glad) {
         renderer = new TextRenderer(new Font("SansSerif", Font.BOLD, 36));
         }

         @Override
         public void dispose(GLAutoDrawable glad) {}

         @Override
         public void display(GLAutoDrawable drawable) {
         renderer.beginRendering(drawable.getWidth(), drawable.getHeight());
         // optionally set the color
         renderer.setColor(1.0f, 0.2f, 0.2f, 0.8f);
         renderer.draw("Text to draw", 100, 100);
         // ... more draw commands, color changes, etc.
         renderer.endRendering();
         }

         @Override
         public void reshape(GLAutoDrawable glad, int i, int i1, int i2, int i3) {}
         });
         */
        // reduce near clipping of volume block surfaces
        Viewport vp = sceneWindow.getCamera().getViewport();
        vp.setzNearRelative(0.50f);
        vp.setzFarRelative(50.0f); // We use rear faces for volume rendering now...

        sceneWindow.setBackgroundColor(Color.DARK_GRAY);
        this.add(sceneWindow.getOuterComponent(), BorderLayout.CENTER);

    }

    private void setupDragAndDropYml() {
        // Allow user to drop tilebase.cache.yml on this window
        setDropTarget(new DropTarget(this, new DropTargetListener() {

            boolean isDropSourceGood(DropTargetDropEvent event) {
                if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    return false;
                }
                return true;
            }

            boolean isDropSourceGood(DropTargetDragEvent event) {
                if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    return false;
                }
                return true;
            }

            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
            }

            @Override
            public void dropActionChanged(DropTargetDragEvent dtde) {
            }

            @Override
            public void dragExit(DropTargetEvent dte) {
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                if (!isDropSourceGood(dtde)) {
                    dtde.rejectDrop();
                    return;
                }
                dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
                Transferable t = dtde.getTransferable();
                try {
                    List<File> fileList = (List) t.getTransferData(DataFlavor.javaFileListFlavor);
                    for (File f : fileList) {
                        loadYamlFile(f);
                    }
                    System.out.println("Yaml files loaded!");
                } catch (UnsupportedFlavorException | IOException ex) {
                    JOptionPane.showMessageDialog(NeuronTracerTopComponent.this, "Error loading yaml file");
                    Exceptions.printStackTrace(ex);
                }
            }
        }));
    }

    /**
     * Helper method toward automatic tile loading
     */
    private BrickInfo loadTileAtCurrentFocus() throws IOException {
        PerformanceTimer timer = new PerformanceTimer();

        PerspectiveCamera pCam = (PerspectiveCamera) sceneWindow.getCamera();

        // 2 - Load the brick at the center...
        // TODO - should happen automatically
        // Find the best resolution available
        double screenPixelResolution = pCam.getVantage().getSceneUnitsPerViewportHeight()
                / pCam.getViewport().getHeightPixels();
        double minDist = Double.MAX_VALUE;
        Double bestRes = null;
        for (Double res : volumeSource.getAvailableResolutions()) {
            double dist = Math.abs(Math.log(res) - Math.log(screenPixelResolution));
            if (dist < minDist) {
                bestRes = res;
                minDist = dist;
            }
        }
        Double brickResolution = bestRes;
        BrickInfoSet brickInfoSet = volumeSource.getAllBrickInfoForResolution(brickResolution);
        BrickInfo brickInfo = brickInfoSet.getBestContainingBrick(pCam.getVantage().getFocusPosition());

        ProgressHandle progress
                = ProgressHandleFactory.createHandle(
                "Loading Tiff Volume...");
        progress.start();

        GL3Actor boxMesh = createBrickActor((BrainTileInfo) brickInfo);

        progress.finish();

        StatusDisplayer.getDefault().setStatusText(
                "One TIFF file loaded and processed in "
                + String.format("%1$,.2f", timer.reportMsAndRestart() / 1000.0)
                + " seconds."
        );

        // mprActor.addChild(boxMesh);
        neuronMPRenderer.addVolumeActor(boxMesh);
        
        return brickInfo;
    }
    
    private void setupContextMenu(Component innerComponent) {
        // Context menu for window - at first just to see if it works with OpenGL
        // (A: YES, if applied to the inner component)
        innerComponent.addMouseListener(new MouseUtils.PopupMouseAdapter() {
            private JPopupMenu createMenu() {
                JPopupMenu menu = new JPopupMenu();

                // Setting popup menu title here instead of in JPopupMenu constructor,
                // because title from constructor is not shown in default look and feel.
                menu.add("Options:").setEnabled(false); // TODO should I place title in constructor?
                menu.add(new JPopupMenu.Separator());

                if (mouseStageLocation != null) {
                    // Recenter
                    menu.add(new AbstractAction("Recenter on This 3D Position [left-click]") {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            PerspectiveCamera pCam = (PerspectiveCamera) sceneWindow.getCamera();
                            animateToFocusXyz(mouseStageLocation, pCam.getVantage(), 150);
                        }
                    });
                }

                menu.add(new AbstractAction("Reset Rotation") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        Vantage v = sceneWindow.getVantage();
                        animateToCameraRotation(
                                v.getDefaultRotation(),
                                v, 150);
                    }
                });

                if (tracingInteractor.getTracingMode() != TracingInteractor.TracingMode.TRACING) {
                    if (mouseStageLocation != null) {
                        // TODO - actually build a neuron model
                        // TODO - check whether real 3D volume information is here
                        Action action = new AbstractAction("Reconstruct this neurite [SHIFT-click]") {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                if (tracingInteractor.getHoverLocation() == null) {
                                    return;
                                }
                                tracingInteractor.setTracingModeOn();
                                // System.out.println("Ha ha. Just kidding. TODO");
                            }
                        };
                        // action.setEnabled(false); // Until I implement it...
                        menu.add(action);
                    }
                } else {
                    Action action = new AbstractAction("Stop reconstructing this neurite [ESC]") {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            tracingInteractor.setTracingModeOff();
                        }
                    };
                    // action.setEnabled(false); // Until I implement it...
                    menu.add(action);
                }

                menu.add(new JPopupMenu.Separator());

                menu.add(new AbstractAction("Auto contrast") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        autoContrast();
                    }
                });

                menu.add(new AbstractAction("Save screen shot...") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        GLAutoDrawable glad = sceneWindow.getGLAutoDrawable();
                        glad.getContext().makeCurrent();

                        // In Jogl 2.1.3, Screenshot is deprecated, but the non-deprecated method does not work. Idiots.
                        // BufferedImage image = Screenshot.readToBufferedImage(glad.getSurfaceWidth(), glad.getSurfaceHeight());
                        // In Jogl 2.2.4, this newer screenshot method seems to work OK
                        AWTGLReadBufferUtil rbu = new AWTGLReadBufferUtil(glad.getGLProfile(), false);
                        BufferedImage image = rbu.readPixelsToBufferedImage(glad.getGL(), true);

                        glad.getContext().release();
                        if (image == null) {
                            return;
                        }
                        FileDialog chooser = new FileDialog((Frame) null,
                                "Save Neuron Tracer Image",
                                FileDialog.SAVE);
                        chooser.setFile("*.png");
                        chooser.setVisible(true);
                        System.out.println("Screen shot file name = " + chooser.getFile());
                        if (chooser.getFile() == null) {
                            return;
                        }
                        if (chooser.getFile().isEmpty()) {
                            return;
                        }
                        File outputFile = new File(chooser.getDirectory(), chooser.getFile());
                        try {
                            ImageIO.write(image, "png", outputFile);
                        } catch (IOException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    }
                });

                menu.add(new AbstractAction("Load tile at current location")
                {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        Runnable task = new Runnable() {
                            public void run() {
                                try {
                                    BrickInfo centerBrickInfo = loadTileAtCurrentFocus();
                                    sceneWindow.getGLAutoDrawable().display();
                                } catch (IOException ex) {
                                    Exceptions.printStackTrace(ex);
                                }
                            }
                        };
                        RequestProcessor.getDefault().post(task);
                    }

                });
                
                // TODO - remove this temporary menu item I used to debug more general volume loading
                menu.add(new AbstractAction("Load Mouse Light YAML file and recenter...") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        // Ask user what file to open
                        JFileChooser fileChooser = new JFileChooser();
                        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                        fileChooser.setFileFilter(new FileNameExtensionFilter(
                                "Mouse Light Tile Index",
                                "yml", "yaml"));
                        fileChooser.setDialogTitle("Open Mouse Light tilebase index");
                        int openResult = fileChooser.showOpenDialog(
                                NeuronTracerTopComponent.this);
                        if (openResult != JFileChooser.APPROVE_OPTION) {
                            return;
                        }
                        final File yamlFile = fileChooser.getSelectedFile();
                        if (!yamlFile.exists()) {
                            JOptionPane.showMessageDialog(
                                    NeuronTracerTopComponent.this,
                                    "ERROR: No such file " + yamlFile.getAbsolutePath(),
                                    "ERROR: File not found.",
                                    JOptionPane.ERROR_MESSAGE);
                            return;
                        }

                        Runnable task = new Runnable() {
                            public void run() {
                                ProgressHandle progress
                                        = ProgressHandleFactory.createHandle("Loading brain tile metadata");
                                progress.start();
                                progress.progress("Loading YAML tile information...");

                                PerformanceTimer timer = new PerformanceTimer();

                                InputStream yamlStream;
                                try {
                                    // 1 - Load tile index
                                    yamlStream = new FileInputStream(yamlFile);
                                    volumeSource = new MouseLightYamlBrickSource(yamlStream);
                                    int tileCount = 0;
                                    for (Double res : volumeSource.getAvailableResolutions()) {
                                        BrickInfoSet brickInfoSet = volumeSource.getAllBrickInfoForResolution(res);
                                        tileCount += brickInfoSet.size();
                                    }
                                    progress.finish();
                                    StatusDisplayer.getDefault().setStatusText(
                                            tileCount
                                            + " tiles loaded in "
                                            + String.format("%1$,.2f", timer.reportMsAndRestart() / 1000.0)
                                            + " seconds."
                                    );
                                    // System.out.println("yaml load took " + timer.reportMsAndRestart() + " ms");

                                    // Recenter
                                    Vector3 centerFocus = volumeSource.getBoundingBox().getCentroid();
                                    // System.out.println("Center of volume is " + centerFocus.toString());
                                    PerspectiveCamera pCam = (PerspectiveCamera) sceneWindow.getCamera();
                                    animateToFocusXyz(centerFocus, pCam.getVantage(), 150);

                                    BrickInfo centerBrickInfo = loadTileAtCurrentFocus();
                                    
                                    Vantage v = pCam.getVantage();
                                    v.centerOn(centerBrickInfo.getBoundingBox());
                                    v.setDefaultBoundingBox(centerBrickInfo.getBoundingBox());
                                    v.notifyObservers();

                                } catch (FileNotFoundException ex) {
                                    JOptionPane.showMessageDialog(
                                            NeuronTracerTopComponent.this,
                                            "ERROR loading YAML file "
                                            + yamlFile.getAbsolutePath(),
                                            "ERROR: YAML load error",
                                            JOptionPane.ERROR_MESSAGE);
                                } catch (IOException ex) {
                                    JOptionPane.showMessageDialog(
                                            NeuronTracerTopComponent.this,
                                            "ERROR loading Volume file",
                                            "ERROR: Volume load error",
                                            JOptionPane.ERROR_MESSAGE);
                                } finally {
                                    progress.finish();
                                }
                            }
                        };
                        RequestProcessor.getDefault().post(task);
                    }
                });
                
                if (volumeMipMaterial != null) {
                    JMenu filterMenu = new JMenu("Rendering filter");
                    menu.add(filterMenu);

                    filterMenu.add(new JRadioButtonMenuItem(
                            new AbstractAction("Nearest-neighbor") 
                    {
                        {  
                            putValue(Action.SELECTED_KEY, 
                                volumeMipMaterial.getFilteringOrder() == 0);
                        }
                        
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            volumeMipMaterial.setFilteringOrder(0);
                            neuronMPRenderer.setIntensityBufferDirty();
                            sceneWindow.getGLAutoDrawable().display();
                        }
                    }));

                    filterMenu.add(new JRadioButtonMenuItem(
                            new AbstractAction("Trilinear") 
                    {
                        {  
                            putValue(Action.SELECTED_KEY, 
                                volumeMipMaterial.getFilteringOrder() == 1);
                        }
                        
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            volumeMipMaterial.setFilteringOrder(1);
                            neuronMPRenderer.setIntensityBufferDirty();
                            sceneWindow.getGLAutoDrawable().display();
                        }
                    }));

                    filterMenu.add(new JRadioButtonMenuItem(
                            new AbstractAction("Tricubic") {
                        {  
                            putValue(Action.SELECTED_KEY, 
                                volumeMipMaterial.getFilteringOrder() == 3);
                        }
                        
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            volumeMipMaterial.setFilteringOrder(3);
                            neuronMPRenderer.setIntensityBufferDirty();
                            sceneWindow.getGLAutoDrawable().display();
                        }
                    }));
                    
                    filterMenu = new JMenu("Projection");
                    menu.add(filterMenu);
                    
                    filterMenu.add(new JRadioButtonMenuItem(
                            new AbstractAction("Maximum Intensity") 
                    {
                        {  
                            putValue(Action.SELECTED_KEY, 
                                volumeMipMaterial.getProjectionMode() == 0);
                        }
                        
                        @Override
                        public void actionPerformed(ActionEvent e) 
                        {
                            volumeMipMaterial.setProjectionMode(0);
                            neuronMPRenderer.setIntensityBufferDirty();
                            sceneWindow.getGLAutoDrawable().display();
                        }
                    }));
                
                    
                    filterMenu.add(new JRadioButtonMenuItem(
                            new AbstractAction("Occluding") 
                    {
                        {  
                            putValue(Action.SELECTED_KEY, 
                                volumeMipMaterial.getProjectionMode() == 1);
                        }
                        
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            volumeMipMaterial.setProjectionMode(1);
                            neuronMPRenderer.setIntensityBufferDirty();
                            sceneWindow.getGLAutoDrawable().display();
                        }
                    }));
                    
                    filterMenu.add(new JRadioButtonMenuItem(
                            new AbstractAction("Isosurface") 
                    {
                        {  
                            putValue(Action.SELECTED_KEY, 
                                volumeMipMaterial.getProjectionMode() == 2);
                        }
                        
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            volumeMipMaterial.setProjectionMode(2);
                            neuronMPRenderer.setIntensityBufferDirty();
                            sceneWindow.getGLAutoDrawable().display();
                        }
                    }));
                                        
                }
                
                if (sceneWindow != null) {
                    JMenu stereoMenu = new JMenu("Stereo3D");
                    menu.add(stereoMenu);

                    stereoMenu.add(new JRadioButtonMenuItem(
                            new AbstractAction("Monoscopic (Not 3D)") 
                    {
                        {  
                            putValue(Action.SELECTED_KEY, 
                                sceneWindow.getRenderer().getStereo3dMode() 
                                        == SceneRenderer.Stereo3dMode.MONO);
                        }
                        
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            sceneWindow.getRenderer().setStereo3dMode(
                                    SceneRenderer.Stereo3dMode.MONO);
                            neuronMPRenderer.setIntensityBufferDirty();
                            sceneWindow.getGLAutoDrawable().display();
                        }
                    }));
                    
                    stereoMenu.add(new JRadioButtonMenuItem(
                            new AbstractAction("Red/Cyan Anaglyph") 
                    {
                        {  
                            putValue(Action.SELECTED_KEY, 
                                sceneWindow.getRenderer().getStereo3dMode() 
                                        == SceneRenderer.Stereo3dMode.RED_CYAN);
                        }
                        
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            sceneWindow.getRenderer().setStereo3dMode(
                                    SceneRenderer.Stereo3dMode.RED_CYAN);
                            neuronMPRenderer.setIntensityBufferDirty();
                            sceneWindow.getGLAutoDrawable().display();
                        }
                    }));
                    
                    stereoMenu.add(new JRadioButtonMenuItem(
                            new AbstractAction("Green/Magenta Anaglyph") 
                    {
                        {  
                            putValue(Action.SELECTED_KEY, 
                                sceneWindow.getRenderer().getStereo3dMode() 
                                        == SceneRenderer.Stereo3dMode.GREEN_MAGENTA);
                        }
                        
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            sceneWindow.getRenderer().setStereo3dMode(
                                    SceneRenderer.Stereo3dMode.GREEN_MAGENTA);
                            neuronMPRenderer.setIntensityBufferDirty();
                            sceneWindow.getGLAutoDrawable().display();
                        }
                    }));
                    
                }
                
                JCheckBoxMenuItem cubeDistortMenu = new JCheckBoxMenuItem("Compress voxels in Z", doCubifyVoxels);
                menu.add(cubeDistortMenu);
                cubeDistortMenu.addActionListener(new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        JCheckBoxMenuItem item = (JCheckBoxMenuItem)e.getSource();
                        if (doCubifyVoxels) {
                            setCubifyVoxels(false);
                        }
                        else {
                            setCubifyVoxels(true);
                        }
                        item.setSelected(doCubifyVoxels);
                    }
                });
                
                // Synchronize with LVV
                // TODO - is LVV present?
                menu.add(new JPopupMenu.Separator());
                JMenu synchronizeMenu = new JMenu("Synchronize with Large Volume Viewer");
                menu.add(synchronizeMenu);
                synchronizeMenu.add(new AbstractAction("Synchronize now") {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                    }
                });
                synchronizeMenu.add(new AbstractAction("Synchronize always") {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                    }
                });
                synchronizeMenu.add(new AbstractAction("Desynchronize") {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                    }
                });
                
                // Cancel/do nothing action
                menu.add(new JPopupMenu.Separator());
                menu.add(new AbstractAction("Close this menu [ESC]") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                    }
                });

                return menu;
            }

            @Override
            protected void showPopup(MouseEvent event) {
                if (!NeuronTracerTopComponent.this.isShowing()) {
                    return;
                }
                // System.out.println("showPopup");
                createMenu().show(NeuronTracerTopComponent.this, event.getPoint().x, event.getPoint().y);
            }
        });
    }

    public void loadYamlFile(File f) throws IOException {
        final File file = f;
        Runnable task = new Runnable() {
            public void run() {
                ProgressHandle progress = ProgressHandleFactory.createHandle("Loading brain tiles");
                progress.start();
                progress.progress("Loading YAML tile information...");

                PerformanceTimer timer = new PerformanceTimer();
                BrainTileInfoList tileList = new BrainTileInfoList();
                try {
                    tileList.loadYamlFile(file);
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
                progress.progress("YAML tile information loaded");
                System.out.println("yaml load took " + timer.reportMsAndRestart() + " ms");
                loadExampleTile(tileList, progress); // TODO remove this testing hack
                progress.progress("Example tile loaded");

                progress.finish();
            }
        };
        RequestProcessor.getDefault().post(task);
    }

    private GL3Actor createBrickActor(BrainTileInfo brainTile) throws IOException 
    {
        Texture3d texture = brainTile.loadBrick(10);

        // System.out.println("tiff load to texture took "+elapsedTimeMs+" ms");
        volumeMipMaterial
                = // new TexCoordMaterial();
                // new VolumeSurfaceMaterial(texture);
                new VolumeMipMaterial(texture, brightnessModel);
        // texture.setMagFilter(GL3.GL_NEAREST); // TODO for demo only
        MeshGeometry boxGeometry = new BrainTileMesh(brainTile);
        GL3Actor boxMesh = new MeshActor(
                boxGeometry, volumeMipMaterial, null);
        return boxMesh;
    }
    
    public boolean loadExampleTile(BrainTileInfoList tileList, ProgressHandle progress) {

        BrainTileInfo exampleTile = null;
        // Find first existing tile
        for (BrainTileInfo tile : tileList) {
            if (tile.folderExists()) {
                exampleTile = tile;
                break;
            }
        }
        
        if (exampleTile == null) {
            return false;
        }

        File tileFile = new File(exampleTile.getParentPath(), exampleTile.getLocalPath());
        System.out.println(tileFile);

        progress.progress("Loading tile file " + tileFile);
        try {
            GL3Actor brickActor = createBrickActor(exampleTile);
            // mprActor.addChild(brickActor);
            neuronMPRenderer.addVolumeActor(brickActor);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }

        // progress.finish();
        
        for (NeuriteActor tracingActor : tracingInteractor.createActors()) {
            sceneWindow.getRenderer().addActor(tracingActor);
            tracingActor.getModel().addObserver(new Observer() {
                @Override
                public void update(Observable o, Object arg) {
                    sceneWindow.getInnerComponent().repaint();
                }
            });
        }

        Vantage v = sceneWindow.getVantage();
        v.centerOn(exampleTile.getBoundingBox());
        v.setDefaultBoundingBox(exampleTile.getBoundingBox());
        v.notifyObservers();

        return true;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
    @Override
    public void componentOpened() {
        // TODO add custom code on component opening
    }

    @Override
    public void componentClosed() {
        // TODO add custom code on component closing
    }

    void writeProperties(java.util.Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");
        // TODO store your settings
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
        // TODO read your settings according to their version
    }

    // VolumeProjection implementation below:
    @Override
    public Component getMouseableComponent() {
        return sceneWindow.getInnerComponent();
    }

    @Override
    public int getIntensity(Point2D xy) {
        return neuronMPRenderer.intensityForScreenXy(xy);
    }

    @Override
    public Vector3 worldXyzForScreenXy(Point2D xy) {
        PerspectiveCamera pCam = (PerspectiveCamera) sceneWindow.getCamera();
        float depthOffset = depthOffsetForScreenXy(xy, pCam);
        Vector3 xyz = worldXyzForScreenXy(
                xy,
                pCam,
                depthOffset);
        return xyz;
    }

    @Override
    public float getPixelsPerSceneUnit() {
        Vantage vantage = sceneWindow.getVantage();
        Viewport viewport = sceneWindow.getCamera().getViewport();
        return viewport.getHeightPixels() / vantage.getSceneUnitsPerViewportHeight();
    }
    
    public boolean setCubifyVoxels(boolean cubify) {
        if (cubify == doCubifyVoxels)
            return false; // no change
        doCubifyVoxels = cubify;
        // TODO - actually cubify
        Vantage v = sceneWindow.getVantage();
        if (doCubifyVoxels) {
            v.setWorldScaleHack(1, 1, 0.4f);
            System.out.println("distort");
        }
        else {
            v.setWorldScaleHack(1, 1, 1);
            System.out.println("undistort");
        }
        v.notifyObservers();
        sceneWindow.getGLAutoDrawable().display();
        
        return true;
    }
}
