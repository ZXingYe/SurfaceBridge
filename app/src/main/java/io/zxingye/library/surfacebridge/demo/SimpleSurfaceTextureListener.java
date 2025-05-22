package io.zxingye.library.surfacebridge.demo;

import android.graphics.SurfaceTexture;
import android.view.TextureView;

import androidx.annotation.NonNull;

public class SimpleSurfaceTextureListener implements TextureView.SurfaceTextureListener {
    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {

    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

    }
}
