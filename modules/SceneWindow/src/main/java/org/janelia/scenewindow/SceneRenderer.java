
package org.janelia.scenewindow;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.media.opengl.DebugGL3;
import javax.media.opengl.GL3;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLEventListener;

import org.janelia.geometry3d.AbstractCamera;
import org.janelia.geometry3d.LateralOffsetCamera;
import org.janelia.geometry3d.OrthographicCamera;
import org.janelia.geometry3d.Vantage;
import org.janelia.geometry3d.Viewport;
import org.janelia.gltools.GL3Actor;
import org.janelia.gltools.GL3Resource;
import org.janelia.gltools.MultipassRenderer;
import org.janelia.scenewindow.fps.FrameTracker;
import org.janelia.scenewindow.stereo.AnaglyphRenderer;
import org.janelia.scenewindow.stereo.LeftEyeRenderer;
import org.janelia.scenewindow.stereo.MonoscopicRenderer;
import org.janelia.scenewindow.stereo.StereoRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author brunsc
 */
public class SceneRenderer implements GLEventListener {

    public enum CameraType {
        ORTHOGRAPHIC,
        PERSPECTIVE
    };
    
    public enum Stereo3dMode {
        MONO,
        LEFT,
        RIGHT,
        RED_CYAN,
        GREEN_MAGENTA,
    }
    
    private Stereo3dMode stereo3dMode = Stereo3dMode.MONO;
    
    private final AbstractCamera camera;
    private final List<GL3Actor> actors = new LinkedList<>();
    private final Set<GL3Resource> resources = new LinkedHashSet<>();
    
    private final List<MultipassRenderer> multipassRenderers = new ArrayList<>();
    
    // background color
    private final float[] bgColor = {1,1,1,1};
    private StereoRenderer stereoRenderer = new MonoscopicRenderer();
    private boolean doAutoSrgb = true;
    private final FrameTracker frameTracker = new FrameTracker();
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public SceneRenderer(Vantage vantage, Viewport viewport, CameraType cameraType) {
        switch(cameraType) {
            case ORTHOGRAPHIC:
                this.camera = new OrthographicCamera(vantage, viewport);
                break;
            case PERSPECTIVE:
            default:
                this.camera = new LateralOffsetCamera(vantage, viewport);
                break;
        }
    }
    
    public synchronized void addActor(GL3Actor actor) {
        actors.add(actor);
        resources.add(actor);
    }
    
    public List<MultipassRenderer> getMultipassRenderers()
    {
        return multipassRenderers;
    }
    
    @Override
    public void init(GLAutoDrawable glad) {
        GL3 gl = new DebugGL3(glad.getGL().getGL3());
        if (doAutoSrgb)
            gl.glEnable(GL3.GL_FRAMEBUFFER_SRGB);
        for(GL3Resource actor : resources)
            actor.init(gl);
        for(MultipassRenderer renderer : multipassRenderers)
            renderer.init(gl);
    }

    @Override
    public void dispose(GLAutoDrawable glad) {
        // System.out.println("dispose");
        GL3 gl = new DebugGL3(glad.getGL().getGL3());
        for(GL3Resource actor : resources)
            actor.dispose(gl);
        for(MultipassRenderer renderer : multipassRenderers)
            renderer.dispose(gl);
    }

    private long displayEndTime = System.nanoTime();

    @Override
    public void display(GLAutoDrawable glad) 
    {
        long displayStartTime = System.nanoTime();
        frameTracker.signalFrameBegin();
        
        GL3 gl = new DebugGL3(glad.getGL().getGL3());
        if (glad.getSurfaceWidth() < 1)
            return;
        if (glad.getSurfaceHeight() < 1)
            return;

        // Johan sometimes sees a crash here at glClearColor() Maybe this could sidestep it?
        // (or perhaps there was a previous error, and this is our first DebugGL3 since...)
        // This is a possible workaround, without understanding the problem.
        if (! glad.getContext().isCurrent()) {
            // Log this undesired condition
            logger.warn("Unexpectedly found invalid OpenGL context");
            return;
        }
        
        // Background
        // TODO - allow different background types
        gl.glClearColor(
                bgColor[0],
                bgColor[1],
                bgColor[2],
                bgColor[3]
        );
        
        // Background
        gl.glClear(GL3.GL_COLOR_BUFFER_BIT /* | GL3.GL_DEPTH_BUFFER_BIT */ );
        // Clearing color buffer causes tearing on Linux - but only if double buffering has not been correctly enabled
        // gl.glClear(GL3.GL_DEPTH_BUFFER_BIT);

        stereoRenderer.renderScene(glad, this, true);
        
        // Compute frame rate 
        long checkPointTime = System.nanoTime();
        long innerDisplayTime = checkPointTime - displayStartTime;
        long totalDisplayTime = checkPointTime - displayEndTime;
        // TODO use/display frame rate calculation
        displayEndTime = checkPointTime;
        frameTracker.signalFrameEnd();
    }

    public Stereo3dMode getStereo3dMode() {
        return stereo3dMode;
    }

    public void setStereo3dMode(Stereo3dMode stereo3dMode) {
        if (stereo3dMode == this.stereo3dMode) return;
        this.stereo3dMode = stereo3dMode;
        switch (stereo3dMode) { 
            case LEFT:
                stereoRenderer = new LeftEyeRenderer(120f);
                break;
            case RIGHT:
                stereoRenderer = new LeftEyeRenderer(-120f);
                break;
            case GREEN_MAGENTA:
                stereoRenderer = new AnaglyphRenderer(false, true, false);
                break;
            case RED_CYAN:
                stereoRenderer = new AnaglyphRenderer(true, false, false);
                break;
            case MONO:
            default:
                stereoRenderer = new MonoscopicRenderer();
                break;
        }
    }
    
    /**
     * Inner render pass, for use by stereoscopic renderers
     * @param gl
     * @param localCamera 
     */
    public synchronized void renderScene(GL3 gl, AbstractCamera localCamera) 
    {
        for (MultipassRenderer renderer : multipassRenderers)
            renderer.display(gl, localCamera);
        
        // Use depth buffer for opaque geometry
        gl.glEnable(GL3.GL_DEPTH_TEST);
        for(GL3Actor actor : actors) {
            if (actor.isVisible())
                actor.display(gl, localCamera, null);
        }
    }

    @Override
    public void reshape(GLAutoDrawable glad, int x, int y, int w, int h) {
        // TODO - modify for stereoscopic modes
        Viewport v = camera.getViewport();
        v.setOriginXPixels(x);
        v.setOriginYPixels(y);
        v.setWidthPixels(w);
        v.setHeightPixels(h);
        
        // GL3 gl = glad.getGL().getGL3();
        // gl.glViewport(v.getOriginXPixels(), v.getOriginYPixels(), v.getWidthPixels(), v.getHeightPixels()); // seems unnecessary
        
        v.getChangeObservable().notifyObservers();
        // System.out.println("SceneRenderer.reshape(): "+x+", "+y+", "+w+", "+h);
    }

    public AbstractCamera getCamera() {
        return camera;
    }

    public FrameTracker getFrameTracker()
    {
        return frameTracker;
    }

    public void setBackgroundColor(Color backgroundColor) {
        bgColor[0] = backgroundColor.getRed() / 255.0f;
        bgColor[1] = backgroundColor.getGreen() / 255.0f;
        bgColor[2] = backgroundColor.getBlue() / 255.0f;
        bgColor[3] = backgroundColor.getAlpha() / 255.0f;
    }

    public void setAutoSrgb(boolean doAutoSrgb) {
        this.doAutoSrgb = doAutoSrgb;
    }
}
