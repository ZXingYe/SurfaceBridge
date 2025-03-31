package io.zxingye.library.surfaceextractor;

import android.annotation.SuppressLint;
import android.media.ImageReader;
import android.opengl.GLES20;

public class EglProgramRGBA extends EglProgram {

    public EglProgramRGBA(EglBufferObjectHolder eglBOHolder) {
        super(FRAGMENT_SHADER_RGB, eglBOHolder);
    }

    @Override
    protected void viewport(int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    protected void onDrawBefore(int width, int height) {
        // do nothing
    }

    @Override
    protected void onDrawAfter(int width, int height) {
        // do nothing
    }

    @SuppressLint("WrongConstant")
    public static ImageReader createImageReader(int width, int height) {
        return EglTool.createImageReader(width, height);
    }

    private static final String FRAGMENT_SHADER_RGB = "" +
            "#version 300 es\n" +
            "#extension GL_OES_EGL_image_external_essl3 : require\n" +
            "precision highp float;\n" +
            "in vec2 v_texCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "layout(location = 0) out vec4 outColor;\n" +
            "void main() {\n" +
            "    outColor = texture(sTexture, v_texCoord);\n" +
            "}\n";
}
