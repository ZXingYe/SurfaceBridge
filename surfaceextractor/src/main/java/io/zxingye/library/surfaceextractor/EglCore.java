package io.zxingye.library.surfaceextractor;

import android.graphics.Color;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLException;
import android.util.Log;
import android.view.Surface;

import java.util.HashMap;
import java.util.Map;

import io.zxingye.library.surfaceextractor.transform.Transform;

/**
 * 负责真正去操作opengl es的调用
 * 开发者需要在独立的线程去使用该类，内部会将openGL绑定在构建EglCore的线程中
 * <p>
 * EglCore本质就是将纹理绘制到多个surface中，需要需要开发者先添加surface到EglCore中,再通过draw方法绘制上去
 */
public class EglCore implements AutoCloseable {

    private static final String TAG = "EglHelper";

    public static EglCore create(EGLContext shareContext) {
        try {
            return new EglCore(shareContext);
        } catch (Exception e) {
            Log.w(TAG, "create opengl error: " + Log.getStackTraceString(e));
        }
        return null;
    }


    private final Map<Surface, EGLSurfaceHolder> eglSurfaceMap = new HashMap<>();
    private final Map<FrameFormat, EglProgram> texture2dProgramMap = new HashMap<>();
    private final EglVertexCoord eglVertexCoord = new EglVertexCoord();
    private final EGLDisplay eglDisplay;
    private final EGLContext eglContext;
    private final EGLConfig eglConfig;
    private final EglBufferObjectHolder eglBOHolder;

    private int backgroundColor = Color.BLACK;
    private boolean isRelease = false;

    private EglCore(final EGLContext shareContext) {
        try {
            eglDisplay = EglTool.createDisplay();
            eglConfig = EglTool.createConfig(eglDisplay);
            eglContext = EglTool.createContext(eglDisplay, eglConfig, shareContext);
            EglTool.makeCurrent(eglDisplay, eglContext, EGL14.EGL_NO_SURFACE);

            eglBOHolder = new EglBufferObjectHolder(2);

            texture2dProgramMap.put(FrameFormat.RGBA_8888, new EglProgramRGBA(eglBOHolder));
            texture2dProgramMap.put(FrameFormat.YU12, new EglProgramYU12(eglBOHolder));
            texture2dProgramMap.put(FrameFormat.YV12, new EglProgramYV12(eglBOHolder));
            texture2dProgramMap.put(FrameFormat.NV12, new EglProgramNV12(eglBOHolder));
            texture2dProgramMap.put(FrameFormat.NV21, new EglProgramNV21(eglBOHolder));
            texture2dProgramMap.put(FrameFormat.I444, new EglProgramI444(eglBOHolder));
            texture2dProgramMap.put(FrameFormat.YUVY, new EglProgramYUVY(eglBOHolder));
            texture2dProgramMap.put(FrameFormat.YUYV, new EglProgramYUYV(eglBOHolder));
            texture2dProgramMap.put(FrameFormat.YVYU, new EglProgramYVYU(eglBOHolder));
            texture2dProgramMap.put(FrameFormat.UYVY, new EglProgramUYVY(eglBOHolder));
            texture2dProgramMap.put(FrameFormat.VYUY, new EglProgramVYUY(eglBOHolder));
            Log.i(TAG, "version = " + GLES20.glGetString(GLES20.GL_VERSION));
        } catch (Throwable e) {
            close();
            throw e;
        }
    }

    @Override
    public void close() {
        if (!isRelease) {
            EglTool.releaseEGLSurfaceHolder(eglDisplay, eglSurfaceMap.values());
            EglTool.releaseEglProgram(texture2dProgramMap.values());
            eglBOHolder.close();
            EglTool.release(eglDisplay, eglContext);
            texture2dProgramMap.clear();
            eglSurfaceMap.clear();
            isRelease = true;
        }
    }

    public void drawOESTexture(int texId, int texWidth, int texHeight, float[] texMatrix) {
        if (isRelease || eglSurfaceMap.isEmpty()) {
            return;
        }
        if (texWidth <= 0 || texHeight <= 0) {
            Log.w(TAG, "skip draw: texture size = 0");
            return;
        }

        EglTool.bindOESTexture(texId, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE0);

        eglBOHolder.updateTexMatrixUbo(texMatrix);

        for (EGLSurfaceHolder surfaceHolder : eglSurfaceMap.values()) {
            if (!surfaceHolder.surface.isValid()) {
                Log.w(TAG, "skip draw: surface is invalid: " + surfaceHolder.surface);
                continue;
            }
            EglProgram program = texture2dProgramMap.get(surfaceHolder.format);
            if (program == null) {
                Log.w(TAG, "skip draw: program == null, format = " + surfaceHolder.format);
                return;
            }


            int surfaceWidth = surfaceHolder.getSurfaceWidth(eglDisplay);
            int surfaceHeight = surfaceHolder.getSurfaceHeight(eglDisplay);
            if (surfaceWidth <= 0 || surfaceHeight <= 0) {
                Log.w(TAG, "skip draw: surface size = 0, surfaceName = " + surfaceHolder.surface);
                continue;
            }

            eglVertexCoord.updateViewport(surfaceWidth, surfaceHeight);
            float[] vertex = eglVertexCoord.getVertexCoord(texWidth, texHeight, surfaceHolder.transform);
            eglBOHolder.updateVertexCoordinate(vertex);

            EglTool.makeCurrent(eglDisplay, eglContext, surfaceHolder.eglSurface);
            EglTool.clearColor(backgroundColor, GLES20.GL_COLOR_BUFFER_BIT);

            program.draw(surfaceWidth, surfaceHeight);

            if (!EGL14.eglSwapBuffers(eglDisplay, surfaceHolder.eglSurface)) {
                throw new GLException(-1, "eglSwapBuffers fail: " + surfaceHolder.surface + " " + surfaceHolder.eglSurface);
            }
        }
    }

    public void deleteOESTextureObject(int textureID) {
        if (!isRelease) {
            EglTool.deleteOESTexture(textureID);
        }
    }

    public void putSurface(Surface surface, int width, int height, FrameFormat format, Transform transform) {
        if (isRelease) {
            return;
        }
        if (surface == null || !surface.isValid()) {
            Log.w(TAG, "putSurface fail",
                    new IllegalArgumentException("surface is not available, name = " + surface));
            return;
        }
        if (!texture2dProgramMap.containsKey(format)) {
            Log.w(TAG, "putSurface fail",
                    new IllegalArgumentException("format is not supported, format = " + format));
            return;
        }
        EGLSurfaceHolder info = eglSurfaceMap.get(surface);
        EGLSurface eglSurface;
        if (info == null) {
            try {
                eglSurface = EglTool.createEGLSurface(eglDisplay, eglConfig, surface);
            } catch (Exception e) {
                Log.w(TAG, "putSurface fail: " + e, e);
                return;
            }
        } else {
            eglSurface = info.eglSurface;
        }
        eglSurfaceMap.put(surface, new EGLSurfaceHolder(
                surface,
                width,
                height,
                eglSurface,
                format,
                transform));
        Log.i(TAG, "putSurface: " +
                "format = " + format + ", " +
                "width = " + width + ", " +
                "height = " + height + ", " +
                "surface = " + surface + ", " +
                "allSurfaceCount = " + eglSurfaceMap.size());
    }

    public void removeSurface(Surface surface) {
        if (surface == null || isRelease) {
            return;
        }
        EGLSurfaceHolder info = eglSurfaceMap.get(surface);
        if (info != null && info.eglSurface != EGL14.EGL_NO_SURFACE) {
            EglTool.releaseSurface(eglDisplay, eglContext, info.eglSurface);
            eglSurfaceMap.remove(surface);
        }
    }

    public boolean hasSurface(Surface surface) {
        return eglSurfaceMap.containsKey(surface);
    }

    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public EGLContext getEGLContext() {
        return eglContext;
    }

    public int createOESTextureObject() {
        return EglTool.createOESTexture();
    }

    public static class EGLSurfaceHolder {
        public final Surface surface;
        public final int surfaceWidth;
        public final int surfaceHeight;
        public final FrameFormat format;
        public final Transform transform;
        public final EGLSurface eglSurface;


        public EGLSurfaceHolder(Surface surface,
                                int width,
                                int height,
                                EGLSurface eglSurface,
                                FrameFormat format,
                                Transform transform) {
            this.surface = surface;
            this.eglSurface = eglSurface;
            this.surfaceWidth = width;
            this.surfaceHeight = height;
            this.format = format;
            this.transform = transform;
        }

        public int getSurfaceWidth(EGLDisplay display) {
            if (surfaceWidth > 0) {
                return surfaceWidth;
            } else {
                return EglTool.getSurfaceWidth(display, eglSurface);
            }
        }

        public int getSurfaceHeight(EGLDisplay display) {
            if (surfaceHeight > 0) {
                return surfaceHeight;
            } else {
                return EglTool.getSurfaceHeight(display, eglSurface);
            }
        }
    }
}

