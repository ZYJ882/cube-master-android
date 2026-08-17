package com.manus.cubemaster;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.animation.PathInterpolator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 透视投影三维魔方。支持连续拖拽、惯性环绕、双击复位，以及按层进行的平滑面转动动画。
 */
public final class Cube3DView extends View {
    public interface MoveAnimationListener { void onMoveCompleted(); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String facelets = CubeState.SOLVED;
    private String animationFacelets;
    private int animationFace = -1;
    private float animationAngle = 0f;
    private ValueAnimator moveAnimator;

    private float yaw = -38f;
    private float pitch = -24f;
    private float lastX;
    private float lastY;
    private float downX;
    private float downY;
    private long downTime;
    private long lastTapTime;
    private float lastTapX;
    private float lastTapY;
    private VelocityTracker velocityTracker;
    private float yawVelocity;
    private float pitchVelocity;
    private long inertiaLastTime;
    private boolean orbiting;
    private Runnable tapListener;

    private final Runnable inertiaRunner = new Runnable() {
        @Override public void run() {
            if (!orbiting) return;
            long now = android.os.SystemClock.uptimeMillis();
            float dt = Math.min(2.5f, Math.max(.5f, (now - inertiaLastTime) / 16f));
            inertiaLastTime = now;
            yaw += yawVelocity * dt;
            pitch = clamp(pitch + pitchVelocity * dt, -84f, 84f);
            float damping = (float) Math.pow(.90f, dt);
            yawVelocity *= damping;
            pitchVelocity *= damping;
            invalidate();
            if (Math.abs(yawVelocity) < .025f && Math.abs(pitchVelocity) < .025f) {
                orbiting = false;
                return;
            }
            postOnAnimation(this);
        }
    };

    public Cube3DView(Context context) { super(context); init(); }
    public Cube3DView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        paint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(Color.argb(92, 0, 0, 0));
        shadowPaint.setStyle(Paint.Style.FILL);
        setClickable(true);
    }

    public void setFacelets(String next) {
        if (next != null && next.length() == 54 && !isMoveAnimating()) {
            facelets = next;
            invalidate();
        }
    }

    public boolean isMoveAnimating() {
        return moveAnimator != null && moveAnimator.isRunning();
    }

    /** 以标准记号动画显示单个转动；模型状态由回调完成后再提交。 */
    public void animateMove(String move, long durationMs, MoveAnimationListener listener) {
        if (move == null || !move.matches("[URFDLB](2|')?")) {
            if (listener != null) listener.onMoveCompleted();
            return;
        }
        cancelMoveAnimation();
        animationFacelets = facelets;
        animationFace = CubeState.FACE_ORDER.indexOf(move.charAt(0));
        float targetAngle = move.endsWith("2") ? 180f : (move.endsWith("'") ? -90f : 90f);
        moveAnimator = ValueAnimator.ofFloat(0f, targetAngle);
        moveAnimator.setDuration(Math.max(130L, durationMs));
        moveAnimator.setInterpolator(new PathInterpolator(.18f, .78f, .18f, 1f));
        moveAnimator.addUpdateListener(animation -> {
            animationAngle = (float) animation.getAnimatedValue();
            invalidate();
        });
        moveAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (!cancelled) {
                    facelets = nextFacelets(animationFacelets, move);
                    animationFacelets = null;
                    animationFace = -1;
                    animationAngle = 0f;
                    invalidate();
                    if (listener != null) listener.onMoveCompleted();
                }
                moveAnimator = null;
            }
        });
        moveAnimator.start();
    }

    /** 取消时保持动画开始前的状态，确保上层模型与显示一致。 */
    public void cancelMoveAnimation() {
        if (moveAnimator != null) {
            moveAnimator.cancel();
            moveAnimator = null;
        }
        animationFacelets = null;
        animationFace = -1;
        animationAngle = 0f;
        invalidate();
    }

    public void resetCamera() {
        stopInertia();
        yaw = -38f;
        pitch = -24f;
        invalidate();
    }

    public void setTapListener(Runnable listener) { tapListener = listener; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() * .5f;
        float cy = getHeight() * .505f;
        float scale = Math.min(getWidth(), getHeight()) * .205f;

        canvas.save();
        canvas.scale(1f, .30f, cx, cy + scale * 1.42f);
        canvas.drawOval(cx - scale * 1.70f, cy + scale * 1.12f, cx + scale * 1.70f, cy + scale * 1.77f, shadowPaint);
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
        String source = animationFacelets == null ? facelets : animationFacelets;
        for (StickerPolygon polygon : polygons) {
            paint.setColor(Color.rgb(9, 16, 30));
            canvas.drawPath(polygon.path, paint);
            Path inset = insetPath(polygon.points, .855f);
            paint.setColor(stickerColor(source.charAt(polygon.stickerIndex)));
            canvas.drawPath(inset, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, scale * .018f));
            paint.setColor(Color.argb(132, 240, 250, 255));
            canvas.drawPath(inset, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private StickerPolygon buildSticker(int face, int row, int col, float cx, float cy, float scale) {
        float x = col - 1f;
        float y = 1f - row;
        float z = 0f;
        float half = .465f;
        float[][] vertices;
        float[] normal;
        switch (face) {
            case 0:
                y = 1.5f; z = row - 1f; normal = new float[]{0, 1, 0};
                vertices = quad(x, y, z, half, new float[]{1, 0, 0}, new float[]{0, 0, 1});
                break;
            case 1:
                x = 1.5f; y = 1f - row; z = 1f - col; normal = new float[]{1, 0, 0};
                vertices = quad(x, y, z, half, new float[]{0, 0, -1}, new float[]{0, -1, 0});
                break;
            case 2:
                z = 1.5f; x = col - 1f; y = 1f - row; normal = new float[]{0, 0, 1};
                vertices = quad(x, y, z, half, new float[]{1, 0, 0}, new float[]{0, -1, 0});
                break;
            case 3:
                y = -1.5f; z = 1f - row; normal = new float[]{0, -1, 0};
                vertices = quad(x, y, z, half, new float[]{1, 0, 0}, new float[]{0, 0, -1});
                break;
            case 4:
                x = -1.5f; y = 1f - row; z = col - 1f; normal = new float[]{-1, 0, 0};
                vertices = quad(x, y, z, half, new float[]{0, 0, 1}, new float[]{0, -1, 0});
                break;
            default:
                z = -1.5f; x = 1f - col; y = 1f - row; normal = new float[]{0, 0, -1};
                vertices = quad(x, y, z, half, new float[]{-1, 0, 0}, new float[]{0, -1, 0});
                break;
        }

        if (animationFace >= 0 && belongsToRotatingLayer(face, row, col)) {
            int[] axisInt = normalForFace(animationFace);
            float[] axis = new float[]{axisInt[0], axisInt[1], axisInt[2]};
            for (int i = 0; i < 4; i++) vertices[i] = rotateMove(vertices[i], axis, animationAngle);
            normal = rotateMove(normal, axis, animationAngle);
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
        return new StickerPolygon(CubeState.stickerIndex(face, row, col), path, points, depth / 4f, transformedNormal[2] > .015f);
    }

    private boolean belongsToRotatingLayer(int face, int row, int col) {
        int[] p = stickerCenter(face, row, col);
        int[] axis = normalForFace(animationFace);
        return p[0] * axis[0] + p[1] * axis[1] + p[2] * axis[2] > 0;
    }

    private static int[] stickerCenter(int face, int row, int col) {
        switch (face) {
            case 0: return new int[]{col - 1, 1, row - 1};
            case 1: return new int[]{1, 1 - row, 1 - col};
            case 2: return new int[]{col - 1, 1 - row, 1};
            case 3: return new int[]{col - 1, -1, 1 - row};
            case 4: return new int[]{-1, 1 - row, col - 1};
            default: return new int[]{1 - col, 1 - row, -1};
        }
    }

    private static int[] normalForFace(int face) {
        switch (face) {
            case 0: return new int[]{0, 1, 0};
            case 1: return new int[]{1, 0, 0};
            case 2: return new int[]{0, 0, 1};
            case 3: return new int[]{0, -1, 0};
            case 4: return new int[]{-1, 0, 0};
            default: return new int[]{0, 0, -1};
        }
    }

    /** 与 CubeState 的顺时针面转方向保持一致。 */
    private static float[] rotateMove(float[] vector, float[] axis, float degrees) {
        double radians = Math.toRadians(-degrees);
        float dot = vector[0] * axis[0] + vector[1] * axis[1] + vector[2] * axis[2];
        float[] parallel = new float[]{axis[0] * dot, axis[1] * dot, axis[2] * dot};
        float[] cross = new float[]{
                axis[1] * vector[2] - axis[2] * vector[1],
                axis[2] * vector[0] - axis[0] * vector[2],
                axis[0] * vector[1] - axis[1] * vector[0]
        };
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        return new float[]{
                parallel[0] + (vector[0] - parallel[0]) * cos + cross[0] * sin,
                parallel[1] + (vector[1] - parallel[1]) * cos + cross[1] * sin,
                parallel[2] + (vector[2] - parallel[2]) * cos + cross[2] * sin
        };
    }

    private static int stickerColor(char color) {
        return color == '?' ? Color.rgb(104, 119, 136) : CubeState.colorArgb(color);
    }

    private static String nextFacelets(String state, String move) {
        CubeState next = new CubeState(state);
        next.applyMove(move);
        return next.facelets();
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
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        float x1 = (float) (v[0] * Math.cos(yawRadians) + v[2] * Math.sin(yawRadians));
        float z1 = (float) (-v[0] * Math.sin(yawRadians) + v[2] * Math.cos(yawRadians));
        float y2 = (float) (v[1] * Math.cos(pitchRadians) - z1 * Math.sin(pitchRadians));
        float z2 = (float) (v[1] * Math.sin(pitchRadians) + z1 * Math.cos(pitchRadians));
        return new float[]{x1, y2, z2};
    }

    private static PointF project(float[] v, float cx, float cy, float scale) {
        float perspective = 8.4f / (8.4f - v[2]);
        return new PointF(cx + v[0] * scale * perspective, cy - v[1] * scale * perspective);
    }

    private static Path insetPath(PointF[] points, float factor) {
        float x = 0f, y = 0f;
        for (PointF p : points) { x += p.x; y += p.y; }
        x /= 4f;
        y /= 4f;
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
                getParent().requestDisallowInterceptTouchEvent(true);
                stopInertia();
                lastX = downX = event.getX();
                lastY = downY = event.getY();
                downTime = android.os.SystemClock.uptimeMillis();
                velocityTracker = VelocityTracker.obtain();
                velocityTracker.addMovement(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (velocityTracker != null) velocityTracker.addMovement(event);
                float dx = event.getX() - lastX;
                float dy = event.getY() - lastY;
                yaw += dx * .42f;
                pitch = clamp(pitch + dy * .38f, -84f, 84f);
                lastX = event.getX();
                lastY = event.getY();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                if (velocityTracker != null) {
                    velocityTracker.addMovement(event);
                    velocityTracker.computeCurrentVelocity(1000);
                    yawVelocity = velocityTracker.getXVelocity() * .00019f;
                    pitchVelocity = velocityTracker.getYVelocity() * .00016f;
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                float drag = Math.max(Math.abs(event.getX() - downX), Math.abs(event.getY() - downY));
                long now = android.os.SystemClock.uptimeMillis();
                if (event.getActionMasked() == MotionEvent.ACTION_UP && drag < dp(10) && now - downTime < 280L) {
                    if (now - lastTapTime < 320L && Math.abs(event.getX() - lastTapX) < dp(22) && Math.abs(event.getY() - lastTapY) < dp(22)) {
                        resetCamera();
                        if (tapListener != null) tapListener.run();
                        lastTapTime = 0L;
                    } else {
                        lastTapTime = now;
                        lastTapX = event.getX();
                        lastTapY = event.getY();
                    }
                } else {
                    startInertia();
                }
                performClick();
                return true;
            default:
                return true;
        }
    }

    private void startInertia() {
        if (Math.abs(yawVelocity) < .025f && Math.abs(pitchVelocity) < .025f) return;
        orbiting = true;
        inertiaLastTime = android.os.SystemClock.uptimeMillis();
        postOnAnimation(inertiaRunner);
    }

    private void stopInertia() {
        orbiting = false;
        removeCallbacks(inertiaRunner);
        yawVelocity = 0f;
        pitchVelocity = 0f;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

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
            this.stickerIndex = index;
            this.path = path;
            this.points = points;
            this.depth = depth;
            this.visible = visible;
        }
    }
}
