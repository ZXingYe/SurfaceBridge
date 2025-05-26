package io.zxingye.surfacebridge;

import android.graphics.Color;
import android.hardware.HardwareBuffer;
import android.media.ImageReader;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLES31;
import android.opengl.GLException;
import android.opengl.Matrix;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collection;

public final class EglTool {

    private EglTool() {
    }


    private static final String TAG = "EglTool";

    private static final int OPEN_GL_ES_VERSION = 3;

    private static final int FLOAT_SIZE = 4;


    public static EGLConfig createConfig(EGLDisplay display) {
        int[] attributesList = new int[]{
                EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR | EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 0,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? EGLExt.EGL_RECORDABLE_ANDROID : 0x3142, 1,
                EGL14.EGL_NONE};

        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(display, attributesList, 0, configs, 0, configs.length, numConfigs, 0)) {
            throw new GLException(-1, "createEGLConfig fail");
        }
        return configs[0];
    }

    public static EGLDisplay createDisplay() {
        EGLDisplay eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new GLException(-1, "createEGLDisplay fail: eglDisplay = " + eglDisplay);
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw new GLException(-1, "createEGLDisplay fail: eglInitialize = false");
        }
        Log.i(TAG, "createEGLDisplay: major = " + version[0] + ", minor = " + version[1]);
        return eglDisplay;
    }


    public static EGLContext createContext(EGLDisplay display, EGLConfig config, EGLContext sharedContext) {
        int[] attributesList = {EGL14.EGL_CONTEXT_CLIENT_VERSION, OPEN_GL_ES_VERSION, EGL14.EGL_NONE};
        sharedContext = sharedContext != null ? sharedContext : EGL14.EGL_NO_CONTEXT;
        EGLContext context = EGL14.eglCreateContext(display, config, sharedContext, attributesList, 0);
        if (EGL14.eglGetError() != EGL14.EGL_SUCCESS) {
            throw new GLException(-1, "createEGLContext fail");
        }
        return context;
    }

    public static EGLSurface createEGLSurface(EGLDisplay display, EGLConfig config, Surface surface) {
        int[] surfaceAttributes = {EGL14.EGL_NONE};
        EGLSurface eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, surfaceAttributes, 0);
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            throw new GLException(-1, "createEGLSurface fail: surface = " + surface);
        }
        checkGlError("eglCreateWindowSurface");
        return eglSurface;
    }

    public static void releaseSurface(EGLDisplay display, EGLContext context, EGLSurface surface) {
        EGLSurface currentSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
        if (currentSurface.equals(surface)) {
            makeCurrent(display, context, EGL14.EGL_NO_SURFACE);
        }
        releaseSurfaces(display, surface);
    }

    public static void releaseSurfaces(EGLDisplay display, EGLSurface... surfaces) {
        for (EGLSurface surface : surfaces) {
            if (!EGL14.eglDestroySurface(display, surface)) {
                Log.w(TAG, "releaseSurface fail: EGLSurface = " + surface);
            }
        }
    }

    public static void release(EGLDisplay display, EGLContext context) {
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroyContext(display, context);
        EGL14.eglReleaseThread();
        EGL14.eglTerminate(display);
    }

    public static void checkGlError(String op) {
        checkGlError(op, false);
    }

    public static void checkGlError(String op, boolean logcat) {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            String msg = op + ": glError 0x" + Integer.toHexString(error);
            if (logcat) {
                Log.e(TAG, msg);
            }
            throw new GLException(error, msg);
        }
    }

    public static void makeCurrent(EGLDisplay display, EGLContext context, EGLSurface surface) {
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
            throw new GLException(-1, "makeCurrent fail");
        }
    }

    public static void setViewportSize(int width, int height) {
        setViewportSize(0, 0, width, height);
    }

    public static void setViewportSize(int x, int y, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        checkGlError("glViewport");
    }

    public static void setShaderProgram(int programId) {
        GLES20.glUseProgram(programId);
        EglTool.checkGlError("glUseProgram");
    }

    public static void drawArraysByVao(int vaoId, int drawMode, int vertexCount) {
        GLES30.glBindVertexArray(vaoId);
        EglTool.checkGlError("glBindVertexArray");
        GLES20.glDrawArrays(drawMode, 0, vertexCount);
        EglTool.checkGlError("glDrawArrays");
        GLES30.glBindVertexArray(0);
    }

    public static int createOESTexture() {
        final int[] texId = new int[1];
        // 1表示生成一个纹理就够了
        GLES20.glGenTextures(1, texId, 0);
        // 将此纹理绑定到外部纹理上，绑定了才能设置后面的参数
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId[0]);
        //过滤（纹理像素映射到坐标点）  （缩小、放大：GL_LINEAR线性）
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        //裁剪（超出纹理坐标范围）  （s==x t==y GL_REPEAT 重复, GL_CLAMP_TO_EDGE 裁剪）
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        // 设置完之后就解绑就与当前的GL解绑
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        if (texId[0] <= 0) {
            throw new GLException(-1, "createOESTexture fail: texId = " + texId[0]);
        }
        return texId[0];
    }

    public static void deleteOESTexture(int texId) {
        if (texId > 0) {
            GLES20.glDeleteTextures(1, new int[]{texId}, 0);
        }
    }

    public static void bindOESTexture(int texId, int texType, int texPosition) {
        GLES20.glActiveTexture(texPosition);
        GLES20.glBindTexture(texType, texId);
        checkGlError("glBindTexture");
    }

    public static void clearColor(int color, int mask) {
        GLES20.glClearColor(
                Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f,
                Color.alpha(color) / 255f);
        GLES20.glClear(mask);
    }

    public static int getSurfaceWidth(EGLDisplay display, EGLSurface surface) {
        int[] result = new int[]{0};
        EGL14.eglQuerySurface(display, surface, EGL14.EGL_WIDTH, result, 0);
        return result[0];
    }


    public static int getSurfaceHeight(EGLDisplay display, EGLSurface surface) {
        int[] result = new int[]{0};
        EGL14.eglQuerySurface(display, surface, EGL14.EGL_HEIGHT, result, 0);
        return result[0];
    }

    public static int getOESTexWidth(int texId) {
        return getTexParameterI(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId, GLES31.GL_TEXTURE_WIDTH);
    }

    public static int getOESTexHeight(int texId) {
        return getTexParameterI(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId, GLES31.GL_TEXTURE_HEIGHT);
    }

    public static int getTexParameterI(int texId, int texType, int paramName) {
        try {
            int[] result = new int[]{-1};
            GLES20.glBindTexture(texType, texId);
            GLES31.glGetTexLevelParameteriv(texType, 0, paramName, result, 0);
            checkGlError("glGetTexLevelParameteriv", false);
            return result[0];
        } catch (Exception e) {
            return -1;
        }
    }

    public static boolean getRealOESTexSize(int texId, float[] texMatrix, int[] size) {
        return getRealTexSize(texId, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texMatrix, size);
    }

    public static boolean getRealTexSize(int texId, int texType, float[] texMatrix, int[] size) {
        try {
            GLES20.glBindTexture(texType, texId);
            GLES31.glGetTexLevelParameteriv(texType, 0, GLES31.GL_TEXTURE_WIDTH, size, 0);
            GLES31.glGetTexLevelParameteriv(texType, 0, GLES31.GL_TEXTURE_HEIGHT, size, 1);
            checkGlError("glGetTexLevelParameteriv", false);
        } catch (Exception e) {
            return false;
        }
        if (size[0] >= Short.MAX_VALUE || size[1] >= Short.MAX_VALUE) {
            return false;
        }
        if (size[0] <= 1 && size[1] <= 1) {
            return false;
        }

        float[] vector = new float[]{size[0], size[1], 0, 1};
        float[] realVector = new float[4];
        Matrix.multiplyMV(realVector, 0, texMatrix, 0, vector, 0);
        size[0] = (int) (realVector[0] > 0 ? (realVector[0]) : (Math.abs(realVector[0]) + 1));
        size[1] = (int) (realVector[1] > 0 ? (realVector[1]) : (Math.abs(realVector[1]) + 1));
        return true;
    }

    public static int loadShader(int shaderType, String shaderStr) {
        int shaderId = GLES20.glCreateShader(shaderType);
        if (shaderId <= 0) {
            throw new GLException(-1, "loadShader fail: shaderId = " + shaderId);
        }
        try {
            GLES20.glShaderSource(shaderId, shaderStr);
            GLES20.glCompileShader(shaderId);
            EglTool.checkGlError("glCreateShader type=" + shaderType);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shaderId, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] <= 0) {
                throw new GLException(-1, "loadShader fail: GL_COMPILE_STATUS = false, " +
                        "info = " + GLES20.glGetShaderInfoLog(shaderId));
            }
        } catch (Exception e) {
            GLES20.glDeleteShader(shaderId);
            throw e;
        }
        return shaderId;
    }

    public static int createProgram(String vertexShaderStr, String pixelShaderStr) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderStr);
        if (vertexShader <= 0) {
            throw new GLException(-1, "createProgram fail: vertexShader = " + vertexShader);
        }
        int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, pixelShaderStr);
        if (pixelShader <= 0) {
            throw new GLException(-1, "createProgram fail: pixelShader = " + pixelShader);
        }
        int shaderId = GLES20.glCreateProgram();
        if (shaderId <= 0) {
            throw new GLException(-1, "createProgram fail: shaderId = " + shaderId);
        }
        try {
            GLES20.glAttachShader(shaderId, vertexShader);
            EglTool.checkGlError("glAttachShader");
            GLES20.glAttachShader(shaderId, pixelShader);
            EglTool.checkGlError("glAttachShader");

            GLES20.glLinkProgram(shaderId);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(shaderId, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
                throw new GLException(-1, "createProgram fail :link status = " + linkStatus[0]
                        + ", log = " + GLES20.glGetProgramInfoLog(shaderId));
            }
        } catch (Exception e) {
            GLES20.glDeleteProgram(shaderId);
            throw e;
        } finally {
            GLES20.glDeleteShader(pixelShader);
            GLES20.glDeleteShader(vertexShader);
        }
        return shaderId;
    }

    public static void deleteProgram(int programId) {
        if (programId > 0) {
            GLES20.glDeleteProgram(programId);
        }
    }

    public static int createVao() {
        int[] vaoId = new int[1];
        GLES30.glGenVertexArrays(1, vaoId, 0);
        if (vaoId[0] <= 0) {
            throw new GLException(-1, "createVAO fail: vaoId = " + vaoId[0]);
        }
        return vaoId[0];
    }

    public static int createVbo(FloatBuffer defaultData, int usage) {
        int[] vboId = new int[1];
        GLES20.glGenBuffers(1, vboId, 0);
        if (vboId[0] <= 0) {
            throw new GLException(-1, "createAndBindVBO fail: glGenBuffers = " + vboId[0]);
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId[0]);
        GLES30.glBufferData(GLES20.GL_ARRAY_BUFFER, sizeof(defaultData), defaultData, usage);
        EglTool.checkGlError("glBufferData");
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, GLES20.GL_NONE);
        return vboId[0];
    }

    public static int createUbo(FloatBuffer defaultData, int usage) {
        int[] uboId = new int[1];
        GLES20.glGenBuffers(1, uboId, 0);
        if (uboId[0] <= 0) {
            throw new GLException(-1, "createUBO fail: glGenBuffers = " + uboId[0]);
        }
        GLES20.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, uboId[0]);
        GLES30.glBufferData(GLES30.GL_UNIFORM_BUFFER, sizeof(defaultData), defaultData, usage);
        EglTool.checkGlError("glBufferData");
        GLES30.glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, uboId[0], uboId[0]);
        EglTool.checkGlError("glBindBufferBase");
        GLES20.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, GLES20.GL_NONE);
        return uboId[0];
    }

    public static void updateBo(int bufferType, int bufferId, FloatBuffer buffer, float[] data) {
        buffer.clear();
        buffer.put(data);
        buffer.rewind();
        GLES20.glBindBuffer(bufferType, bufferId);
        GLES20.glBufferSubData(bufferType, 0, sizeof(buffer), buffer);
        EglTool.checkGlError("glBufferSubData");
    }

    public static void deleteBo(int bufferId) {
        if (bufferId > 0) {
            GLES20.glDeleteBuffers(1, new int[]{bufferId}, 0);
        }
    }

    public static void deleteVao(int vertexArrayId) {
        if (vertexArrayId > 0) {
            GLES30.glDeleteVertexArrays(1, new int[vertexArrayId], 0);
        }
    }

    public static void bindVboPointerToVao(int vaoId, int vboId, int attribIndex, int dimensional) {
        GLES30.glBindVertexArray(vaoId);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glEnableVertexAttribArray(attribIndex);
        EglTool.checkGlError("glEnableVertexAttribArray");
        GLES20.glVertexAttribPointer(
                attribIndex,
                dimensional,
                GLES20.GL_FLOAT,
                false,
                FLOAT_SIZE * dimensional,
                0);
        EglTool.checkGlError("glVertexAttribPointer");
        GLES30.glBindVertexArray(0);
    }

    public static void bindUboBindingIndex(int shaderId, int blockIndex, int uboBindingIndex) {
        GLES30.glUniformBlockBinding(shaderId, blockIndex, uboBindingIndex);
        EglTool.checkGlError("glUniformBlockBinding");
    }

    public static void releaseEglProgram(Collection<EglProgram> programs) {
        for (EglProgram program : programs) {
            program.close();
        }
    }

    public static void releaseEGLSurfaceHolder(EGLDisplay display, Collection<EglCore.EglSurfaceHolder> holders) {
        EGLSurface[] surfaceArr = new EGLSurface[holders.size()];
        int i = 0;
        for (EglCore.EglSurfaceHolder holder : holders) {
            surfaceArr[i++] = holder.eglSurface;
        }
        releaseSurfaces(display, surfaceArr);
    }

    public static ImageReader createImageReader(int width, int height, int format) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            long usage = HardwareBuffer.USAGE_GPU_COLOR_OUTPUT | HardwareBuffer.USAGE_CPU_READ_OFTEN;
            if (HardwareBuffer.isSupported(width, height, format, 1, usage)) {
                return ImageReader.newInstance(width, height, format, 2, usage);
            }
        }
        return ImageReader.newInstance(width, height, format, 2);
    }

    public static FloatBuffer createFloatBuffer(int floatCount) {
        ByteBuffer bb = ByteBuffer.allocateDirect(floatCount * FLOAT_SIZE);
        bb.order(ByteOrder.nativeOrder());
        return bb.asFloatBuffer();
    }

    public static FloatBuffer createFloatBuffer(float[] array) {
        FloatBuffer fb = createFloatBuffer(array.length);
        fb.put(array);
        fb.rewind();
        return fb;
    }

    public static int sizeof(FloatBuffer buffer) {
        return buffer.capacity() * 4;
    }

}
