package io.zxingye.surfacebridge.demo;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Surface;

import androidx.core.app.NotificationCompat;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;


public class ScreenCaptureService extends Service {

    private static final String TAG = "ScreenCaptureService";

    private static final String KEY_MEDIA_INTENT = "ScreenCaptureIntent";
    private static final String KEY_OUTPUT_SURFACE = "outputSurface";
    private static final String CHANNEL_ID = "ScreenCapture";

    private static final int MEDIA_REQUEST_CODE = CHANNEL_ID.hashCode();

    private static final int DEFAULT_CAPTURE_DPI = 320;

    private static final Set<OnCaptureStateListener> GLOBAL_LISTENERS = new CopyOnWriteArraySet<>();

    public static void requestScreenCapturePermission(@NotNull Activity activity) {
        activity.startActivityForResult(createScreenCaptureIntent(activity), CHANNEL_ID.hashCode());
    }

    public static Intent createScreenCaptureIntent(@NotNull Context context) {
        MediaProjectionManager mpm = (MediaProjectionManager) context.getSystemService(MEDIA_PROJECTION_SERVICE);
        return mpm.createScreenCaptureIntent();
    }

    public static void startScreenCaptureService(@NotNull Context context,
                                                 @NotNull Intent screenCaptureIntent,
                                                 @NotNull Surface surface) {
        Intent serviceIntent = new Intent(context, ScreenCaptureService.class);
        serviceIntent.putExtra(KEY_MEDIA_INTENT, Objects.requireNonNull(screenCaptureIntent));
        serviceIntent.putExtra(KEY_OUTPUT_SURFACE, surface);
        context.stopService(serviceIntent);
        context.startService(serviceIntent);
    }

    public static void stopScreenCaptureService(@NotNull Context context) {
        context.stopService(new Intent(context, ScreenCaptureService.class));
    }

    public static void addCaptureStateListener(@NotNull OnCaptureStateListener listener) {
        GLOBAL_LISTENERS.add(listener);
    }

    public static void removeCaptureStateListener(@NotNull OnCaptureStateListener listener) {
        GLOBAL_LISTENERS.remove(listener);
    }


    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");
        startForeground(MEDIA_REQUEST_CODE, createNotification(this));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!startCapture(intent)) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private boolean startCapture(Intent intent) {
        if (intent == null || mediaProjection != null || virtualDisplay != null) {
            return false;
        }

        Intent screenCaptureIntent = intent.getParcelableExtra(KEY_MEDIA_INTENT);
        Surface surface = intent.getParcelableExtra(KEY_OUTPUT_SURFACE);
        if (screenCaptureIntent == null || surface == null) {
            return false;
        }

        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpm.getMediaProjection(Activity.RESULT_OK, screenCaptureIntent);
        if (mediaProjection == null) {
            return false;
        }
        mediaProjection.registerCallback(captureCallback, null);

        virtualDisplay = mediaProjection.createVirtualDisplay(
                TAG,
                1,
                1,
                DEFAULT_CAPTURE_DPI,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null);

        notifyOnStart(this);

        return true;
    }

    public void stopCapture() {
        if (virtualDisplay != null) {
            virtualDisplay.setSurface(null);
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        stopSelf();
    }


    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        stopCapture();
        super.onDestroy();
        notifyOnFinish();
    }

    private final MediaProjection.Callback captureCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            Log.i(TAG, "onStop");
        }

        @Override
        public void onCapturedContentResize(int width, int height) {
            if (virtualDisplay != null) {
                virtualDisplay.resize(width, height, DEFAULT_CAPTURE_DPI);
            }
            Log.i(TAG, "onCapturedContentResize: " + width + " x " + height);
            notifyOnSizeChange(width, height);
        }

        @Override
        public void onCapturedContentVisibilityChanged(boolean isVisible) {
            Log.i(TAG, "onCapturedContentVisibilityChanged: " + isVisible);
        }
    };

    private static void notifyOnStart(ScreenCaptureService service) {
        for (OnCaptureStateListener listener : GLOBAL_LISTENERS) {
            listener.onCaptureStart(service);
        }
    }

    private static void notifyOnSizeChange(int width, int height) {
        for (OnCaptureStateListener listener : GLOBAL_LISTENERS) {
            listener.onCaptureSizeChange(width, height);
        }
    }

    private static void notifyOnFinish() {
        for (OnCaptureStateListener listener : GLOBAL_LISTENERS) {
            listener.onCaptureFinish();
        }
    }

    private static Notification createNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_ID,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Screen Capture Notification");
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PRIVATE);
            manager.createNotificationChannel(channel);
        }
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Screen Capture")
                .setContentText("Screen content is being captured, don't turn off the notification")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .build();
    }


    public interface OnCaptureStateListener {
        default void onCaptureStart(ScreenCaptureService service) {
        }

        default void onCaptureSizeChange(int width, int height) {
        }

        default void onCaptureFinish() {
        }
    }
}