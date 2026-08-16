package com.manus.cubemaster;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 轻量级六面扫描器。识别结果应由用户在手动上色面板中复核后再求解；
 * 这避免了复杂光照下单帧颜色分类造成的静默错误。
 */
public final class CameraScanDialog extends Dialog implements ImageAnalysis.Analyzer {
    public interface Listener { void onFaceCaptured(int face, char[] values); }

    private final LifecycleOwner lifecycleOwner;
    private final Listener listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView status;
    private TextView faceTitle;
    private PreviewView previewView;
    private volatile char[] latestColors = new char[]{'U','U','U','U','U','U','U','U','U'};
    private int selectedFace = 0;
    private ProcessCameraProvider cameraProvider;

    public CameraScanDialog(@NonNull Context context, @NonNull LifecycleOwner lifecycleOwner, int initialFace, @NonNull Listener listener) {
        super(context);
        this.lifecycleOwner = lifecycleOwner;
        this.listener = listener;
        this.selectedFace = Math.max(0, Math.min(5, initialFace));
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(content());
        Window window = getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.black);
        }
        startCamera();
    }

    @Override public void dismiss() {
        if (cameraProvider != null) cameraProvider.unbindAll();
        executor.shutdownNow();
        super.dismiss();
    }

    private View content() {
        FrameLayout root = new FrameLayout(getContext());
        root.setBackgroundColor(Color.BLACK);
        previewView = new PreviewView(getContext());
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(previewView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(new GuideOverlay(getContext()), new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout chrome = new LinearLayout(getContext());
        chrome.setOrientation(LinearLayout.VERTICAL);
        chrome.setPadding(dp(18), dp(38), dp(18), dp(22));
        chrome.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        faceTitle = new TextView(getContext());
        faceTitle.setTextColor(Color.WHITE);
        faceTitle.setTextSize(19);
        faceTitle.setGravity(Gravity.CENTER);
        chrome.addView(faceTitle, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32)));

        TextView instruction = new TextView(getContext());
        instruction.setText("让一个面的九格对准中央方框；识别后仍可在上色面板修正。\n光线均匀、避免反光时结果最佳。");
        instruction.setTextColor(Color.rgb(220, 226, 235));
        instruction.setTextSize(13);
        instruction.setGravity(Gravity.CENTER);
        chrome.addView(instruction, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        LinearLayout faceRow = new LinearLayout(getContext());
        faceRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < 6; i++) {
            final int face = i;
            AppCompatButton button = new AppCompatButton(getContext());
            button.setAllCaps(false);
            button.setText(String.valueOf(CubeState.FACE_ORDER.charAt(i)));
            button.setTextSize(13);
            button.setPadding(0, 0, 0, 0);
            button.setOnClickListener(v -> { selectedFace = face; refreshTitle(); });
            faceRow.addView(button, new LinearLayout.LayoutParams(dp(43), dp(36)) {{ setMargins(dp(1), 0, dp(1), 0); }});
        }
        chrome.addView(faceRow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));
        root.addView(chrome, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        LinearLayout bottom = new LinearLayout(getContext());
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(18), dp(8), dp(18), dp(18));
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        status = new TextView(getContext());
        status.setTextColor(Color.rgb(210, 217, 229));
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER);
        status.setText("正在识别颜色…");
        bottom.addView(status, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(26)));
        AppCompatButton capture = new AppCompatButton(getContext());
        capture.setText("保存当前面");
        capture.setAllCaps(false);
        capture.setTextColor(Color.rgb(13, 22, 20));
        capture.setTextSize(16);
        capture.setBackgroundColor(Color.rgb(110, 231, 183));
        capture.setOnClickListener(v -> {
            char[] sample = latestColors.clone();
            sample[4] = CubeState.FACE_ORDER.charAt(selectedFace);
            listener.onFaceCaptured(selectedFace, sample);
            status.setText("已保存 " + CubeState.FACE_ORDER.charAt(selectedFace) + " 面；可继续选择下一面或关闭。");
        });
        bottom.addView(capture, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));
        AppCompatButton close = new AppCompatButton(getContext());
        close.setText("完成扫描");
        close.setAllCaps(false);
        close.setTextColor(Color.WHITE);
        close.setOnClickListener(v -> dismiss());
        bottom.addView(close, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
        root.addView(bottom, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        refreshTitle();
        return root;
    }

    private void refreshTitle() {
        if (faceTitle != null) faceTitle.setText("拍摄 " + CubeState.FACE_ORDER.charAt(selectedFace) + " 面");
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(getContext());
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(960, 1280))
                        .setTargetRotation(Surface.ROTATION_0)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(executor, this);
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            } catch (Exception e) {
                if (status != null) status.setText("无法启动相机，请检查权限后重试。");
            }
        }, ContextCompat.getMainExecutor(getContext()));
    }

    @Override public void analyze(@NonNull ImageProxy image) {
        try {
            int width = image.getWidth();
            int height = image.getHeight();
            char[] result = new char[9];
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int sampleX = (int) (width * (0.35f + col * 0.15f));
                    int sampleY = (int) (height * (0.35f + row * 0.15f));
                    int rgb = meanRgb(image, sampleX, sampleY, Math.max(5, Math.min(width, height) / 70));
                    result[row * 3 + col] = classify(rgb);
                }
            }
            latestColors = result;
            if (status != null) {
                String label = new String(result);
                status.post(() -> status.setText("当前颜色：" + label + "  ·  请确认后保存"));
            }
        } finally {
            image.close();
        }
    }

    private static int meanRgb(ImageProxy image, int centerX, int centerY, int radius) {
        long rs = 0, gs = 0, bs = 0;
        int count = 0;
        for (int y = centerY - radius; y <= centerY + radius; y += 3) {
            for (int x = centerX - radius; x <= centerX + radius; x += 3) {
                int[] rgb = yuvToRgb(image, Math.max(0, Math.min(image.getWidth() - 1, x)), Math.max(0, Math.min(image.getHeight() - 1, y)));
                rs += rgb[0]; gs += rgb[1]; bs += rgb[2]; count++;
            }
        }
        return Color.rgb((int) (rs / count), (int) (gs / count), (int) (bs / count));
    }

    private static int[] yuvToRgb(ImageProxy image, int x, int y) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        int yy = readPlane(planes[0], x, y);
        int uu = readPlane(planes[1], x / 2, y / 2) - 128;
        int vv = readPlane(planes[2], x / 2, y / 2) - 128;
        int r = clamp((int) (yy + 1.402f * vv));
        int g = clamp((int) (yy - 0.344136f * uu - 0.714136f * vv));
        int b = clamp((int) (yy + 1.772f * uu));
        return new int[]{r, g, b};
    }

    private static int readPlane(ImageProxy.PlaneProxy plane, int x, int y) {
        ByteBuffer buffer = plane.getBuffer();
        int index = y * plane.getRowStride() + x * plane.getPixelStride();
        if (index < 0 || index >= buffer.limit()) return 128;
        return buffer.get(index) & 0xFF;
    }

    private static char classify(int rgb) {
        float[] hsv = new float[3];
        Color.colorToHSV(rgb, hsv);
        float h = hsv[0], s = hsv[1], v = hsv[2];
        if (s < 0.25f && v > 0.35f) return 'U';
        if (h >= 42f && h < 82f) return 'D';
        if (h >= 82f && h < 178f) return 'F';
        if (h >= 178f && h < 275f) return 'B';
        if (h >= 16f && h < 42f) return 'L';
        return 'R';
    }

    private static int clamp(int value) { return Math.max(0, Math.min(255, value)); }
    private int dp(int value) { return (int) (value * getContext().getResources().getDisplayMetrics().density + 0.5f); }

    private static final class GuideOverlay extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        GuideOverlay(Context context) { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            float side = Math.min(getWidth(), getHeight()) * 0.52f;
            float left = (getWidth() - side) / 2f;
            float top = (getHeight() - side) / 2f - getHeight() * 0.03f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4f);
            paint.setColor(Color.argb(230, 110, 231, 183));
            canvas.drawRoundRect(new RectF(left, top, left + side, top + side), 14f, 14f, paint);
            paint.setStrokeWidth(2f);
            paint.setColor(Color.argb(180, 255, 255, 255));
            for (int i = 1; i < 3; i++) {
                float d = side * i / 3f;
                canvas.drawLine(left + d, top, left + d, top + side, paint);
                canvas.drawLine(left, top + d, left + side, top + d, paint);
            }
        }
    }
}
