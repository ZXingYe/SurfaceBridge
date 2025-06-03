package io.zxingye.surfacebridge.demo;

import android.Manifest;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.zxingye.surfacebridge.FrameFormat;
import io.zxingye.surfacebridge.OnFrameListener;
import io.zxingye.surfacebridge.SurfaceBridge;
import io.zxingye.surfacebridge.demo.databinding.ActivityMainBinding;
import io.zxingye.surfacebridge.demo.source.Camera2TexSource;
import io.zxingye.surfacebridge.demo.source.CameraTexSource;
import io.zxingye.surfacebridge.demo.source.CanvasTexSource;
import io.zxingye.surfacebridge.demo.source.DecoderTexSource;
import io.zxingye.surfacebridge.demo.source.TexSource;
import io.zxingye.surfacebridge.demo.source.MediaPlayTexSource;
import io.zxingye.surfacebridge.demo.source.ScreenCaptureTexSource;
import io.zxingye.surfacebridge.transform.ScaleToFitTransform;
import io.zxingye.surfacebridge.transform.Transform;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private final List<Pair<String, Runnable>> surfaceSourceList = new ArrayList<>();
    private final Map<Surface, Boolean> outputSurfaceState = new HashMap<>();

    private ActivityMainBinding binding;
    private SurfaceBridge surfaceBridge;
    private TexSource texSource;

    private Pair<Size, Integer> sourceInfo;
    private int scaleCheckedId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdge.enable(this);
        ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        surfaceBridge = SurfaceBridge.create();

        setupScaleRadioGroup();
        setupSurfaceViewPreview();
        setupTextureViewPreview();
        setupScreenCaptureTexSurface();
        setupCameraCaptureTexSurface();
        setupCamera2CaptureTexSurface();
        setupMediaPlayTexSource();
        setupDecoderTexSource();
        setupCanvasTexSource();
        setupSurfaceSourceSelector();
        setupFrameDownloader();
    }


    private void setupScaleRadioGroup() {
        List<RadioGroup> radioGroupList = new ArrayList<>();
        radioGroupList.add(binding.tgScaleCrop);
        radioGroupList.add(binding.tgScaleInside);
        radioGroupList.add(binding.tgScaleOther);
        RadioGroup.OnCheckedChangeListener listener = new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                for (RadioGroup rg : radioGroupList) {
                    if (rg != group) {
                        rg.setOnCheckedChangeListener(null);
                        rg.clearCheck();
                        rg.setOnCheckedChangeListener(this);
                    }
                }
                scaleCheckedId = checkedId;
                updatePreview();
            }
        };
        for (RadioGroup rg : radioGroupList) {
            rg.setOnCheckedChangeListener(listener);
        }
        binding.tgScaleInside.check(binding.rbtnInsideCenter.getId());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        switchSurfaceSource(null);
        surfaceBridge.release();
    }

    private void setupSurfaceViewPreview() {
        SurfaceHolder.Callback callback = new SimpleSurfaceCallback() {
            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                outputSurfaceState.put(holder.getSurface(), true);
                updatePreview();
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                outputSurfaceState.put(holder.getSurface(), false);
                updatePreview();
            }
        };
        binding.svPreview34.getHolder().addCallback(callback);
        binding.svPreview43.getHolder().addCallback(callback);
        binding.svPreview21.getHolder().addCallback(callback);
    }

    private void setupTextureViewPreview() {
        binding.tvPreview.setSurfaceTextureListener(new SimpleSurfaceTextureListener() {
            private Surface surface;

            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture texture, int width, int height) {
                surface = new Surface(texture);
                outputSurfaceState.put(surface, true);
                updatePreview();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture texture) {
                outputSurfaceState.put(surface, false);
                updatePreview();
                surface.release();
                return true;
            }
        });
    }

    private void setupScreenCaptureTexSurface() {
        ActivityResultLauncher<Intent> requestScreenCapture = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Intent intent = result.getData();
                    if (intent == null) {
                        showToast("Lack of capture permissions");
                        return;
                    }
                    switchSurfaceSource(new ScreenCaptureTexSource(MainActivity.this, intent));
                });
        surfaceSourceList.add(new Pair<>(
                "Screen capture", () -> {
            Intent intent = ScreenCaptureService.createScreenCaptureIntent(MainActivity.this);
            requestScreenCapture.launch(intent);
        }));
    }

    private void setupCameraCaptureTexSurface() {
        ActivityResultLauncher<String> requestCameraCapture = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                success -> {
                    if (success) {
                        switchSurfaceSource(new CameraTexSource(MainActivity.this));
                    } else {
                        showToast("Lack of camera permissions");
                    }
                });
        surfaceSourceList.add(new Pair<>(
                "Camera preview",
                () -> requestCameraCapture.launch(Manifest.permission.CAMERA)));
    }

    private void setupCamera2CaptureTexSurface() {
        ActivityResultLauncher<String> requestCamera2Capture = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                success -> {
                    if (success) {
                        switchSurfaceSource(new Camera2TexSource(MainActivity.this));
                    } else {
                        showToast("Lack of camera permissions");
                    }
                });
        surfaceSourceList.add(new Pair<>(
                "Camera2 preview",
                () -> requestCamera2Capture.launch(Manifest.permission.CAMERA)));
    }

    private void setupMediaPlayTexSource() {
        surfaceSourceList.add(new Pair<>(
                "Play video(MediaPlay)",
                () -> switchSurfaceSource(new MediaPlayTexSource(getAssets()))));
    }

    private void setupDecoderTexSource() {
        surfaceSourceList.add(new Pair<>(
                "Play video(MediaCodec)",
                () -> switchSurfaceSource(new DecoderTexSource(getAssets()))));
    }

    private void setupCanvasTexSource() {
        surfaceSourceList.add(new Pair<>(
                "Custom draw by canvas",
                () -> switchSurfaceSource(new CanvasTexSource())));
    }

    private void setupSurfaceSourceSelector() {
        binding.btnSelectSource.setOnClickListener(v -> {
            String[] items = new String[surfaceSourceList.size()];
            for (int i = 0; i < items.length; i++) {
                items[i] = surfaceSourceList.get(i).first;
            }
            int[] selected = {-1};
            new AlertDialog
                    .Builder(MainActivity.this)
                    .setTitle("From: ")
                    .setSingleChoiceItems(items, -1, (dialog, which) -> selected[0] = which)
                    .setNegativeButton("cancel", null)
                    .setPositiveButton("confirm", (dialog, which) -> {
                        int position = selected[0];
                        if (position >= 0 && position < surfaceSourceList.size()) {
                            surfaceSourceList.get(position).second.run();
                        }
                    })
                    .show();
        });
    }

    private void setupFrameDownloader() {
        Map<String, FrameFormat> formatMap = new LinkedHashMap<>();
        for (FrameFormat format : FrameFormat.values()) {
            formatMap.put(format.name(), format);
        }
        String[] items = formatMap.keySet().toArray(new String[0]);

        OnFrameListener captureOnceListener = new OnFrameListener() {
            @Override
            public void onFrame(ByteBuffer frameData, Size resolution, FrameFormat format) {
                surfaceBridge.removeOnFrameListener(this);
                String formatName = format.name().toLowerCase().replace("_", "");
                File saveFile = new File(getExternalFilesDir(null), "frame." + formatName);
                try (FileOutputStream out = new FileOutputStream(saveFile)) {
                    //noinspection ResultOfMethodCallIgnored
                    out.getChannel().write(frameData);
                    showHintDialog("Capture Successful", "Save to: " + saveFile);
                } catch (Exception e) {
                    showToast("Capture frame fail: " + e);
                }
            }
        };

        binding.btnDownload.setOnClickListener(v -> {
            if (texSource == null) {
                showToast("no data");
                return;
            }
            int[] selected = {-1};
            new AlertDialog
                    .Builder(MainActivity.this)
                    .setTitle("Capture a frame format:")
                    .setSingleChoiceItems(items, -1, (dialog, which) -> selected[0] = which)
                    .setNegativeButton("cancel", null)
                    .setPositiveButton("confirm", (dialog, which) -> {
                        if (selected[0] < 0) {
                            return;
                        }
                        String formatName = items[selected[0]];
                        FrameFormat format = formatMap.get(formatName);
                        surfaceBridge.addOnFrameListener(format, captureOnceListener);
                    })
                    .show();
        });
    }

    private void switchSurfaceSource(TexSource source) {
        if (source != null && texSource != null
                && source.getClass() == texSource.getClass()) {
            return;
        }
        if (texSource != null) {
            texSource.stop();
        }
        texSource = source;
        if (texSource != null) {
            texSource.start(surfaceBridge.getInputSurfaceTexture(), new TexSource.OnSurfaceStateListener() {
                @Override
                public void onSizeChange(int width, int height, int rotation) {
                    sourceInfo = new Pair<>(new Size(width, height), rotation);
                    updatePreview();
                }

                @Override
                public void onError(Throwable e) {
                    Log.w(TAG, "preview fail: " + e, e);
                    showToast("preview fail: " + e);
                    switchSurfaceSource(null);
                }
            });
        }
    }

    private void updatePreview() {
        if (surfaceBridge == null) {
            return;
        }
        int inputWidth = sourceInfo != null ? sourceInfo.first.getWidth() : 0;
        int inputHeight = sourceInfo != null ? sourceInfo.first.getHeight() : 0;
        int rotation = sourceInfo != null ? sourceInfo.second : 0;
        surfaceBridge.setDefaultInputBufferSize(inputWidth, inputHeight);
        Transform transform = null;
        View scaleCheckedView = findViewById(scaleCheckedId);
        if (scaleCheckedView != null) {
            String scaleType = (String) scaleCheckedView.getTag();
            if (!TextUtils.isEmpty(scaleType)) {
                transform = new ScaleToFitTransform(
                        ScaleToFitTransform.Type.valueOf(scaleType),
                        ScaleToFitTransform.Rotation.find(-rotation));
            }
        }

        Iterator<Map.Entry<Surface, Boolean>> iterator = outputSurfaceState.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Surface, Boolean> entry = iterator.next();
            Surface previewSurface = entry.getKey();
            boolean isNeedPreview = Boolean.TRUE.equals(entry.getValue());
            if (isNeedPreview) {
                surfaceBridge.putOutputSurface(previewSurface, transform);
            } else {
                surfaceBridge.removeOutputSurface(previewSurface);
                iterator.remove();
            }
        }
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
    }

    private void showHintDialog(String title, String message) {
        runOnUiThread(() -> new AlertDialog
                .Builder(MainActivity.this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("ok", null)
                .show());
    }
}