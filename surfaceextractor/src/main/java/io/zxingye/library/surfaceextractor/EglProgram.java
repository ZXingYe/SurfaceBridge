package io.zxingye.library.surfaceextractor;

import android.media.ImageReader;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.util.Size;

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


    protected abstract int getRealViewportWidth(int width);

    protected abstract int getRealViewportHeight(int height);

    protected abstract void onDraw(int width, int height);


    public abstract FrameFormat getFrameFormat();

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

        GLES20.glViewport(0, 0, getRealViewportWidth(viewportWidth), getRealViewportHeight(viewportHeight));
        EglTool.checkGlError("viewport");

        // Select the program.
        GLES20.glUseProgram(programId);
        EglTool.checkGlError("glUseProgram");

        GLES30.glBindVertexArray(vertexVAO);
        EglTool.checkGlError("glBindVertexArray");

        onDraw(viewportWidth, viewportHeight);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, eglBOHolder.getVertexCount());
        EglTool.checkGlError("glDrawArrays");

        GLES30.glBindVertexArray(0);
        GLES20.glUseProgram(0);

        EglTool.checkGlError("draw");
    }

    public EglFrameReader createEglFrameReader(Size size, boolean directBuffer) {
        ImageReader reader = EglTool.createImageReader(
                getRealViewportWidth(size.getWidth()),
                getRealViewportHeight(size.getHeight()));
        return new EglFrameReader(reader, getFrameFormat(), size, directBuffer);
    }
}
