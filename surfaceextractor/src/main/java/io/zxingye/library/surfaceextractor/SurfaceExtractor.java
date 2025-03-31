package io.zxingye.library.surfaceextractor;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import io.zxingye.library.surfaceextractor.transform.Transform;

public class SurfaceExtractor {

    private static final String TAG = "GLFrameDispatcher";

    public static SurfaceExtractor create() {
        return create("SurfaceExtractor");
    }

    public static SurfaceExtractor create(String threadName) {
        Handler eglHandler = Util.createWorkHandler(threadName);
        SurfaceExtractor[] result = new SurfaceExtractor[1];
        Util.runOn(eglHandler, () -> {
            EglCore core = EglCore.create(null);
            int oesTextureId;
            if (core != null) {
                oesTextureId = core.createOESTextureObject();
                if (oesTextureId > 0) {
                    result[0] = new SurfaceExtractor(core, oesTextureId, eglHandler);
                } else {
                    core.close();
                }
            }
        }, true);
        return result[0];
    }

    private final List<EglImageReader> imageReaderList = new ArrayList<>();
    private final float[] textureMatrix = new float[16];
    private final EglCore eglCore;
    private final SurfaceTexture inputSurfaceTexture;
    private final Handler eglHandler;
    private int oesTextureId;
    private int inputWidth;
    private int inputHeight;

    private SurfaceExtractor(EglCore eglCore, int oesTextureId, Handler handler) {
        this.eglCore = eglCore;
        this.oesTextureId = oesTextureId;
        this.eglHandler = handler;
        this.inputSurfaceTexture = new InnerSurfaceTexture(oesTextureId, this::drawSurface, eglHandler);
        Log.i(TAG, "create: " + handler.getLooper().getThread().getName());
    }

    public synchronized void release() {
        run(() -> {
            for (EglImageReader imageReader : imageReaderList) {
                imageReader.release();
            }
            imageReaderList.clear();
            if (inputSurfaceTexture != null) {
                inputSurfaceTexture.release();
            }
            if (eglCore != null) {
                eglCore.deleteOESTextureObject(oesTextureId);
                eglCore.close();
            }
            eglHandler.getLooper().quit();
            oesTextureId = -1;
        });

    }

    public SurfaceTexture getInputSurfaceTexture() {
        return inputSurfaceTexture;
    }

    public void setDefaultInputBufferSize(int width, int height) {
        inputSurfaceTexture.setDefaultBufferSize(width, height);
    }

    public void putOutputSurface(Surface surface, Transform transform) {
        putOutputSurface(surface, -1, -1, transform);
    }

    public void putOutputSurface(Surface surface, int width, int height, Transform transform) {
        run(() -> eglCore.putSurface(surface, width, height, FrameFormat.RGBA_8888, transform));
    }

    public void removeOutputSurface(Surface surface) {
        awaitRun(() -> eglCore.removeSurface(surface));
    }

    public boolean hasOutputSurface(Surface surface) {
        return eglCore != null && eglCore.hasSurface(surface);
    }

    public void addOnFrameListener(FrameFormat format, Transform transform, OnImageFrameListener listener) {
        addOnFrameListener(format, -1, -1, transform, listener);
    }

    public void addOnFrameListener(FrameFormat format,
                                   int outputWidth,
                                   int outHeight,
                                   Transform transform,
                                   OnImageFrameListener listener) {
        if (listener == null) {
            return;
        }
        removeOnFrameListener(format, listener);
        run(() -> {
            EglImageReader imageReader = new EglImageReader(format, outputWidth, outHeight);
            imageReader.onCreateListener = (surface, width, height) ->
                    run(() -> eglCore.putSurface(surface, width, height, format, transform));
            imageReader.onCloseListener = SurfaceExtractor.this::removeOutputSurface;
            imageReader.onFrameListener = listener;
            imageReader.updateInputSize(inputWidth, inputHeight);
            imageReaderList.add(imageReader);
        });
    }

    public void removeOnFrameListener(OnImageFrameListener listener) {
        removeOnFrameListener(null, listener);
    }

    public void removeOnFrameListener(FrameFormat format, OnImageFrameListener listener) {
        awaitRun(() -> {
            Iterator<EglImageReader> iterator = imageReaderList.iterator();
            while (iterator.hasNext()) {
                EglImageReader reader = iterator.next();
                if (Objects.equals(listener, reader.onFrameListener)
                        && (format == null || format == reader.getFormat())) {
                    iterator.remove();
                    reader.release();
                }
            }
        });
    }

    public void setBackgroundColor(int backgroundColor) {
        run(() -> eglCore.setBackgroundColor(backgroundColor));
    }

    private void onInputSizeChange(int width, int height) {
        if (inputWidth == width && inputHeight == height) {
            return;
        }
        Log.i(TAG, "onInputSizeChange: " + width + " x " + height);
        inputWidth = width;
        inputHeight = height;
        for (EglImageReader imageReader : imageReaderList) {
            imageReader.updateInputSize(width, height);
        }
    }

    private void drawSurface(SurfaceTexture surfaceTexture) {
        try {
            surfaceTexture.updateTexImage();
            surfaceTexture.getTransformMatrix(textureMatrix);
            int[] texSize = new int[2];
            if (EglTool.getRealOESTexSize(oesTextureId, textureMatrix, texSize)) {
                int texWidth = texSize[0];
                int texHeight = texSize[1];
                if (texWidth >= 0 && texWidth != inputWidth && texHeight >= 0 && texHeight != inputHeight) {
                    onInputSizeChange(texWidth, texHeight);
                }
            }
            eglCore.drawOESTexture(oesTextureId, inputWidth, inputHeight, textureMatrix);
        } catch (Exception e) {
            Log.w(TAG, "drawSurface fail: " + e, e);
        }
    }

    private void run(Runnable runnable) {
        Util.runOn(eglHandler, runnable, false);
    }

    private void awaitRun(Runnable runnable) {
        Util.runOn(eglHandler, runnable, true);
    }

    private class InnerSurfaceTexture extends SurfaceTexture {
        public InnerSurfaceTexture(int texName, OnFrameAvailableListener listener, Handler handler) {
            super(texName);
            super.setOnFrameAvailableListener(listener, handler);
        }

        @Override
        public void setDefaultBufferSize(int width, int height) {
            run(() -> {
                super.setDefaultBufferSize(width, height);
                onInputSizeChange(width, height);
            });
        }

        @Override
        public void setOnFrameAvailableListener(OnFrameAvailableListener listener, Handler handler) {
            throw new UnsupportedOperationException();
        }
    }
}