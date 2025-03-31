package io.zxingye.library.surfaceextractor;

public interface OnImageFrameListener {
    void onFrame(byte[] frameData, int offset, int length, int width, int height, FrameFormat format);
}
