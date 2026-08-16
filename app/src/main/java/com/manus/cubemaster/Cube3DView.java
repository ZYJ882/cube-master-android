package com.manus.cubemaster;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 轻量级透视投影三维魔方；可通过拖拽绕水平和垂直轴浏览。 */
public final class Cube3DView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String facelets = CubeState.SOLVED;
    private float yaw = -38f;
    private float pitch = -27f;
    private float lastX;
    private float lastY;
    private float downX;
    private float downY;
    private long downTime;
    private Runnable tapListener;

    public Cube3DView(Context context) { super(context); init(); }
    public Cube3DView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        paint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(Color.argb(95, 0, 0, 0));
        shadowPaint.setStyle(Paint.Style.FILL);
    }

    public void setFacelets(String next) {
        if (next != null && next.length() == 54) {
            facelets = next;
            invalidate();
        }
    }

    public void resetCamera() {
        yaw = -38f;
        pitch = -27f;
        invalidate();
    }

    public void setTapListener(Runnable listener) {
        tapListener = listener;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.TRANSPARENT);
        float cx = getWidth() * 0.5f;
        float cy = getHeight() * 0.51f;
        float scale = Math.min(getWidth(), getHeight()) * 0.26f;

        // 环境投影阴影
        canvas.save();
        canvas.scale(1.0f, 0.34f, cx, cy + scale * 1.32f);
        canvas.drawOval(cx - scale * 1.62f, cy + scale * 0.97f, cx + scale * 1.62f, cy + scale * 1.67f, shadowPaint);
        canvas.restore();

        List<StickerPolygon> polygons = new ArrayList<>();
        for (int face = 0; face < 6; face++) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    StickerPolygon polygon = buildSticker(face, row, col, cx, cy, scale);
                    if (polygon.visible) polygons.add(polygon);
                }
            }
        }
        Collections.sort(polygons, Comparator.comparingDouble(p -> p.depth));
        for (StickerPolygon polygon : polygons) {
            paint.setColor(Color.rgb(15, 18, 24));
            canvas.drawPath(polygon.path, paint);
            Path inset = insetPath(polygon.points, 0.86f);
            paint.setColor(CubeState.colorArgb(facelets.charAt(polygon.stickerIndex)));
            canvas.drawPath(inset, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, scale * 0.023f));
            paint.setColor(Color.argb(115, 255, 255, 255));
            canvas.drawPath(inset, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private StickerPolygon buildSticker(int face, int row, int col, float cx, float cy, float scale) {
        float x = col - 1f;
        float y = 1f - row;
        float z = 0f;
        float half = 0.47f;
        float[][] vertices = new float[4][3];
        float[] normal;
        switch (face) {
            case 0: // U
                y = 1.5f; z = row - 1f; normal = new float[]{0, 1, 0};
                vertices = quad(x, y, z, half, new float[]{1, 0, 0}, new float[]{0, 0, 1});
                break;
            case 1: // R
                x = 1.5f; y = 1f - row; z = 1f - col; normal = new float[]{1, 0, 0};
                vertices = quad(x, y, z, half, new float[]{0, 0, -1}, new float[]{0, -1, 0});
                break;
            case 2: // F
                z = 1.5f; x = col - 1f; y = 1f - row; normal = new float[]{0, 0, 1};
                vertices = quad(x, y, z, half, new float[]{1, 0, 0}, new float[]{0, -1, 0});
                break;
            case 3: // D
                y = -1.5f; z = 1f - row; normal = new float[]{0, -1, 0};
                vertices = quad(x, y, z, half, new float[]{1, 0, 0}, new float[]{0, 0, -1});
                break;
            case 4: // L
                x = -1.5f; y = 1f - row; z = col - 1f; normal = new float[]{-1, 0, 0};
                vertices = quad(x, y, z, half, new float[]{0, 0, 1}, new float[]{0, -1, 0});
                break;
            default: // B
                z = -1.5f; x = 1f - col; y = 1f - row; normal = new float[]{0, 0, -1};
                vertices = quad(x, y, z, half, new float[]{-1, 0, 0}, new float[]{0, -1, 0});
                break;
        }

        float[] transformedNormal = transform(normal);
        Path path = new Path();
        PointF[] points = new PointF[4];
        float depth = 0f;
        for (int i = 0; i < 4; i++) {
            float[] t = transform(vertices[i]);
            depth += t[2];
            PointF point = project(t, cx, cy, scale);
            points[i] = point;
            if (i == 0) path.moveTo(point.x, point.y); else path.lineTo(point.x, point.y);
        }
        path.close();
        return new StickerPolygon(CubeState.stickerIndex(face, row, col), path, points, depth / 4f, transformedNormal[2] > 0.03f);
    }

    private static float[][] quad(float x, float y, float z, float h, float[] right, float[] down) {
        return new float[][]{
                {x - right[0] * h - down[0] * h, y - right[1] * h - down[1] * h, z - right[2] * h - down[2] * h},
                {x + right[0] * h - down[0] * h, y + right[1] * h - down[1] * h, z + right[2] * h - down[2] * h},
                {x + right[0] * h + down[0] * h, y + right[1] * h + down[1] * h, z + right[2] * h + down[2] * h},
                {x - right[0] * h + down[0] * h, y - right[1] * h + down[1] * h, z - right[2] * h + down[2] * h}
        };
    }

    private float[] transform(float[] v) {
        double yRad = Math.toRadians(yaw);
        double pRad = Math.toRadians(pitch);
        float x1 = (float) (v[0] * Math.cos(yRad) + v[2] * Math.sin(yRad));
        float z1 = (float) (-v[0] * Math.sin(yRad) + v[2] * Math.cos(yRad));
        float y2 = (float) (v[1] * Math.cos(pRad) - z1 * Math.sin(pRad));
        float z2 = (float) (v[1] * Math.sin(pRad) + z1 * Math.cos(pRad));
        return new float[]{x1, y2, z2};
    }

    private static PointF project(float[] v, float cx, float cy, float scale) {
        float perspective = 5.3f / (5.3f - v[2]);
        return new PointF(cx + v[0] * scale * perspective, cy - v[1] * scale * perspective);
    }

    private static Path insetPath(PointF[] points, float factor) {
        float x = 0f, y = 0f;
        for (PointF p : points) { x += p.x; y += p.y; }
        x /= 4f; y /= 4f;
        Path result = new Path();
        for (int i = 0; i < 4; i++) {
            float px = x + (points[i].x - x) * factor;
            float py = y + (points[i].y - y) * factor;
            if (i == 0) result.moveTo(px, py); else result.lineTo(px, py);
        }
        result.close();
        return result;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = downX = event.getX();
                lastY = downY = event.getY();
                downTime = System.currentTimeMillis();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - lastX;
                float dy = event.getY() - lastY;
                yaw += dx * 0.55f;
                pitch = Math.max(-78f, Math.min(78f, pitch + dy * 0.48f));
                lastX = event.getX();
                lastY = event.getY();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (Math.abs(event.getX() - downX) < 10f && Math.abs(event.getY() - downY) < 10f
                        && System.currentTimeMillis() - downTime < 260L && tapListener != null) {
                    tapListener.run();
                }
                performClick();
                return true;
            default: return true;
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private static final class StickerPolygon {
        final int stickerIndex;
        final Path path;
        final PointF[] points;
        final float depth;
        final boolean visible;
        StickerPolygon(int index, Path path, PointF[] points, float depth, boolean visible) {
            this.stickerIndex = index; this.path = path; this.points = points; this.depth = depth; this.visible = visible;
        }
    }
}
