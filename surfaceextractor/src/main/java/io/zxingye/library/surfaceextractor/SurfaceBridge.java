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

public class SurfaceBridge {

    private static final String TAG = "GLFrameDispatcher";

    public static SurfaceBridge create() {
        return create("SurfaceExtractor");
    }

    public static SurfaceBridge create(String threadName) {
        Handler eglHandler = Util.createWorkHandler(threadName);
        SurfaceBridge[] result = new SurfaceBridge[1];
        Util.runOn(eglHandler, () -> {
            EglCore core = EglCore.create(null);
            int oesTextureId;
            if (core != null) {
                oesTextureId = core.createOESTextureObject();
                if (oesTextureId > 0) {
                    result[0] = new SurfaceBridge(core, oesTextureId, eglHandler);
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

    private SurfaceBridge(EglCore eglCore, int oesTextureId, Handler handler) {
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

    /**
     * 添加一个输出Surface，可以多次重复put同一个surface，内部会做参数覆盖
     * 支持指定输出的大小，如果大小是负值，表示不指定，这个时候内部会动态获取实际的surface大小
     *
     * @param surface     用于输出的surface，常来源于SurfaceView或TextureView等。
     * @param surfaceSize 指定输出的大小，不能为空，但是可以为负值。
     * @param transform   用于做输出变换，比如放大缩小、平移、旋转等操作，如果为null表示不做任何变换
     */
    public void putOutputSurface(Surface surface,
                                 Size surfaceSize,
                                 Transform transform) {
        run(() -> eglCore.putSurface(surface, surfaceSize, transform));
    }

    public void removeOutputSurface(Surface surface) {
        awaitRun(() -> eglCore.removeSurface(surface));
    }

    public void addOnFrameListener(FrameFormat format,
                                   OnFrameListener listener) {
        addOnFrameListener(format, new Size(-1, -1), null, listener);
    }

    /**
     * 添加一个Listener用于监听帧输出，可以指定帧输出的格式、大小、变换
     *
     * @param format     输出的格式
     * @param outputSize 输出的大小，不能为null，但是可以为负值，表示输出原始大小。
     * @param transform  帧图像的变换，如果不为空会在输出帧之前进行图像变换，一般用于各种缩放适配。
     * @param listener   监听器，不同的格式可以公用一个监听器。
     */
    public void addOnFrameListener(FrameFormat format,
                                   Size outputSize,
                                   Transform transform,
                                   OnFrameListener listener) {
        addOnFrameListener(format, outputSize, transform, listener, false);
    }

    public void addOnFrameListener(FrameFormat format,
                                   Size outputSize,
                                   Transform transform,
                                   OnFrameListener listener,
                                   boolean directBuffer) {
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

    public void setYUVColorSpace(EglYUVColorSpace colorSpace) {
        run(() -> eglCore.setYUVColorSpace(colorSpace));
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