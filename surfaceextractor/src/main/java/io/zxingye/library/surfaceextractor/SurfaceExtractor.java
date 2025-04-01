package io.zxingye.library.surfaceextractor;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

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

    private final Map<OnFrameListener, EglFrameReaderHelper> onFrameListenerMap = new HashMap<>();
    private final float[] textureMatrix = new float[16];
    private final int[] texSize = new int[2];
    private final EglCore eglCore;
    private final SurfaceTexture inputSurfaceTexture;
    private final Handler eglHandler;
    private int oesTextureId;
    private Size inputSize;

    private SurfaceExtractor(EglCore eglCore, int oesTextureId, Handler handler) {
        this.eglCore = eglCore;
        this.oesTextureId = oesTextureId;
        this.eglHandler = handler;
        this.inputSurfaceTexture = new InnerSurfaceTexture(oesTextureId, this::drawSurface, eglHandler);
        this.inputSize = new Size(-1, -1);
        Log.i(TAG, "create: " + handler.getLooper().getThread().getName());
    }

    public void release() {
        run(() -> {
            for (EglFrameReaderHelper imageReader : onFrameListenerMap.values()) {
                imageReader.close();
            }
            onFrameListenerMap.clear();
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

    public void putOutputSurface(Surface surface,
                                 Transform transform) {
        putOutputSurface(surface, new Size(-1, -1), transform);
    }

    public void putOutputSurface(Surface surface,
                                 Size surfaceSize,
                                 Transform transform) {
        run(() -> eglCore.putSurface(surface, surfaceSize, transform));
    }

    public void removeOutputSurface(Surface surface) {
        awaitRun(() -> eglCore.removeSurface(surface));
    }

    public void addOnFrameListener(FrameFormat format,
                                   Transform transform,
                                   OnFrameListener listener) {
        addOnFrameListener(format, false, transform, listener);
    }

    public void addOnFrameListener(FrameFormat format,
                                   boolean directBuffer,
                                   Transform transform,
                                   OnFrameListener listener) {
        addOnFrameListener(format, new Size(-1, -1), directBuffer, transform, listener);
    }

    public void addOnFrameListener(FrameFormat format,
                                   Size outputSize,
                                   boolean directBuffer,
                                   Transform transform,
                                   OnFrameListener listener) {
        if (listener == null) {
            return;
        }
        removeOnFrameListener(listener);
        run(() -> {
            EglFrameReaderHelper imageReader = new EglFrameReaderHelper(
                    format, outputSize, directBuffer, new EglFrameReaderHelper.Adapter() {
                @Override
                public EglFrameReader onCreate(FrameFormat format, Size size, boolean directBuffer) {
                    EglFrameReader reader = eglCore.createFrameReader(format, size, directBuffer);
                    run(() -> eglCore.putFrameReader(reader, transform));
                    return reader;
                }

                @Override
                public void onClose(EglFrameReader reader) {
                    run(() -> eglCore.removeFrameReader(reader));
                }

                @Override
                public void onFrame(ByteBuffer frame, Size resolution, FrameFormat format) {
                    listener.onFrame(frame, resolution, format);
                }
            });
            imageReader.updateInputSize(inputSize);
            onFrameListenerMap.put(listener, imageReader);
        });
    }

    public void removeOnFrameListener(OnFrameListener listener) {
        awaitRun(() -> {
            EglFrameReaderHelper helper = onFrameListenerMap.remove(listener);
            if (helper != null) {
                helper.close();
            }
        });
    }

    public void setBackgroundColor(int backgroundColor) {
        run(() -> eglCore.setBackgroundColor(backgroundColor));
    }

    private void onInputSizeChange(int width, int height) {
        inputSize = new Size(width, height);
        Log.i(TAG, "onInputSizeChange: " + width + " x " + height);
        for (EglFrameReaderHelper imageReader : onFrameListenerMap.values()) {
            imageReader.updateInputSize(inputSize);
        }
    }

    private void drawSurface(SurfaceTexture surfaceTexture) {
        try {
            surfaceTexture.updateTexImage();
            surfaceTexture.getTransformMatrix(textureMatrix);
            texSize[0] = 0;
            texSize[1] = 0;
            if (EglTool.getRealOESTexSize(oesTextureId, textureMatrix, texSize)) {
                if (texSize[0] != inputSize.getWidth() || texSize[1] != inputSize.getHeight()) {
                    onInputSizeChange(texSize[0], texSize[1]);
                }
            }
            eglCore.drawOESTexture(oesTextureId, inputSize, textureMatrix);
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