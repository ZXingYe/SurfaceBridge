package io.zxingye.library.surfaceextractor.demo.source;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;

public class DecoderTexSource implements TexSource {

    private static final String TAG = "DecoderTexSource";

    private final static String FILE_NAME = "video.mp4";

    private final AssetManager assetManager;

    private MediaExtractor videoExtractor;
    private MediaCodec videoCodec;
    private AssetFileDescriptor fd;
    private Surface surface;

    public DecoderTexSource(AssetManager asset) {
        this.assetManager = asset;
    }


    @Override
    public synchronized void start(SurfaceTexture surfaceTexture, OnSurfaceStateListener listener) {
        try {
            surface = new Surface(surfaceTexture);
            videoExtractor = new MediaExtractor();
            fd = assetManager.openFd(FILE_NAME);
            videoExtractor.setDataSource(fd);
            int videoTrackIndex = getMediaTrackIndexByMimeType(videoExtractor, "video/");
            if (videoTrackIndex < 0) {
                throw new Exception("videoTrackIndex < 0");
            }
            videoExtractor.selectTrack(videoTrackIndex);
            MediaFormat videoFormat = videoExtractor.getTrackFormat(videoTrackIndex);
            String mime = videoFormat.getString(MediaFormat.KEY_MIME);
            if (mime == null) {
                mime = MediaFormat.MIMETYPE_VIDEO_AVC;
            }
            videoCodec = MediaCodec.createDecoderByType(mime);
            videoCodec.configure(videoFormat, surface, null, 0);
            videoCodec.setCallback(new DecoderCallback(listener));
            videoCodec.start();
        } catch (Exception e) {
            stop();
            listener.onError(e);
        }
    }

    @Override
    public synchronized void stop() {
        if (videoCodec != null) {
            videoCodec.release();
            videoCodec = null;
        }
        if (videoExtractor != null) {
            videoExtractor.release();
            videoExtractor = null;
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

    private class DecoderCallback extends MediaCodec.Callback {
        private final OnSurfaceStateListener listener;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private long startPtsUs;

        public DecoderCallback(OnSurfaceStateListener listener) {
            this.listener = listener;
        }

        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
            synchronized (DecoderTexSource.this) {
                if (videoCodec == null || videoExtractor == null) {
                    return;
                }
                if (startPtsUs <= 0) {
                    startPtsUs = SystemClock.uptimeMillis() * 1000;
                }
                ByteBuffer inputBuffer = codec.getInputBuffer(index);
                if (inputBuffer != null) {
                    inputBuffer.clear();
                    int readLength = videoExtractor.readSampleData(inputBuffer, 0);
                    if (readLength > 0) {
                        long nextFramePlayTimeUs = (videoExtractor.getSampleTime() + startPtsUs);
                        codec.queueInputBuffer(index, 0, readLength, nextFramePlayTimeUs, 0);
                    }
                    if (!videoExtractor.advance()) {
                        videoExtractor.seekTo(0, MediaExtractor.SEEK_TO_NEXT_SYNC);
                        startPtsUs = 0;
                        Log.i(TAG, "replay");
                    }
                }
            }
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
            handler.postAtTime(() -> {
                synchronized (DecoderTexSource.this) {
                    if (videoCodec == null || videoExtractor == null || startPtsUs <= 0) {
                        return;
                    }
                    if (info.flags != MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                        codec.releaseOutputBuffer(index, true);
                    }
                }
            }, codec, info.presentationTimeUs / 1000);
        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
            stop();
            listener.onError(e);
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
            int width = format.getInteger(MediaFormat.KEY_WIDTH);
            int height = format.getInteger(MediaFormat.KEY_HEIGHT);
            listener.onSizeChange(width, height, 0);
        }
    }

    private static int getMediaTrackIndexByMimeType(MediaExtractor mediaExtractor, String mimeType) {
        for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
            MediaFormat mediaFormat = mediaExtractor.getTrackFormat(i);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith(mimeType)) {
                return i;
            }
        }
        return -1;
    }
}
