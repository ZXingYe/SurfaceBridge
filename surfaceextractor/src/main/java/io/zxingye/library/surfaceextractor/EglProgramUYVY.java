package io.zxingye.library.surfaceextractor;

import android.opengl.GLES20;

public class EglProgramUYVY extends EglProgram {

    private final int offsetLoc;

    public EglProgramUYVY(EglBufferObjectHolder eglBOHolder) {
        super(FRAGMENT_SHADER_RGB_TO_UYVY, eglBOHolder);
        offsetLoc = GLES20.glGetUniformLocation(programId, "u_Offset");
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
        GLES20.glUniform1f(offsetLoc, 1.f / (float) width);
        super.onDraw(width, height);
    }

    @Override
    public FrameFormat getFrameFormat() {
        return FrameFormat.UYVY;
    }

    private static final String FRAGMENT_SHADER_RGB_TO_UYVY = "" +
            "#version 300 es\n" +
            "#extension GL_OES_EGL_image_external_essl3 : require\n" +
            "precision highp float;\n" +
            "in vec2 v_texCoord;\n" +
            "layout(location = 0) out vec4 outColor;\n" +
            "uniform samplerExternalOES s_TextureMap;\n" +
            "uniform float u_Offset;\n" +
            "//Y =  0.299R + 0.587G + 0.114B\n" +
            "//U = -0.147R - 0.289G + 0.436B\n" +
            "//V =  0.615R - 0.515G - 0.100B\n" +
            "const vec3 COEF_Y = vec3( 0.299,  0.587,  0.114);\n" +
            "const vec3 COEF_U = vec3(-0.147, -0.289,  0.436);\n" +
            "const vec3 COEF_V = vec3( 0.615, -0.515, -0.100);\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "    vec2 texelOffset = vec2(u_Offset, 0.0);\n" +
            "    vec4 color0 = texture(s_TextureMap, v_texCoord);\n" +
            "    vec4 color1 = texture(s_TextureMap, v_texCoord + texelOffset);\n" +
            "    float y0 = dot(color0.rgb, COEF_Y);\n" +
            "    float u0 = dot(color0.rgb, COEF_U) + 0.5;\n" +
            "    float v0 = dot(color0.rgb, COEF_V) + 0.5;\n" +
            "    float y1 = dot(color1.rgb, COEF_Y);\n" +
            "    outColor = vec4(u0, y0, v0, y1);\n" +
            "}";
}
