package io.zxingye.library.surfaceextractor;

import android.opengl.GLES20;
import android.opengl.GLES30;

public abstract class EglProgram implements AutoCloseable {

    protected static final String VERTEX_SHADER = "" +
            "#version 300 es\n" +
            "layout(location = 0) in vec4 a_vertexCoord;\n" +
            "layout(location = 1) in vec4 a_texCoord;\n" +
            "layout (std140) uniform MVPMatrix\n" +
            "{\n" +
            "   mat4 mvp_matrix;\n" +
            "}; " +
            "layout (std140) uniform TexMatrix\n" +
            "{\n" +
            "   mat4 tex_matrix;\n" +
            "}; " +
            "out vec2 v_texCoord;\n" +
            "void main() {\n" +
            "    gl_Position = mvp_matrix * a_vertexCoord;\n" +
            "    v_texCoord = (tex_matrix * a_texCoord).xy;\n" +
            "}\n";


    protected final EglBufferObjectHolder eglBOHolder;
    protected int vertexVAO;
    protected int programId;


    protected abstract void viewport(int width, int height);

    protected abstract void onDrawBefore(int width, int height);

    protected abstract void onDrawAfter(int width, int height);

    protected EglProgram(String pixelShaderStr, EglBufferObjectHolder eglBOHolder) {
        this.eglBOHolder = eglBOHolder;
        try {
            programId = EglTool.createProgram(VERTEX_SHADER, pixelShaderStr);

            vertexVAO = EglTool.createVao();
            eglBOHolder.bindVertexVboPointer(vertexVAO, GLES20.glGetAttribLocation(programId, "a_vertexCoord"));
            eglBOHolder.bindTexVboPointer(vertexVAO, GLES20.glGetAttribLocation(programId, "a_texCoord"));

            eglBOHolder.bindMvpMatrixUbo(programId, GLES30.glGetUniformBlockIndex(programId, "MVPMatrix"));
            eglBOHolder.bindTexMatrixUbo(programId, GLES30.glGetUniformBlockIndex(programId, "TexMatrix"));
        } catch (Exception e) {
            close();
            throw e;
        }
    }

    @Override
    public void close() {
        GLES20.glUseProgram(0);
        EglTool.deleteVao(vertexVAO);
        EglTool.deleteProgram(programId);
        vertexVAO = 0;
        programId = 0;
    }

    public void draw(int viewportWidth, int viewportHeight) {
        EglTool.checkGlError("draw start");

        viewport(viewportWidth, viewportHeight);
        EglTool.checkGlError("viewport");

        // Select the program.
        GLES20.glUseProgram(programId);
        EglTool.checkGlError("glUseProgram");

        GLES30.glBindVertexArray(vertexVAO);
        EglTool.checkGlError("glBindVertexArray");

        onDrawBefore(viewportWidth, viewportHeight);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, eglBOHolder.getVertexCount());
        EglTool.checkGlError("glDrawArrays");

        onDrawAfter(viewportWidth, viewportHeight);

        GLES30.glBindVertexArray(0);
        GLES20.glUseProgram(0);

        EglTool.checkGlError("draw");
    }
}
