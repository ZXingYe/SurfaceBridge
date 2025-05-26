package io.zxingye.surfacebridge;

import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLException;

import java.nio.FloatBuffer;

public class EglBufferObjectHolder implements AutoCloseable {

    private static final float[] IDENTITY_MATRIX = new float[]{
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
    };

    private static final float[] DEFAULT_VERTEX_COORDS = new float[]{
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f
    };

    private static final float[] DEFAULT_TEX_COORDS = new float[]{
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
    };

    private static final int DEFAULT_DIMENSIONAL = 2;

    private final FloatBuffer vertexCoordBuffer;
    private final FloatBuffer texCoordBuffer;
    private final FloatBuffer mvpMatrixBuffer;
    private final FloatBuffer texMatrixBuffer;

    private final int dimensional;

    private int vertexCoordVbo;
    private int texCoordVbo;
    private int mvpMatrixUbo;
    private int texMatrixUbo;

    public EglBufferObjectHolder() {
        this(DEFAULT_DIMENSIONAL);
    }

    public EglBufferObjectHolder(int dimensional) {
        if (dimensional <= 0) {
            throw new GLException(-1, "dimensional <= 0");
        }
        this.dimensional = dimensional;
        try {
            vertexCoordBuffer = EglTool.createFloatBuffer(DEFAULT_VERTEX_COORDS);
            texCoordBuffer = EglTool.createFloatBuffer(DEFAULT_TEX_COORDS);
            mvpMatrixBuffer = EglTool.createFloatBuffer(IDENTITY_MATRIX);
            texMatrixBuffer = EglTool.createFloatBuffer(IDENTITY_MATRIX);

            vertexCoordVbo = EglTool.createVbo(vertexCoordBuffer, GLES20.GL_STREAM_DRAW);
            texCoordVbo = EglTool.createVbo(texCoordBuffer, GLES20.GL_STREAM_DRAW);
            mvpMatrixUbo = EglTool.createUbo(mvpMatrixBuffer, GLES20.GL_STREAM_DRAW);
            texMatrixUbo = EglTool.createUbo(texMatrixBuffer, GLES20.GL_STREAM_DRAW);
        } catch (Throwable e) {
            close();
            throw e;
        }
    }

    @Override
    public void close() {
        EglTool.deleteBo(vertexCoordVbo);
        vertexCoordVbo = 0;

        EglTool.deleteBo(texCoordVbo);
        texCoordVbo = 0;

        EglTool.deleteBo(mvpMatrixUbo);
        mvpMatrixUbo = 0;

        EglTool.deleteBo(texMatrixUbo);
        texMatrixUbo = 0;
    }

    public void updateVertexCoordinate(float[] vertexCoordinate) {
        EglTool.updateBo(GLES30.GL_ARRAY_BUFFER, vertexCoordVbo, vertexCoordBuffer, vertexCoordinate);
    }

    public void updateTexCoordinate(float[] texCoordinate) {
        EglTool.updateBo(GLES30.GL_ARRAY_BUFFER, texCoordVbo, texCoordBuffer, texCoordinate);
    }

    public void updateMvpMatrixUbo(float[] mvpMatrix) {
        EglTool.updateBo(GLES30.GL_UNIFORM_BUFFER, mvpMatrixUbo, mvpMatrixBuffer, mvpMatrix);
    }

    public void updateTexMatrixUbo(float[] texMatrix) {
        EglTool.updateBo(GLES30.GL_UNIFORM_BUFFER, texMatrixUbo, texMatrixBuffer, texMatrix);
    }

    public void bindVertexVboPointer(int vaoId, int vertexAttribIndex) {
        EglTool.bindVboPointerToVao(vaoId, vertexCoordVbo, vertexAttribIndex, dimensional);
    }

    public void bindTexVboPointer(int vaoId, int texAttribIndex) {
        EglTool.bindVboPointerToVao(vaoId, texCoordVbo, texAttribIndex, dimensional);
    }

    public void bindMvpMatrixUbo(int shaderId, int blockIndex) {
        EglTool.bindUboBindingIndex(shaderId, blockIndex, mvpMatrixUbo);
    }

    public void bindTexMatrixUbo(int shaderId, int blockIndex) {
        EglTool.bindUboBindingIndex(shaderId, blockIndex, texMatrixUbo);
    }

    public int getVertexCount() {
        return vertexCoordBuffer.capacity() / dimensional;
    }

}
