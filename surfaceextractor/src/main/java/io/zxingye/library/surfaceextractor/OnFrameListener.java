package io.zxingye.library.surfaceextractor;

import android.util.Size;

import java.nio.ByteBuffer;

public interface OnFrameListener {
    void onFrame(ByteBuffer frame, Size resolution, FrameFormat format);
}
