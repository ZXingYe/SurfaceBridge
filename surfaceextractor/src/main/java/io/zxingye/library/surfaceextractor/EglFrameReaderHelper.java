package io.zxingye.library.surfaceextractor;

import android.os.Handler;
import android.util.Log;
import android.util.Size;

import java.nio.ByteBuffer;
import java.util.Objects;

public class EglFrameReaderHelper implements AutoCloseable {
    private static final String TAG = "EglImageReader";
    private final Adapter adapter;
    private final FrameFormat format;
    private final Size outputSize;
    private final Handler dispatcherHandler;
    private final boolean directBuffer;
    private EglFrameReader frameReader;

    public EglFrameReaderHelper(FrameFormat format,
                                Size outputSize,
                                boolean directBuffer,
                                Adapter adapter) {
        this.adapter = Objects.requireNonNull(adapter);
        this.format = Objects.requireNonNull(format);
        this.outputSize = Objects.requireNonNull(outputSize);
        this.directBuffer = directBuffer;
        this.dispatcherHandler = Util.createWorkHandler(TAG + "_" + format);
        Log.i(TAG, "create :" + " format = " + format + ", hashCode = " + hashCode());

        updateInputSize(outputSize);
    }

    @Override
    public synchronized void close() {
        if (frameReader != null) {
            frameReader.close();
            adapter.onClose(frameReader);
            frameReader = null;
        }
        dispatcherHandler.removeCallbacksAndMessages(null);
        dispatcherHandler.getLooper().quit();
        Log.i(TAG, "close," + hashCode());
    }

    public synchronized void updateInputSize(Size inputSize) {
        int realWidth = outputSize.getWidth() > 0 ? outputSize.getWidth() : inputSize.getWidth();
        int realHeight = outputSize.getHeight() > 0 ? outputSize.getHeight() : inputSize.getHeight();
        if (frameReader != null && Objects.requireNonNull(inputSize).equals(frameReader.getSize())) {
            return;
        }
        if (frameReader != null) {
            adapter.onClose(frameReader);
            frameReader.close();
            frameReader = null;
        }
        if (realWidth <= 0 || realHeight <= 0) {
            return;
        }
        Log.i(TAG, "updateInputSize: " + realWidth + " x " + realHeight + ", format = " + format);
        frameReader = adapter.onCreate(format, new Size(realWidth, realHeight), directBuffer);
        if (frameReader == null) {
            throw new UnsupportedOperationException("create frameReader fail: " + format);
        }

        frameReader.setOnFrameListener(adapter::onFrame, dispatcherHandler);
    }

    public interface Adapter {
        EglFrameReader onCreate(FrameFormat format, Size size, boolean directBuffer);

        void onClose(EglFrameReader reader);

        void onFrame(ByteBuffer frame, Size resolution, FrameFormat format);
    }
}
