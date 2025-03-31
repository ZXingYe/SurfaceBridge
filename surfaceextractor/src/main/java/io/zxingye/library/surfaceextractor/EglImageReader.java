package io.zxingye.library.surfaceextractor;

import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.Objects;

public class EglImageReader {
    private static final String TAG = "EglImageReader";
    private final FrameFormat format;
    private final Handler dispatcherHandler;
    private final int outputWidth;
    private final int outputHeight;
    private ImageReader imageReader;
    private int imageWidth;
    private int imageHeight;
    private byte[] frameDataBuffer;

    public OnCreateListener onCreateListener;
    public OnCloseListener onCloseListener;
    public OnImageFrameListener onFrameListener;

    public EglImageReader(FrameFormat format, int outputWidth, int outputHeight) {
        this.format = Objects.requireNonNull(format);
        this.outputWidth = outputWidth;
        this.outputHeight = outputHeight;
        this.dispatcherHandler = Util.createWorkHandler(TAG + "_" + format);
        Log.i(TAG, "create :" + " format = " + format + ", hashCode = " + hashCode());

        updateInputSize(outputWidth, imageHeight);
    }

    public synchronized void updateInputSize(int width, int height) {
        int realWidth = outputWidth > 0 ? outputWidth : width;
        int realHeight = outputHeight > 0 ? outputHeight : height;
        if (realWidth == imageWidth && realHeight == imageHeight) {
            return;
        }
        imageWidth = realWidth;
        imageHeight = realHeight;
        if (imageReader != null) {
            notifySurfaceClose(imageReader.getSurface());
            imageReader.close();
            imageReader = null;
        }
        if (realWidth <= 0 || realHeight <= 0) {
            return;
        }
        Log.i(TAG, "updateInputSize: " + realWidth + " x " + realHeight + ", format = " + format);
        switch (format) {
            case RGBA_8888:
                imageReader = EglProgramRGBA.createImageReader(realWidth, realHeight);
                break;
            case YU12:
                imageReader = EglProgramYU12.createImageReader(realWidth, realHeight);
                break;
            case YV12:
                imageReader = EglProgramYV12.createImageReader(realWidth, realHeight);
                break;
            case I444:
                imageReader = EglProgramI444.createImageReader(realWidth, realHeight);
                break;
            case NV12:
                imageReader = EglProgramNV12.createImageReader(realWidth, realHeight);
                break;
            case NV21:
                imageReader = EglProgramNV21.createImageReader(realWidth, realHeight);
                break;
            case YUVY:
                imageReader = EglProgramYUVY.createImageReader(realWidth, realHeight);
                break;
            case YUYV:
                imageReader = EglProgramYUYV.createImageReader(realWidth, realHeight);
                break;
            case YVYU:
                imageReader = EglProgramYVYU.createImageReader(realWidth, realHeight);
                break;
            case UYVY:
                imageReader = EglProgramUYVY.createImageReader(realWidth, realHeight);
            case VYUY:
                imageReader = EglProgramVYUY.createImageReader(realWidth, realHeight);
                break;
            default:
                throw new IllegalArgumentException("Unknown format: " + format);
        }

        imageReader.setOnImageAvailableListener(reader ->
                dispatchImage(reader, realWidth, realHeight), dispatcherHandler);
        notifySurfaceUpdate(imageReader.getSurface(), realWidth, realHeight);
    }

    public synchronized void release() {
        updateInputSize(0, 0);
        dispatcherHandler.getLooper().quit();
        Log.i(TAG, "release," + hashCode());
    }

    public FrameFormat getFormat() {
        return format;
    }

    private void dispatchImage(ImageReader reader, int width, int height) {
        int dataLength = 0;
        synchronized (this) {
            try (Image image = reader.acquireLatestImage()) {
                if (image == null) {
                    return;
                }
                Image.Plane[] planes = image.getPlanes();
                if (planes == null || planes.length == 0) {
                    return;
                }
                ByteBuffer data = planes[0].getBuffer();
                int imageWidth = image.getWidth();
                int imageHeight = image.getHeight();
                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * imageWidth;
                if (frameDataBuffer == null || frameDataBuffer.length < data.remaining()) {
                    int bufferSize = frameDataBuffer == null ? 0 : frameDataBuffer.length;
                    frameDataBuffer = new byte[(int) Math.max(bufferSize * 1.5, data.remaining())];
                }
                int eachReadLength = imageWidth * pixelStride;
                for (int readHeight = 0; readHeight < imageHeight && data.remaining() > 0; readHeight++) {
                    int readLength = Math.min(data.remaining(), eachReadLength);
                    data.get(frameDataBuffer, dataLength, readLength);
                    dataLength += readLength;
                    int skipLength = Math.min(data.remaining(), rowPadding);
                    if (skipLength > 0) {
                        data.position(data.position() + skipLength);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "dispatcher frameData fail: " + e);
                return;
            }
        }
        notifyFrameUpdate(frameDataBuffer, 0, dataLength, width, height, format);
    }

    private void notifySurfaceUpdate(Surface surface, int width, int height) {
        OnCreateListener updateListener = onCreateListener;
        if (updateListener != null) {
            updateListener.onCreate(surface, width, height);
        }
    }

    private void notifySurfaceClose(Surface surface) {
        OnCloseListener closeListener = onCloseListener;
        if (closeListener != null) {
            closeListener.onClose(surface);
        }
    }

    private void notifyFrameUpdate(byte[] frameData, int offset, int length, int width, int height, FrameFormat format) {
        OnImageFrameListener frameListener = onFrameListener;
        if (frameListener != null) {
            frameListener.onFrame(frameData, offset, length, width, height, format);
        }
    }

    public interface OnCreateListener {
        void onCreate(Surface surface, int width, int height);
    }

    public interface OnCloseListener {
        void onClose(Surface surface);
    }
}
