package io.zxingye.surfacebridge.demo.source;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.Surface;

public class CanvasTexSource implements TexSource {

    private static final String TAG = "CanvasTexSource";

    private static final int FRAME_ROTE = 60;
    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;

    private Thread drawThread;

    @Override
    public void start(SurfaceTexture surfaceTexture, OnSurfaceStateListener listener) {
        drawThread = new Thread(() -> {
            Thread thread = Thread.currentThread();
            Surface surface = new Surface(surfaceTexture);
            listener.onSizeChange(WIDTH, HEIGHT, 0);

            Paint paint = new Paint();
            paint.setColor(Color.RED);

            int rectangleWidth = WIDTH / 2;
            int rectangleHeight = HEIGHT / 2;
            Rect rectangle = new Rect();
            rectangle.left = (WIDTH - rectangleWidth) / 2;
            rectangle.top = (HEIGHT - rectangleHeight) / 2;
            rectangle.right = rectangle.left + rectangleWidth;
            rectangle.bottom = rectangle.top + rectangleHeight;

            int bgColor = Color.argb(255, 48, 48, 48);
            try {
                long drawCount = 0;
                while (!thread.isInterrupted()) {
                    Canvas canvas = surface.lockHardwareCanvas();
                    canvas.drawColor(bgColor);
                    canvas.rotate(180f / FRAME_ROTE * drawCount, WIDTH / 2f, HEIGHT / 2f);
                    canvas.drawRect(rectangle, paint);
                    surface.unlockCanvasAndPost(canvas);
                    sleep(1000f / FRAME_ROTE);
                    drawCount++;
                }
            } finally {
                surface.release();
            }
        });
        drawThread.start();
    }

    @Override
    public void stop() {
        int i = 100;
        while ((i--) > 0) {
            drawThread.interrupt();
            try {
                drawThread.join(10);
            } catch (InterruptedException e) {
                Log.e(TAG, "stop fail: " + e, e);
            }
        }
    }

    private static void sleep(float timeMs) {
        int sleepMs = (int) timeMs;
        int sleepNs = (int) ((timeMs - sleepMs) * 1000 * 1000);
        try {
            Thread.sleep(sleepMs, sleepNs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


}
