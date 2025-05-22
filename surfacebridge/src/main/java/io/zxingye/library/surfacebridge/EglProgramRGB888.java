package io.zxingye.library.surfacebridge;

import android.opengl.GLES20;

public class EglProgramRGB888 extends EglProgram {

    private final int uWidth;

    public EglProgramRGB888(EglBufferObjectHolder eglBOHolder) {
        super(FRAGMENT_SHADER_RGB888, eglBOHolder);
        uWidth = GLES20.glGetUniformLocation(programId, "u_width");
    }

    @Override
    protected int getRealViewportWidth(int width) {
        return width * 3 / 4;
    }

    @Override
    protected int getRealViewportHeight(int height) {
        return height;
    }

    @Override
    protected void onDraw(int width, int height) {
        GLES20.glUniform1f(uWidth, width);
        super.onDraw(width, height);
    }

    @Override
    public FrameFormat getFrameFormat() {
        return FrameFormat.RGB_888;
    }

    private static final String FRAGMENT_SHADER_RGB888 = "" +
            "#version 300 es\n" +
            "#extension GL_OES_EGL_image_external_essl3 : require\n" +
            "precision highp float;\n" +
            "in vec2 v_texCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "uniform float u_width;\n" +
            "layout(location = 0) out vec4 outColor;\n" +
            "void main() {\n" +
            "     float viewportWidth = u_width * 3.0 / 4.0;\n" +
            "     float width = v_texCoord.x * viewportWidth;\n" +
            "     float x = (width + (width - 1.0) / 3.0) / u_width;\n" +
            "     vec2 coordStart = vec2(x, v_texCoord.y);\n" +
            "     vec2 coordEnd = vec2(x + 1.0 / u_width, v_texCoord.y);\n" +
            "     vec4 colorStart = texture(sTexture, coordStart);\n" +
            "     vec4 colorEnd = texture(sTexture, coordEnd);\n" +
            "     switch (int(ceil(width)) % 3) {\n" +
            "         case 0:\n" +
            "             outColor = vec4(colorStart.b, colorEnd.r, colorEnd.g, colorEnd.b);\n" +
            "             break;\n" +
            "         case 1:\n" +
            "             outColor = vec4(colorStart.r, colorStart.g, colorStart.b, colorEnd.r);\n" +
            "             break;\n" +
            "         case 2:\n" +
            "             outColor = vec4(colorStart.g, colorStart.b, colorEnd.r, colorEnd.g);\n" +
            "             break;\n" +
            "     }\n" +
            "}";
}
