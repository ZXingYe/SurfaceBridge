package io.zxingye.library.surfaceextractor;

import android.annotation.SuppressLint;
import android.media.ImageReader;
import android.opengl.GLES20;

public class EglProgramYV12 extends EglProgram {

    private final int offsetLoc;
    private final int imgSizeLoc;

    public EglProgramYV12(EglBufferObjectHolder eglBOHolder) {
        super(FRAGMENT_SHADER_RGB_TO_YV12, eglBOHolder);
        offsetLoc = GLES20.glGetUniformLocation(programId, "u_Offset");
        imgSizeLoc = GLES20.glGetUniformLocation(programId, "u_ImgSize");
    }

    @Override
    protected void viewport(int width, int height) {
        GLES20.glViewport(0, 0, width / 4, (int) (height * 1.5f));
    }

    @Override
    protected void onDrawBefore(int width, int height) {
        GLES20.glUniform1f(offsetLoc, 1.f / (float) width);
        GLES20.glUniform2f(imgSizeLoc, width, height);
    }

    @Override
    protected void onDrawAfter(int width, int height) {
        // do nothing
    }

    @SuppressLint("WrongConstant")
    public static ImageReader createImageReader(int width, int height) {
        return EglTool.createImageReader(width / 4, (int) (height * 1.5));
    }

    private static final String FRAGMENT_SHADER_RGB_TO_YV12 = "" +
            "#version 300 es\n" +
            "#extension GL_OES_EGL_image_external_essl3 : require\n" +
            "precision highp float;\n" +
            "in vec2 v_texCoord;\n" +
            "layout(location = 0) out vec4 outColor;\n" +
            "uniform samplerExternalOES s_TextureMap;\n" +
            "uniform float u_Offset;\n" +
            "uniform vec2 u_ImgSize;\n" +
            "//Y =  0.299R + 0.587G + 0.114B\n" +
            "//U = -0.147R - 0.289G + 0.436B\n" +
            "//V =  0.615R - 0.515G - 0.100B\n" +
            "const vec3 COEF_Y = vec3( 0.299,  0.587,  0.114);\n" +
            "const vec3 COEF_U = vec3(-0.147, -0.289,  0.436);\n" +
            "const vec3 COEF_V = vec3( 0.615, -0.515, -0.100);\n" +
            "const float V_DIVIDE_LINE = 2.0 / 3.0;\n" +
            "const float U_DIVIDE_LINE = 5.0 / 6.0;\n" +
            "void main()\n" +
            "{\n" +
            "    vec2 texelOffset = vec2(u_Offset, 0.0);\n" +
            "    if(v_texCoord.y <= V_DIVIDE_LINE) {\n" +
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
            "    else if(v_texCoord.y <= U_DIVIDE_LINE){\n" +
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
            "    else {\n" +
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
            "}";
}
