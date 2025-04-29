package io.zxingye.library.surfaceextractor;

public class EglProgramBGRX extends EglProgram {

    public EglProgramBGRX(EglBufferObjectHolder eglBOHolder) {
        super(FRAGMENT_SHADER_BGRX, eglBOHolder);
    }

    @Override
    protected int getRealViewportWidth(int width) {
        return width;
    }

    @Override
    protected int getRealViewportHeight(int height) {
        return height;
    }

    @Override
    public FrameFormat getFrameFormat() {
        return FrameFormat.BGRX_8888;
    }

    private static final String FRAGMENT_SHADER_BGRX = "" +
            "#version 300 es\n" +
            "#extension GL_OES_EGL_image_external_essl3 : require\n" +
            "precision highp float;\n" +
            "in vec2 v_texCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "layout(location = 0) out vec4 outColor;\n" +
            "void main() {\n" +
            "    outColor = texture(sTexture, v_texCoord).bgra;\n" +
            "    outColor.a = 1.0;\n" +
            "}";
}
