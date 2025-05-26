package io.zxingye.surfacebridge;

public class EglProgramI444 extends EglProgramYUV {

    public EglProgramI444(EglBufferObjectHolder eglBOHolder) {
        super(FRAGMENT_SHADER_RGB_TO_I444, eglBOHolder);
    }

    @Override
    protected int getRealViewportWidth(int width) {
        return width / 4;
    }

    @Override
    protected int getRealViewportHeight(int height) {
        return height * 3;
    }

    @Override
    public FrameFormat getFrameFormat() {
        return FrameFormat.I444;
    }

    private static final String FRAGMENT_SHADER_RGB_TO_I444 = "" +
            "#version 300 es\n" +
            "#extension GL_OES_EGL_image_external_essl3 : require\n" +
            "precision highp float;\n" +
            "in vec2 v_texCoord;\n" +
            "layout(location = 0) out vec4 outColor;\n" +
            "uniform samplerExternalOES s_TextureMap;\n" +
            "uniform float u_Offset;\n" +
            "uniform vec3 COEF_Y;\n" +
            "uniform vec3 COEF_U;\n" +
            "uniform vec3 COEF_V;\n" +
            "const float U_DIVIDE_LINE = 1.0 / 3.0;\n" +
            "const float V_DIVIDE_LINE = 2.0 / 3.0;\n" +
            "void main()\n" +
            "{\n" +
            "    vec2 texelOffset = vec2(u_Offset, 0.0);\n" +
            "    if(v_texCoord.y <= U_DIVIDE_LINE) {\n" +
            "        vec2 texCoord = vec2(v_texCoord.x, v_texCoord.y * 3.0);\n" +
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
            "    } else if(v_texCoord.y <= V_DIVIDE_LINE) {\n" +
            "        vec2 texCoord = vec2(v_texCoord.x, (v_texCoord.y - U_DIVIDE_LINE) * 3.0);\n" +
            "        vec4 color0 = texture(s_TextureMap, texCoord);\n" +
            "        vec4 color1 = texture(s_TextureMap, texCoord + texelOffset);\n" +
            "        vec4 color2 = texture(s_TextureMap, texCoord + texelOffset * 2.0);\n" +
            "        vec4 color3 = texture(s_TextureMap, texCoord + texelOffset * 3.0);\n" +
            "\n" +
            "        float u0 = dot(color0.rgb, COEF_U) + 0.5;\n" +
            "        float u1 = dot(color1.rgb, COEF_U) + 0.5;\n" +
            "        float u2 = dot(color2.rgb, COEF_U) + 0.5;\n" +
            "        float u3 = dot(color3.rgb, COEF_U) + 0.5;\n" +
            "        outColor = vec4(u0, u1, u2, u3);\n" +
            "    } else {\n" +
            "        vec2 texCoord = vec2(v_texCoord.x, (v_texCoord.y - V_DIVIDE_LINE) * 3.0);\n" +
            "        vec4 color0 = texture(s_TextureMap, texCoord);\n" +
            "        vec4 color1 = texture(s_TextureMap, texCoord + texelOffset);\n" +
            "        vec4 color2 = texture(s_TextureMap, texCoord + texelOffset * 2.0);\n" +
            "        vec4 color3 = texture(s_TextureMap, texCoord + texelOffset * 3.0);\n" +
            "\n" +
            "        float v0 = dot(color0.rgb, COEF_V) + 0.5;\n" +
            "        float v1 = dot(color1.rgb, COEF_V) + 0.5;\n" +
            "        float v2 = dot(color2.rgb, COEF_V) + 0.5;\n" +
            "        float v3 = dot(color3.rgb, COEF_V) + 0.5;\n" +
            "        outColor = vec4(v0, v1, v2, v3);\n" +
            "    }\n" +
            "}";
}
