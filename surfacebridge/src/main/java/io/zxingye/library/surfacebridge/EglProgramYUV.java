package io.zxingye.library.surfacebridge;

import android.opengl.GLES20;

public abstract class EglProgramYUV extends EglProgram {

    private final int coefYLoc;
    private final int coefULoc;
    private final int coefVLoc;

    protected EglProgramYUV(String pixelShaderStr, EglBufferObjectHolder eglBOHolder) {
        super(pixelShaderStr, eglBOHolder);
        coefYLoc = GLES20.glGetUniformLocation(programId, "COEF_Y");
        coefULoc = GLES20.glGetUniformLocation(programId, "COEF_U");
        coefVLoc = GLES20.glGetUniformLocation(programId, "COEF_V");
        setYuvColorSpace(EglYUVColorSpace.BT_709);
    }

    public void setYuvColorSpace(EglYUVColorSpace colorSpace) {
        EglTool.setShaderProgram(programId);
        switch (colorSpace) {
            case BT_601:
                GLES20.glUniform3f(coefYLoc, 0.299f, 0.587f, 0.114f);
                GLES20.glUniform3f(coefULoc, -0.14713f, -0.2886f, 0.436f);
                GLES20.glUniform3f(coefVLoc, 0.615f, -0.51499f, -0.10001f);
                break;
            case BT_709:
                GLES20.glUniform3f(coefYLoc, 0.2126f, 0.7152f, 0.0722f);
                GLES20.glUniform3f(coefULoc, -0.09991f, -0.33609f, 0.436f);
                GLES20.glUniform3f(coefVLoc, 0.615f, -0.55861f, -0.05639f);
                break;
            case BT_2020:
                GLES20.glUniform3f(coefYLoc, 0.2627f, 0.6780f, 0.0593f);
                GLES20.glUniform3f(coefULoc, -0.13963f, -0.36037f, 0.5f);
                GLES20.glUniform3f(coefVLoc, 0.5f, -0.45979f, -0.04021f);
                break;
        }
    }
}
