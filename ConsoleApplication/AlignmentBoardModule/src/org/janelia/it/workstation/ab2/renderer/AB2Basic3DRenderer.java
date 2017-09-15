package org.janelia.it.workstation.ab2.renderer;

import java.awt.Point;
import java.util.Deque;

import javax.media.opengl.GL4;
import javax.media.opengl.GLAutoDrawable;

import org.janelia.geometry3d.BasicObject3D;
import org.janelia.geometry3d.Matrix4;
import org.janelia.geometry3d.PerspectiveCamera;
import org.janelia.geometry3d.Vantage;
import org.janelia.geometry3d.Vector3;
import org.janelia.geometry3d.Vector4;

import org.janelia.geometry3d.Viewport;
import org.janelia.it.jacs.shared.geom.Rotation3d;
import org.janelia.it.jacs.shared.geom.UnitVec3;
import org.janelia.it.jacs.shared.geom.Vec3;

import org.janelia.it.workstation.ab2.gl.GLDisplayUpdateCallback;
import org.janelia.it.workstation.ab2.gl.GLShaderActionSequence;
import org.janelia.it.workstation.ab2.gl.GLShaderProgram;
import org.janelia.it.workstation.ab2.shader.AB2Basic3DShader;


public class AB2Basic3DRenderer extends AB23DRenderer {

    protected PerspectiveCamera camera;
    protected Vantage vantage;
    protected Viewport viewport;

    public static final double DEFAULT_CAMERA_FOCUS_DISTANCE = 2.0;

    Vector4 backgroundColor=new Vector4(0.0f, 0.0f, 0.0f, 0.0f);

    //int blend_method=0; // 0=transparency, 1=mip

    //public static final int BLEND_METHOD_MIX=0;
    //public static final int BLEND_METHOD_MIP=1;

    //public static final double DISTANCE_TO_SCREEN_IN_PIXELS = 2000;
    //private static final double MAX_CAMERA_FOCUS_DISTANCE = 1000000.0;
    //private static final double MIN_CAMERA_FOCUS_DISTANCE = 0.001;
    //private static final Vector3 UP_IN_CAMERA = new Vector3(0,1,0);

    //private static double FOV_Y_DEGREES = 45.0f;
    //private float FOV_TERM = new Float(Math.tan( (Math.PI/180.0) * (FOV_Y_DEGREES/2.0) ) );

    // camera parameters
    private boolean resetFirstRedraw;
    private boolean hasBeenReset = false;

    Matrix4 mvp;

    GLShaderActionSequence shaderActionSequence=new GLShaderActionSequence(AB2Basic3DRenderer.class.getName());
    final AB2Basic3DShader shader;

    public AB2Basic3DRenderer() {
        shader=new AB2Basic3DShader();
        vantage=new Vantage(null);
        viewport=new Viewport();
        camera = new PerspectiveCamera(vantage, viewport);
        vantage.setFocus(0.0f,0.0f,(float)DEFAULT_CAMERA_FOCUS_DISTANCE);
    }

//    public void setPixelDimensions(int widthInPixels, int heightInPixels) {
//        viewport.setWidthPixels(widthInPixels);
//        viewport.setHeightPixels(heightInPixels);
//    }
//
//    public void centerOnPixel(Point p) {
//        double dx =  p.x - viewport.getWidthPixels()/2.0;
//        double dy = viewport.getHeightPixels()/2.0 - p.y;
//        translatePixels(-dx, dy, 0.0);
//    }

    @Override
    public void init(GLAutoDrawable glAutoDrawable) {
        initSync(glAutoDrawable);
    }

    private synchronized void initSync(GLAutoDrawable glDrawable) {
        final GL4 gl = glDrawable.getGL().getGL4();

        // need to create and load shader here

        try {
            shader.setUpdateCallback(new GLDisplayUpdateCallback() {
                @Override
                public void update(GL4 gl) {
                    shader.setMVP(gl, mvp);
                }
            });
            shaderActionSequence.setShader(shader);
            shaderActionSequence.setApplyMemoryBarrier(false);
            shaderActionSequence.init(gl);

            // todo: init model actors?

        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    @Override
    public void dispose(GLAutoDrawable glAutoDrawable) {
        final GL4 gl = glAutoDrawable.getGL().getGL4();
        shaderActionSequence.dispose(gl);
    }

    @Override
    public void display(GLAutoDrawable glAutoDrawable) {
        displaySync(glAutoDrawable);
    }


    private synchronized void displaySync(GLAutoDrawable glDrawable) {
        final GL4 gl = glDrawable.getGL().getGL4();

        gl.glClear(GL4.GL_DEPTH_BUFFER_BIT);
        gl.glEnable(GL4.GL_DEPTH_TEST);

        Matrix4 projectionMatrix=camera.getProjectionMatrix();
        Matrix4 viewMatrix=camera.getViewMatrix();

        mvp=projectionMatrix.multiply(viewMatrix);

        shader.setMVP(gl, mvp);

        shaderActionSequence.display(gl);
    }

//    public double glUnitsPerPixel() {
//        return Math.abs( model.getCameraFocusDistance() ) / DISTANCE_TO_SCREEN_IN_PIXELS;
//    }
//
//    public void resetView() {
//        camera.resetRotation();
//        camera.setFocus(0.0, 0.0, 0.5);
//        model.setCameraDepth(new Vec3(0.0, 0.0, VoxelViewerModel.DEFAULT_CAMERA_FOCUS_DISTANCE));
//    }

    @Override
    public void reshape(GLAutoDrawable glDrawable, int x, int y, int width, int height) {
        viewport.setWidthPixels(width);
        viewport.setHeightPixels(height);
        display(glDrawable);
    }


    public void rotatePixels(double dx, double dy, double dz) {
        // Rotate like a trackball
        double dragDistance = Math.sqrt(dy * dy + dx * dx + dz * dz);
        if (dragDistance <= 0.0)
            return;
        UnitVec3 rotationAxis = new UnitVec3(dy, dx, dz);
        double windowSize = Math.sqrt(
                widthInPixels * widthInPixels
                        + heightInPixels * heightInPixels);
        // Drag across the entire window to rotate all the way around
        double rotationAngle = 2.0 * Math.PI * dragDistance/windowSize;
        // System.out.println(rotationAxis.toString() + rotationAngle);
        Rotation3d rotation = new Rotation3d().setFromAngleAboutUnitVector(
                rotationAngle, rotationAxis);
        // System.out.println(rotation);
        camera.setRotation(camera.getRotation().times(rotation.transpose()));
        // System.out.println(R_ground_camera);
    }

    public void translatePixels(double dx, double dy, double dz) {
        // trackball translate
        Vec3 t = new Vec3(-dx, dy, -dz);
        t.multEquals(glUnitsPerPixel());
        model.getCamera3d().getFocus().plusEquals(
                camera.getRotation().times(t)
        );
    }

//    public void updateProjection(GL4 gl) {
//        //gl.glViewport(0, 0, (int) widthInPixels, (int) heightInPixels);
//
//        //logger.info("updateProjection() using widthInPixels="+widthInPixels+" heightInPixels="+heightInPixels);
//
//        final float h = (float) widthInPixels / (float) heightInPixels;
//        double cameraFocusDistance = model.getCameraFocusDistance();
//        float scaledFocusDistance = new Float(Math.abs(cameraFocusDistance));
//        //projectionMatrix = computeProjection(h, 0.5f*scaledFocusDistance, 2.0f*scaledFocusDistance);
//        projectionMatrix = computeProjection(h, 0.01f*scaledFocusDistance, 2.0f*scaledFocusDistance);
//    }
//
//    Matrix4 computeProjection(float aspectRatio, float near, float far) {
//        Matrix4 projection = new Matrix4();
//        float top=near*FOV_TERM;
//        float bottom = -1f * top;
//        float right = top * aspectRatio;
//        float left = -1f * right;
//
//        projection.set(
//
//                (2f * near) / (right - left),     0f,                             (right + left) / (right - left),         0f,
//                0f,                               (2f * near) / (top - bottom),   (top + bottom) / (top - bottom),         0f,
//                0f,                               0f,                             -1f * ((far + near) / (far - near)),    -1f * ((2f * far * near) / (far - near)),
//                0f,                               0f,                             -1f,                                     0f);
//        projection.transpose();
//
//        return projection;
//    }

    public void zoom(double zoomRatio) {
        if (zoomRatio <= 0.0) {
            return;
        }
        if (zoomRatio == 1.0) {
            return;
        }

        double cameraFocusDistance = model.getCameraFocusDistance();
        cameraFocusDistance /= zoomRatio;
        if ( cameraFocusDistance > MAX_CAMERA_FOCUS_DISTANCE ) {
            return;
        }
        if ( cameraFocusDistance < MIN_CAMERA_FOCUS_DISTANCE ) {
            return;
        }
        model.setCameraPixelsPerSceneUnit(DISTANCE_TO_SCREEN_IN_PIXELS, cameraFocusDistance);

        model.setCameraDepth(new Vec3(0.0, 0.0, cameraFocusDistance));

//        logger.info("ZOOM  glUnitsPerPixel=" + glUnitsPerPixel() + " cameraFocusDistance=" + cameraFocusDistance);

    }

    public void zoomPixels(Point newPoint, Point oldPoint) {
        // Are we dragging away from the center, or toward the center?
        double[] p0 = {oldPoint.x - widthInPixels/2.0,
                oldPoint.y - heightInPixels/2.0};
        double[] p1 = {newPoint.x - widthInPixels/2.0,
                newPoint.y - heightInPixels/2.0};
        double dC0 = Math.sqrt(p0[0] * p0[0] + p0[1] * p0[1]);
        double dC1 = Math.sqrt(p1[0]*p1[0]+p1[1]*p1[1]);
        double dC = dC1 - dC0; // positive means away
        double denom = Math.max(20.0, dC1);
        double zoomRatio = 1.0 + dC/denom;
        zoom(zoomRatio);
    }

//    public VoxelViewerModel getModel() {
//        return model;
//    }
//
//
//    public void setResetFirstRedraw(boolean resetFirstRedraw) {
//        this.resetFirstRedraw = resetFirstRedraw;
//    }
//
//    // c = camera position
//    // f = focus point
//    // u = up vector
//    protected Matrix4 lookAt(Vector3 c, Vector3 f, Vector3 u) {
//        Vector3 forward = new Vector3(f.getX() - c.getX(), f.getY() - c.getY(), f.getZ() - c.getZ());
//        Vector3 fn=forward.normalize();
//        Vector3 side=computeNormalOfPlane(fn, u);
//        Vector3 sn=side.normalize();
//        Vector3 up=computeNormalOfPlane(sn, fn);
//        Matrix4 lam = new Matrix4();
//
//        lam.set( sn.getX(),   up.getX(),    -1f*fn.getX(),   0.0f,
//                sn.getY(),   up.getY(),    -1f*fn.getY(),   0.0f,
//                sn.getZ(),   up.getZ(),    -1f*fn.getZ(),   0.0f,
//                0.0f,         0.0f,        0.0f,        1.0f);
//
//        Matrix4 tm = new Matrix4();
//
//        tm.set( 1f, 0f, 0f, 0f,
//                0f, 1f, 0f, 0f,
//                0f, 0f, 1f, 0f,
//                -1f*c.getX(), -1f*c.getY(), -1f*c.getZ(), 1f);
//
//        Matrix4 result = tm.multiply(lam);
//        return result;
//    }
//
//    protected Vector3 computeNormalOfPlane(Vector3 v1, Vector3 v2) {
//        Vector3 norm=new Vector3(v1.getY() * v2.getZ() - v1.getZ() * v2.getY(),
//                v1.getZ() * v2.getX() - v1.getX() * v2.getZ(),
//                v1.getX() * v2.getY() - v1.getY() * v2.getX());
//        return norm;
//    }


}
