package io.zxingye.library.surfaceextractor.demo.source;

import android.graphics.SurfaceTexture;

public interface TexSource {

    void start(SurfaceTexture surfaceTexture, OnSurfaceStateListener listener);

    void stop();

    interface OnSurfaceStateListener {
        void onSizeChange(int width, int height,int rotation);

        void onError(Throwable e);
    }
}
