package org.janelia.it.workstation.ab2.actor;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Date;
import java.util.List;
import java.util.Random;

import javax.media.opengl.GL4;

import org.janelia.geometry3d.Vector3;
import org.janelia.it.workstation.ab2.gl.GLAbstractActor;
import org.janelia.it.workstation.ab2.model.AB2NeuronSkeleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PointSetActor extends GLAbstractActor {

    private final Logger logger = LoggerFactory.getLogger(PointSetActor.class);

    List<Vector3> points;

    IntBuffer vertexArrayId=IntBuffer.allocate(1);
    IntBuffer vertexBufferId=IntBuffer.allocate(1);

    FloatBuffer pointVertexFb;

    public PointSetActor(int actorId, List<Vector3> points) {
        this.actorId=actorId;
        this.points=points;
    }

    @Override
    public void init(GL4 gl) {

        float[] pointData=new float[points.size()*3];

        for (int i=0;i<points.size();i++) {
            Vector3 v=points.get(i);
            pointData[i*3]=v.getX();
            pointData[i*3+1]=v.getY();
            pointData[i*3+2]=v.getZ();
        }

        pointVertexFb= GLAbstractActor.createGLFloatBuffer(pointData);

        gl.glGenVertexArrays(1, vertexArrayId);
        checkGlError(gl, "i1 glGenVertexArrays error");

        gl.glBindVertexArray(vertexArrayId.get(0));
        checkGlError(gl, "i2 glBindVertexArray error");

        gl.glGenBuffers(1, vertexBufferId);
        checkGlError(gl, "i3 glGenBuffers() error");

        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, vertexBufferId.get(0));
        checkGlError(gl, "i4 glBindBuffer error");

        gl.glBufferData(GL4.GL_ARRAY_BUFFER, pointVertexFb.capacity() * 4, pointVertexFb, GL4.GL_STATIC_DRAW);
        checkGlError(gl, "i5 glBufferData error");

        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, 0);

    }

    @Override
    public void display(GL4 gl) {

        //logger.info("display() start");

        gl.glBindVertexArray(vertexArrayId.get(0));
        checkGlError(gl, "d1 glBindVertexArray() error");

        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, vertexBufferId.get(0));
        checkGlError(gl, "d2 glBindBuffer error");

        gl.glVertexAttribPointer(0, 3, GL4.GL_FLOAT, false, 0, 0);
        checkGlError(gl, "d3 glVertexAttribPointer 0 () error");

        gl.glEnableVertexAttribArray(0);
        checkGlError(gl, "d4 glEnableVertexAttribArray 0 () error");

        gl.glDrawArrays(GL4.GL_POINTS, 0, pointVertexFb.capacity()/3);
        checkGlError(gl, "d7 glDrawArrays() error");

        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, 0);

        //logger.info("display() end");

    }

    @Override
    public void dispose(GL4 gl) {
        gl.glDeleteVertexArrays(1, vertexArrayId);
        gl.glDeleteBuffers(1, vertexBufferId);
    }



}
