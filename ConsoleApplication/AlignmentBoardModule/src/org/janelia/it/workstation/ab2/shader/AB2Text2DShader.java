package org.janelia.it.workstation.ab2.shader;

import javax.media.opengl.GL4;

import org.janelia.geometry3d.Matrix4;
import org.janelia.geometry3d.Vector4;
import org.janelia.it.workstation.ab2.gl.GLShaderProgram;

public class AB2Text2DShader extends GLShaderProgram {

    @Override
    public String getVertexShaderResourceName() {
        return "AB2Text2DShader_vertex.glsl";
    }

    @Override
    public String getFragmentShaderResourceName() {
        return "AB2Text2DShader_fragment.glsl";
    }

    public void setMVP2d(GL4 gl, Matrix4 mvp) {
        setUniformMatrix4fv(gl, "mvp2d", false, mvp.asArray());
        checkGlError(gl, "AB2Text2DShader setMVP2d() error");
    }

    public void setForegroundColor(GL4 gl, Vector4 color0) {
        setUniform4v(gl, "colorForeground", 1, color0.toArray());
        checkGlError(gl, "AB2Text2DShader setForegroundColor() error");
    }

    public void setBackgroundColor(GL4 gl, Vector4 color1) {
        setUniform4v(gl, "colorBackground", 1, color1.toArray());
        checkGlError(gl, "AB2Text2DShader setBackgroundColor() error");
    }


}
