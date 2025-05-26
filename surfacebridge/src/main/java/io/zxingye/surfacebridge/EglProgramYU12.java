package io.zxingye.surfacebridge;

import android.opengl.GLES20;

public class EglProgramYU12 extends EglProgramYUV {

    private final int offsetLoc;
    private final int imgSizeLoc;

    public EglProgramYU12(EglBufferObjectHolder eglBOHolder) {
        super(FRAGMENT_SHADER_RGB_TO_YU12, eglBOHolder);
        offsetLoc = GLES20.glGetUniformLocation(programId, "u_Offset");
        imgSizeLoc = GLES20.glGetUniformLocation(programId, "u_ImgSize");
    }

    @Override
    protected int getRealViewportWidth(int width) {
        return width / 4;
    }

    @Override
    protected int getRealViewportHeight(int height) {
        return (int) (height * 1.5f);
    }

    @Override
    protected void onDraw(int width, int height) {
        GLES20.glUniform1f(offsetLoc, 1.f / (float) width);
        GLES20.glUniform2f(imgSizeLoc, width, height);
        super.onDraw(width, height);
    }

    @Override
    public FrameFormat getFrameFormat() {
        return FrameFormat.YU12;
    }

    private static final String FRAGMENT_SHADER_RGB_TO_YU12 = "" +
            "#version 300 es\n" +
            "#extension GL_OES_EGL_image_external_essl3 : require\n" +
            "precision highp float;\n" +
            "in vec2 v_texCoord;\n" +
            "layout(location = 0) out vec4 outColor;\n" +
            "uniform samplerExternalOES s_TextureMap;\n" +
            "uniform float u_Offset;\n" +
            "uniform vec2 u_ImgSize;\n" +
            "uniform vec3 COEF_Y;\n" +
            "uniform vec3 COEF_U;\n" +
            "uniform vec3 COEF_V;\n" +
            "const float U_DIVIDE_LINE = 2.0 / 3.0;\n" +
            "const float V_DIVIDE_LINE = 5.0 / 6.0;\n" +
            "void main()\n" +
            "{\n" +
            "    vec2 texelOffset = vec2(u_Offset, 0.0);\n" +
            "    if(v_texCoord.y <= U_DIVIDE_LINE) {\n" +
            "        vec2 texCoord = vec2(v_texCoord.x, v_texCoord.y * 3.0 / 2.0);\n" +
            "        vec4 color0 = texture(s_TextureMap, texCoord);\n" +
            "        vec4 color1 = texture(s_TextureMap, texCoord + texelOffset);\n" +
            "        vec4 color2 = texture(s_TextureMap, texCoord + texelOffset * 2.0);\n" +
            "        vec4 color3 = texture(s_TextureMap, texCoord + texelOffset * 3.0);\n" +
            "\n" +
            "        float y0 = dot(color0.rgb, COEF_Y);\n" +
            "        float y1 = dot(color1.rgb, COEF_Y);\n" +
            "        float y2 = dot(color2.rgb, COEF_Y);\n" +
            "        float y3 = dot(color3.rgb, COEF_Y);\n" +
            "        outColor = vec4(y0, y1, y2, y3);\n" +
            "    }\n" +
            "    else if(v_texCoord.y <= V_DIVIDE_LINE){\n" +
            "        float offsetY = 1.0 / 3.0 / u_ImgSize.y;\n" +
            "        vec2 texCoord;\n" +
            "        if(v_texCoord.x <= 0.5) {\n" +
            "            texCoord = vec2(v_texCoord.x * 2.0, (v_texCoord.y - U_DIVIDE_LINE) * 2.0 * 3.0);\n" +
            "        }\n" +
            "        else {\n" +
            "            texCoord = vec2((v_texCoord.x - 0.5) * 2.0, ((v_texCoord.y - U_DIVIDE_LINE) * 2.0 + offsetY) * 3.0);\n" +
            "        }\n" +
            "\n" +
            "        vec4 color0 = texture(s_TextureMap, texCoord);\n" +
            "        vec4 color1 = texture(s_TextureMap, texCoord + texelOffset * 2.0);\n" +
            "        vec4 color2 = texture(s_TextureMap, texCoord + texelOffset * 4.0);\n" +
            "        vec4 color3 = texture(s_TextureMap, texCoord + texelOffset * 6.0);\n" +
            "\n" +
            "        float u0 = dot(color0.rgb, COEF_U) + 0.5;\n" +
            "        float u1 = dot(color1.rgb, COEF_U) + 0.5;\n" +
            "        float u2 = dot(color2.rgb, COEF_U) + 0.5;\n" +
            "        float u3 = dot(color3.rgb, COEF_U) + 0.5;\n" +
            "        outColor = vec4(u0, u1, u2, u3);\n" +
            "    }\n" +
            "    else {\n" +
            "        float offsetY = 1.0 / 3.0 / u_ImgSize.y;\n" +
            "        vec2 texCoord;\n" +
            "        if(v_texCoord.x <= 0.5) {\n" +
            "            texCoord = vec2(v_texCoord.x * 2.0, (v_texCoord.y - V_DIVIDE_LINE) * 2.0 * 3.0);\n" +
            "        }\n" +
            "        else {\n" +
            "            texCoord = vec2((v_texCoord.x - 0.5) * 2.0, ((v_texCoord.y - V_DIVIDE_LINE) * 2.0 + offsetY) * 3.0);\n" +
            "        }\n" +
            "\n" +
            "        vec4 color0 = texture(s_TextureMap, texCoord);\n" +
            "        vec4 color1 = texture(s_TextureMap, texCoord + texelOffset * 2.0);\n" +
            "        vec4 color2 = texture(s_TextureMap, texCoord + texelOffset * 4.0);\n" +
            "        vec4 color3 = texture(s_TextureMap, texCoord + texelOffset * 6.0);\n" +
            "\n" +
            "        float v0 = dot(color0.rgb, COEF_V) + 0.5;\n" +
            "        float v1 = dot(color1.rgb, COEF_V) + 0.5;\n" +
            "        float v2 = dot(color2.rgb, COEF_V) + 0.5;\n" +
            "        float v3 = dot(color3.rgb, COEF_V) + 0.5;\n" +
            "        outColor = vec4(v0, v1, v2, v3);\n" +
            "    }\n" +
            "}";
}
