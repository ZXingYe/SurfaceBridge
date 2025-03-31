package io.zxingye.library.surfaceextractor.demo.source;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.text.TextUtils;
import android.util.Pair;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressLint("MissingPermission")
public class Camera2TexSource implements TexSource {

    private static final int DEFAULT_LENS = CameraCharacteristics.LENS_FACING_BACK;

    private final WindowManager windowManager;
    private final CameraManager cameraManager;
    private Surface surface;
    private CameraDevice cameraDevice;
    public Camera2TexSource(Context context) {
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    public void start(SurfaceTexture surfaceTexture, OnSurfaceStateListener listener) {
        this.surface = new Surface(surfaceTexture);
        try {
            Pair<String, CameraCharacteristics> cameraInfo = getCameraInfo(DEFAULT_LENS);
            if (cameraInfo == null) {
                throw new Exception("No available camera could be found");
            }
            Size previewSize = getPreviewSize(cameraInfo.second);
            if (previewSize == null) {
                throw new Exception("No available previewSize could be found");
            }
            cameraManager.openCamera(cameraInfo.first, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    listener.onSizeChange(previewSize.getWidth(),
                            previewSize.getHeight(),
                            getWindowsRotation());
                    createPreviewSession(camera, surface, listener);
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    error(listener, new CameraAccessException(CameraAccessException.CAMERA_DISCONNECTED));
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    error(listener, new CameraAccessException(error));
                }
            }, null);
        } catch (Exception e) {
            error(listener, e);
        }
    }


    @Override
    public void stop() {
        if (cameraDevice != null) {
            cameraDevice.close();
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
        this.cameraDevice = null;
    }

    private void error(OnSurfaceStateListener listener, Throwable e) {
        if (listener != null) {
            listener.onError(e);
        }
        stop();
    }

    private void createPreviewSession(CameraDevice camera, Surface surface, OnSurfaceStateListener listener) {
        try {
            camera.createCaptureSession(
                    Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            try {
                                CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                                builder.addTarget(surface);
                                session.setRepeatingRequest(builder.build(), null, null);
                            } catch (CameraAccessException e) {
                                error(listener, e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            camera.close();
                            error(listener, new Exception("onConfigureFailed"));
                        }
                    },
                    null
            );
        } catch (Exception e) {
            error(listener, e);
        }
    }

    private Pair<String, CameraCharacteristics> getCameraInfo(int lens) throws CameraAccessException {
        String[] cameraIdArr = cameraManager.getCameraIdList();
        if (cameraIdArr.length == 0) {
            return null;
        }
        String finalCameraId = "";
        CameraCharacteristics finalCameraInfo = null;
        for (String cameraId : cameraIdArr) {
            CameraCharacteristics info = cameraManager.getCameraCharacteristics(cameraId);
            Integer lensFacing = info.get(CameraCharacteristics.LENS_FACING);
            if (lensFacing == null) {
                continue;
            }
            finalCameraId = cameraId;
            finalCameraInfo = info;
            if (lensFacing == lens) {
                break;
            }

        }
        if (TextUtils.isEmpty(finalCameraId) || finalCameraInfo == null) {
            return null;
        }
        return new Pair<>(finalCameraId, finalCameraInfo);
    }

    private Size getPreviewSize(CameraCharacteristics info) {
        StreamConfigurationMap map = info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            return null;
        }
        Size[] previewSizeArr = map.getOutputSizes(SurfaceTexture.class);
        if (previewSizeArr == null || previewSizeArr.length == 0) {
            return null;
        }
        List<Size> candidateSizes = new ArrayList<>();
        for (Size size : previewSizeArr) {
            float ratio = (float) size.getWidth() / size.getHeight();
            if (ratio >= 4 / 3f && ratio <= 16 / 9f) {
                candidateSizes.add(size);
            }
        }
        Collections.sort(candidateSizes, (a, b) -> b.getWidth() * a.getWidth());
        Size result;
        if (!candidateSizes.isEmpty()) {
            result = candidateSizes.get(candidateSizes.size() / 2);
        } else {
            result = previewSizeArr[0];
        }
        return result;
    }

    private int getWindowsRotation() {
        int degrees;
        switch (windowManager.getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
            default:
                degrees = 0;
        }
        return degrees;
    }

}
