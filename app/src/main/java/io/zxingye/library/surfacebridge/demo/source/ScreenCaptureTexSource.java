package io.zxingye.library.surfacebridge.demo.source;

import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.view.Surface;

import io.zxingye.library.surfacebridge.demo.ScreenCaptureService;

public class ScreenCaptureTexSource implements TexSource {

    private final Context context;
    private final Intent screenCaptureIntent;

    private Surface surface;
    private ScreenCaptureService captureService;

    public ScreenCaptureTexSource(Context context, Intent screenCaptureIntent) {
        this.context = context;
        this.screenCaptureIntent = screenCaptureIntent;
    }

    @Override
    public void start(SurfaceTexture surfaceTexture, OnSurfaceStateListener listener) {
        surface = new Surface(surfaceTexture);
        ScreenCaptureService.addCaptureStateListener(new ScreenCaptureService.OnCaptureStateListener() {
            @Override
            public void onCaptureStart(ScreenCaptureService service) {
                captureService = service;
            }

            @Override
            public void onCaptureSizeChange(int width, int height) {
                listener.onSizeChange(width, height, 0);
            }

            @Override
            public void onCaptureFinish() {
                ScreenCaptureService.removeCaptureStateListener(this);
            }
        });
        ScreenCaptureService.startScreenCaptureService(context, screenCaptureIntent, surface);
    }

    @Override
    public void stop() {
        ScreenCaptureService.stopScreenCaptureService(context);
        captureService.stopCapture();
        if (surface != null) {
            surface.release();
            surface = null;
        }
    }
}
