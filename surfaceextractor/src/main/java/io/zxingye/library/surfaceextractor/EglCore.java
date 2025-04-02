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
import android.util.Size;
import android.view.Surface;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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


    private final Map<Surface, EglSurfaceHolder> eglSurfaceMap = new HashMap<>();
    private final Map<FrameFormat, EglProgram> eglProgramMap = new HashMap<>();
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

            eglBOHolder = new EglBufferObjectHolder();

            EglProgram[] supportProgram = {
                    new EglProgramRGBA(eglBOHolder),
                    new EglProgramRGBX(eglBOHolder),
                    new EglProgramBGRA(eglBOHolder),
                    new EglProgramBGRX(eglBOHolder),
                    new EglProgramRGB888(eglBOHolder),
                    new EglProgramYU12(eglBOHolder),
                    new EglProgramYV12(eglBOHolder),
                    new EglProgramNV12(eglBOHolder),
                    new EglProgramNV21(eglBOHolder),
                    new EglProgramI444(eglBOHolder),
                    new EglProgramYUVY(eglBOHolder),
                    new EglProgramYUYV(eglBOHolder),
                    new EglProgramYVYU(eglBOHolder),
                    new EglProgramUYVY(eglBOHolder),
                    new EglProgramVYUY(eglBOHolder),
            };

            for (EglProgram program : supportProgram) {
                if (eglProgramMap.containsKey(program.getFrameFormat())) {
                    throw new GLException(-1, "repeated format");
                }
                eglProgramMap.put(program.getFrameFormat(), program);
            }
            Log.i(TAG, "version = " + GLES20.glGetString(GLES20.GL_VERSION)
                    + ", supportFormat = " + eglProgramMap.keySet());
        } catch (Throwable e) {
            close();
            throw e;
        }
    }

    @Override
    public void close() {
        if (!isRelease) {
            EglTool.releaseEGLSurfaceHolder(eglDisplay, eglSurfaceMap.values());
            EglTool.releaseEglProgram(eglProgramMap.values());
            eglBOHolder.close();
            EglTool.release(eglDisplay, eglContext);
            eglProgramMap.clear();
            eglSurfaceMap.clear();
            isRelease = true;
        }
    }

    public int createOESTextureObject() {
        return EglTool.createOESTexture();
    }

    public void deleteOESTextureObject(int textureID) {
        if (!isRelease) {
            EglTool.deleteOESTexture(textureID);
        }
    }

    public void drawOESTexture(int texId, Size texSize, float[] texMatrix) {
        if (isRelease || eglSurfaceMap.isEmpty()) {
            return;
        }
        if (texSize.getWidth() <= 0 || texSize.getHeight() <= 0) {
            Log.w(TAG, "skip draw: texture size = 0");
            return;
        }

        EglTool.bindOESTexture(texId, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE0);

        eglBOHolder.updateTexMatrixUbo(texMatrix);

        for (EglSurfaceHolder surfaceHolder : eglSurfaceMap.values()) {
            if (!surfaceHolder.surface.isValid()) {
                Log.w(TAG, "skip draw: surface is invalid: " + surfaceHolder.surface);
                continue;
            }
            EglProgram program = eglProgramMap.get(surfaceHolder.format);
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
            float[] vertex = eglVertexCoord.getVertexCoord(texSize.getWidth(), texSize.getHeight(), surfaceHolder.transform);
            eglBOHolder.updateVertexCoordinate(vertex);

            EglTool.makeCurrent(eglDisplay, eglContext, surfaceHolder.eglSurface);
            EglTool.clearColor(backgroundColor, GLES20.GL_COLOR_BUFFER_BIT);

            program.draw(surfaceWidth, surfaceHeight);

            if (!EGL14.eglSwapBuffers(eglDisplay, surfaceHolder.eglSurface)) {
                throw new GLException(-1, "eglSwapBuffers fail: " + surfaceHolder.surface + " " + surfaceHolder.eglSurface);
            }
        }
    }

    public EglFrameReader createFrameReader(FrameFormat format, Size size, boolean directBuffer) {
        EglProgram eglProgram = eglProgramMap.get(format);
        if (eglProgram == null) {
            return null;
        }
        return eglProgram.createEglFrameReader(size, directBuffer);
    }

    public void putFrameReader(EglFrameReader reader, Transform transform) {
        updateSurface(
                reader.getSurface(),
                reader.getSize(),
                reader.getFormat(),
                transform);
    }

    public void removeFrameReader(EglFrameReader reader) {
        updateSurface(reader.getSurface(), null, null, null);
    }

    public void putSurface(Surface surface, Size surfaceSize, Transform transform) {
        updateSurface(surface, surfaceSize, FrameFormat.RGBA_8888, transform);
    }

    public void removeSurface(Surface surface) {
        updateSurface(surface, null, null, null);
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

    private void updateSurface(Surface surface, Size surfaceSize, FrameFormat format, Transform transform) {
        if (isRelease) {
            return;
        }
        if (!Objects.requireNonNull(surface).isValid() || surfaceSize == null) {
            EglSurfaceHolder info = eglSurfaceMap.get(surface);
            if (info != null && info.eglSurface != EGL14.EGL_NO_SURFACE) {
                EglTool.releaseSurface(eglDisplay, eglContext, info.eglSurface);
                eglSurfaceMap.remove(surface);
            }
            return;
        }
        if (!eglProgramMap.containsKey(format)) {
            Log.w(TAG, "updateSurface fail",
                    new IllegalArgumentException("format is not supported, format = " + format));
            return;
        }
        EglSurfaceHolder info = eglSurfaceMap.get(surface);
        EGLSurface eglSurface;
        if (info == null) {
            try {
                eglSurface = EglTool.createEGLSurface(eglDisplay, eglConfig, surface);
            } catch (Exception e) {
                Log.w(TAG, "updateSurface fail: " + e, e);
                return;
            }
        } else {
            eglSurface = info.eglSurface;
        }
        eglSurfaceMap.put(surface, new EglSurfaceHolder(
                surface,
                surfaceSize,
                eglSurface,
                format,
                transform));
        Log.i(TAG, "updateSurface: " +
                "format = " + format + ", " +
                "size = " + surfaceSize + ", " +
                "surface = " + surface + ", " +
                "currentSurfaceCount = " + eglSurfaceMap.size());
    }


    protected static class EglSurfaceHolder {
        public final Surface surface;
        public final Size surfaceSize;
        public final FrameFormat format;
        public final Transform transform;
        public final EGLSurface eglSurface;


        public EglSurfaceHolder(Surface surface,
                                Size surfaceSize,
                                EGLSurface eglSurface,
                                FrameFormat format,
                                Transform transform) {
            this.surface = surface;
            this.eglSurface = eglSurface;
            this.surfaceSize = surfaceSize;
            this.format = format;
            this.transform = transform;
        }

        public int getSurfaceWidth(EGLDisplay display) {
            if (surfaceSize != null && surfaceSize.getWidth() > 0) {
                return surfaceSize.getWidth();
            } else {
                return EglTool.getSurfaceWidth(display, eglSurface);
            }
        }

        public int getSurfaceHeight(EGLDisplay display) {
            if (surfaceSize != null && surfaceSize.getHeight() > 0) {
                return surfaceSize.getHeight();
            } else {
                return EglTool.getSurfaceHeight(display, eglSurface);
            }
        }
    }
}

