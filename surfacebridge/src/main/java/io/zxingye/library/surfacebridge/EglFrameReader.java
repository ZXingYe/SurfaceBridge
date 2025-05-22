package io.zxingye.library.surfacebridge;

import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.nio.ByteBuffer;

public class EglFrameReader implements AutoCloseable {

    private static final String TAG = "EglFrameReader";

    private final ImageReader reader;
    private final FrameFormat format;
    private final Size outputSize;
    private final boolean directBuffer;
    private ByteBuffer frameDataBuffer;

    public EglFrameReader(ImageReader reader,
                          FrameFormat format,
                          Size outputSize,
                          boolean directBuffer) {
        this.reader = reader;
        this.format = format;
        this.outputSize = outputSize;
        this.directBuffer = directBuffer;
    }

    @Override
    public void close() {
        reader.close();
    }

    public void setOnFrameListener(OnFrameListener listener, Handler handler) {
        if (listener == null) {
            reader.setOnImageAvailableListener(null, null);
        } else {
            reader.setOnImageAvailableListener(reader -> {
                ByteBuffer frameData = acquireLNextFrame();
                if (frameData != null) {
                    listener.onFrame(frameData, outputSize, format);
                }
            }, handler);
        }
    }

    public Surface getSurface() {
        return reader.getSurface();
    }

    public Size getSize() {
        return outputSize;
    }

    public FrameFormat getFormat() {
        return format;
    }

    public ByteBuffer acquireLNextFrame() {
        try (Image image = reader.acquireLatestImage()) {
            if (image == null) {
                return null;
            }
            Image.Plane[] planes = image.getPlanes();
            if (planes == null || planes.length == 0) {
                return null;
            }
            ByteBuffer data = planes[0].getBuffer();
            int maxReadLength = data.remaining();
            if (maxReadLength <= 0) {
                return null;
            }
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * imageWidth;
            if (frameDataBuffer == null || frameDataBuffer.capacity() < data.remaining()) {
                if (directBuffer) {
                    frameDataBuffer = ByteBuffer.allocateDirect(maxReadLength);
                } else {
                    frameDataBuffer = ByteBuffer.allocate(maxReadLength);
                }
            }
            frameDataBuffer.clear();
            int maxLimit = data.limit();
            int eachReadLength = imageWidth * pixelStride;
            for (int readHeight = 0; readHeight < imageHeight && data.remaining() > 0; readHeight++) {
                int readLength = Math.min(data.remaining(), eachReadLength);
                data.limit(data.position() + readLength);
                frameDataBuffer.put(data);
                data.limit(maxLimit);
                int skipLength = Math.min(data.remaining(), rowPadding);
                if (skipLength > 0) {
                    data.position(data.position() + skipLength);
                }
            }
            return (ByteBuffer) frameDataBuffer.asReadOnlyBuffer().rewind();
        } catch (Exception e) {
            Log.e(TAG, "acquireLNextFrame fail: " + e);
            return null;
        }
    }

}