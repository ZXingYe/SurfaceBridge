package io.zxingye.surfacebridge;

import android.opengl.GLES20;

public class EglProgramRGB565 extends EglProgram {

    private final int uWidth;

    public EglProgramRGB565(EglBufferObjectHolder eglBOHolder) {
        super(FRAGMENT_SHADER_RGB565, eglBOHolder);
        uWidth = GLES20.glGetUniformLocation(programId, "u_width");
    }

    @Override
    protected int getRealViewportWidth(int width) {
        return width / 2;
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
        return FrameFormat.RGB_565;
    }

    private static final String FRAGMENT_SHADER_RGB565 = "" +
            "#version 300 es\n" +
            "#extension GL_OES_EGL_image_external_essl3 : require\n" +
            "precision highp float;\n" +
            "in vec2 v_texCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "uniform float u_width;\n" +
            "layout(location = 0) out vec4 outColor;\n" +
            "\n" +
            "int getColor(float color, int maxValue) {\n" +
            "    int value = int(round(color * float(maxValue)));\n" +
            "    value = min(value, maxValue);\n" +
            "    value = max(0, value);\n" +
            "    return value;\n" +
            "}" +
            "\n" +
            "void main() {\n" +
            "   float x1 = v_texCoord.x;\n" +
            "   float x0 = x1 - 1.0 / u_width;\n" +
            "   vec2 coord0 = vec2(x0, v_texCoord.y);\n" +
            "   vec2 coord1 = vec2(x1, v_texCoord.y);\n" +
            "   vec3 color0 = texture(sTexture, coord0).rgb;\n" +
            "   vec3 color1 = texture(sTexture, coord1).rgb;\n" +
            "   int r0 = getColor(color0.r, 31);\n" +
            "   int g0 = getColor(color0.g, 63);\n" +
            "   int b0 = getColor(color0.b, 31);\n" +
            "   int output0 = (r0 << 11) | (g0 << 5) | b0;\n" +
            "   int r1 = getColor(color1.r, 31);\n" +
            "   int g1 = getColor(color1.g, 63);\n" +
            "   int b1 = getColor(color1.b, 31);\n" +
            "   int output1 =  (r1 << 11) | (g1 << 5) | b1;\n" +
            "   outColor = vec4(\n" +
            "           float((output0 & 0xFF)),\n" +
            "           float(((output0 >> 8) & 0xFF)),\n" +
            "           float((output1 & 0xFF)),\n" +
            "           float(((output1 >> 8) & 0xFF))) / 255.0;\n" +
            "}";
}
