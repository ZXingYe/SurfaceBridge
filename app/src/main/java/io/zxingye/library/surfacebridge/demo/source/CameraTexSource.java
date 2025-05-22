package io.zxingye.library.surfacebridge.demo.source;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import java.util.List;

public class CameraTexSource implements TexSource {

    private static final int DEFAULT_LENS = Camera.CameraInfo.CAMERA_FACING_BACK;

    private static final String TAG = "CameraSurfaceSource";

    private final WindowManager windowManager;
    private Camera camera;

    public CameraTexSource(Context context) {
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    public void start(SurfaceTexture surface, OnSurfaceStateListener listener) {
        try {
            int cameraId = getCameraId();
            if (cameraId >= 0) {
                camera = Camera.open(cameraId);
            }
            if (camera == null) {
                Log.w(TAG, "open camera fail");
                return;
            }

            Camera.Parameters params = camera.getParameters();
            List<Camera.Size> supportedSizes = params.getSupportedPreviewSizes();
            if (supportedSizes == null || supportedSizes.isEmpty()) {
                camera.release();
                Log.w(TAG, "supportedSizes is empty");
                return;
            }
            Camera.Size size = supportedSizes.get(0);
            params.setPreviewSize(size.width, size.height);
            listener.onSizeChange(size.width, size.height, 0);
            if (params.getSupportedFocusModes().contains(
                    Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }
            camera.setParameters(params);
            camera.setDisplayOrientation(getDisplayOrientation(cameraId));
            camera.setErrorCallback(new Camera.ErrorCallback() {
                @Override
                public void onError(int error, Camera camera) {
                    error(listener, new Exception("error = " + error));
                }
            });
            camera.setPreviewTexture(surface);
            camera.startPreview();
        } catch (Exception e) {
            error(listener, e);
        }
    }

    @Override
    public void stop() {
        if (camera != null) {
            camera.release();
            camera = null;
        }
    }

    private void error(OnSurfaceStateListener listener, Throwable e) {
        if (listener != null) {
            listener.onError(e);
        }
        stop();
    }

    private int getCameraId() {
        int numberOfCameras = Camera.getNumberOfCameras();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        int cameraId = -1;
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            cameraId = i;
            if (DEFAULT_LENS == cameraInfo.facing) {
                break;
            }

        }
        return cameraId;
    }

    private int getDisplayOrientation(int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = windowManager.getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }


}
