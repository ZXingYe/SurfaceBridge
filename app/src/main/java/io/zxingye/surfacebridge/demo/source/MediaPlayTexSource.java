package io.zxingye.surfacebridge.demo.source;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;

import java.io.IOException;

public class MediaPlayTexSource implements TexSource {

    private final static String FILE_NAME = "video.mp4";
    private final AssetManager assetManager;
    private MediaPlayer player;
    private AssetFileDescriptor fd;
    private Surface surface;

    public MediaPlayTexSource(AssetManager asset) {
        this.assetManager = asset;
    }

    @Override
    public void start(SurfaceTexture surfaceTexture, OnSurfaceStateListener listener) {
        try {
            surface = new Surface(surfaceTexture);
            fd = assetManager.openFd(FILE_NAME);
            player = new MediaPlayer();
            player.setOnVideoSizeChangedListener((mp, width, height) ->
                    listener.onSizeChange(width, height, 0));
            player.setOnErrorListener((mp, what, extra) -> {
                stop();
                listener.onError(new Exception("play error: what = " + what + ", extra = " + extra));
                return false;
            });
            player.setLooping(true);
            player.setVolume(0, 0);
            player.setSurface(surface);
            player.setDataSource(fd);
            player.prepare();
            player.start();
        } catch (Exception e) {
            stop();
            listener.onError(e);
        }

    }

    @Override
    public void stop() {
        if (player != null) {
            player.release();
            player = null;
        }
        if (fd != null) {
            try {
                fd.close();
            } catch (IOException ignored) {

            }
            fd = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
    }
}
