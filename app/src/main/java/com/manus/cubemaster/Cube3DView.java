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
    /** 手指已越过拖动死区并被识别为层转时触发。 */
    public interface LayerGestureListener { void onLayerGestureStarted(); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String facelets = CubeState.SOLVED;
    private String animationFacelets;
    /** 正在预览或播放的标准转动，可为 U/R/F/D/L/B 或 M/E/S。 */
    private char animationMove = 0;
    private float animationAngle = 0f;
    private ValueAnimator moveAnimator;
    private ValueAnimator layerSettleAnimator;
    private ValueAnimator cameraAnimator;

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
    public interface DirectMoveListener { void onMove(String move); }
    private DirectMoveListener directMoveListener;
    private LayerGestureListener layerGestureListener;
    private StickerPolygon touchSticker;
    private int gestureMode;
    private boolean layerDirectionLocked;
    private boolean layerHorizontal;
    private String pendingLayerMove;
    private boolean directMoveStarted;
    private static final int GESTURE_ORBIT = 0;
    private static final int GESTURE_LAYER = 1;

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
        return (moveAnimator != null && moveAnimator.isRunning())
                || (layerSettleAnimator != null && layerSettleAnimator.isRunning());
    }

    /** 以标准记号动画显示单个转动；模型状态由回调完成后再提交。 */
    public void animateMove(String move, long durationMs, MoveAnimationListener listener) {
        if (move == null || !move.matches("[URFDLBMES](2|')?")) {
            if (listener != null) listener.onMoveCompleted();
            return;
        }
        cancelMoveAnimation();
        animationFacelets = facelets;
        animationMove = move.charAt(0);
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
                    animationMove = 0;
                    animationAngle = 0f;
                    invalidate();
                    moveAnimator = null;
                    if (listener != null) listener.onMoveCompleted();
                } else {
                    moveAnimator = null;
                }
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
        if (layerSettleAnimator != null) {
            layerSettleAnimator.cancel();
            layerSettleAnimator = null;
        }
        clearLayerPreview();
        invalidate();
    }

    public void resetCamera() {
        stopInertia();
        if (cameraAnimator != null) cameraAnimator.cancel();
        yaw = -38f;
        pitch = -24f;
        invalidate();
    }

    /** 保存当前视角，供临时预览模式在松手后恢复。 */
    public CameraPose captureCameraPose() { return new CameraPose(yaw, pitch); }

    /** 外部控制按钮按下时调用，避免与已有惯性或复原动画竞争。 */
    public void beginExternalCameraControl() {
        stopInertia();
        if (cameraAnimator != null) cameraAnimator.cancel();
    }

    /** 用增量拖动控制视角，供“3D”和“眼睛”按钮复用。 */
    public void dragExternalCameraBy(float dx, float dy) {
        yaw += dx * .48f;
        pitch = clamp(pitch + dy * .42f, -84f, 84f);
        invalidate();
    }

    /** 以短缓动回到保存的视角，用于小眼睛临时预览。 */
    public void restoreCameraPose(CameraPose pose) {
        if (pose == null) return;
        stopInertia();
        if (cameraAnimator != null) cameraAnimator.cancel();
        final float startYaw = yaw;
        final float startPitch = pitch;
        cameraAnimator = ValueAnimator.ofFloat(0f, 1f);
        cameraAnimator.setDuration(230L);
        cameraAnimator.setInterpolator(new PathInterpolator(.2f, .72f, .2f, 1f));
        cameraAnimator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            yaw = startYaw + (pose.yaw - startYaw) * t;
            pitch = startPitch + (pose.pitch - startPitch) * t;
            invalidate();
        });
        cameraAnimator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { cameraAnimator = null; }
        });
        cameraAnimator.start();
    }

    public void setTapListener(Runnable listener) { tapListener = listener; }
    public void setDirectMoveListener(DirectMoveListener listener) { directMoveListener = listener; }
    public void setLayerGestureListener(LayerGestureListener listener) { layerGestureListener = listener; }

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

        if (animationMove != 0 && belongsToRotatingLayer(face, row, col)) {
            int[] axisInt = CubeState.rotationAxisForMove(animationMove);
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
        return new StickerPolygon(face, row, col, CubeState.stickerIndex(face, row, col), path, points, depth / 4f, transformedNormal[2] > .015f);
    }

    private boolean belongsToRotatingLayer(int face, int row, int col) {
        int[] p = stickerCenter(face, row, col);
        int[] axis = CubeState.rotationAxisForMove(animationMove);
        return p[0] * axis[0] + p[1] * axis[1] + p[2] * axis[2] == CubeState.rotationLayerForMove(animationMove);
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
                if (cameraAnimator != null) cameraAnimator.cancel();
                lastX = downX = event.getX();
                lastY = downY = event.getY();
                downTime = android.os.SystemClock.uptimeMillis();
                touchSticker = !isMoveAnimating() ? findStickerAt(downX, downY) : null;
                gestureMode = touchSticker == null ? GESTURE_ORBIT : GESTURE_LAYER;
                layerDirectionLocked = false;
                pendingLayerMove = null;
                directMoveStarted = false;
                velocityTracker = VelocityTracker.obtain();
                velocityTracker.addMovement(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (velocityTracker != null) velocityTracker.addMovement(event);
                if (gestureMode == GESTURE_LAYER) {
                    updateLayerGesture(event.getX() - downX, event.getY() - downY);
                } else {
                    float stepX = event.getX() - lastX;
                    float stepY = event.getY() - lastY;
                    yaw += stepX * .42f;
                    pitch = clamp(pitch + stepY * .38f, -84f, 84f);
                    lastX = event.getX();
                    lastY = event.getY();
                    invalidate();
                }
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
                if (gestureMode == GESTURE_LAYER) {
                    finishLayerGesture(event.getActionMasked() == MotionEvent.ACTION_UP);
                    yawVelocity = pitchVelocity = 0f;
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP && drag < dp(10) && now - downTime < 280L) {
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
                touchSticker = null;
                gestureMode = GESTURE_ORBIT;
                performClick();
                return true;
            default:
                return true;
        }
    }

    private void updateLayerGesture(float dx, float dy) {
        float distance = (float) Math.hypot(dx, dy);
        if (!layerDirectionLocked) {
            if (distance < dp(8)) return;
            if (layerGestureListener != null) layerGestureListener.onLayerGestureStarted();
            layerHorizontal = Math.abs(dx) >= Math.abs(dy);
            layerDirectionLocked = true;
            animationFacelets = facelets;
        }
        float primary = layerHorizontal ? dx : dy;
        pendingLayerMove = directMoveFor(touchSticker, layerHorizontal, primary);
        animationMove = pendingLayerMove.charAt(0);
        float sign = pendingLayerMove.endsWith("'") ? -1f : 1f;
        float magnitude = clamp(Math.abs(primary) * 90f / dp(116), 0f, 82f);
        animationAngle = sign * magnitude;
        directMoveStarted = magnitude >= 12f;
        invalidate();
    }

    /**
     * 从可见贴纸的行列推导实际层：横向拖动优先按行选择 U/E/D 或 F/S/B；
     * 纵向拖动优先按列选择 L/M/R 或 B/S/F。这样顶层、中层与外层都可直接触控。
     */
    private String directMoveFor(StickerPolygon sticker, boolean horizontal, float primaryDrag) {
        char move;
        if (horizontal) {
            if (sticker.face == 0 || sticker.face == 3) move = rowMove(sticker.row, 'B', 'S', 'F');
            else move = rowMove(sticker.row, 'U', 'E', 'D');
        } else {
            if (sticker.face == 1 || sticker.face == 4) move = columnMove(sticker.col, 'B', 'S', 'F');
            else move = columnMove(sticker.col, 'L', 'M', 'R');
        }
        return String.valueOf(move) + (primaryDrag >= 0f ? "" : "'");
    }

    private static char rowMove(int row, char top, char middle, char bottom) {
        return row == 0 ? top : (row == 1 ? middle : bottom);
    }

    private static char columnMove(int col, char left, char middle, char right) {
        return col == 0 ? left : (col == 1 ? middle : right);
    }

    private void finishLayerGesture(boolean released) {
        if (!released || !layerDirectionLocked || pendingLayerMove == null) {
            settleLayerTo(0f, null);
            return;
        }
        float commitThreshold = 30f;
        if (Math.abs(animationAngle) < commitThreshold) {
            settleLayerTo(0f, null);
        } else {
            float target = pendingLayerMove.endsWith("'") ? -90f : 90f;
            settleLayerTo(target, pendingLayerMove);
        }
    }

    /** 将跟手预览平滑吸附到 0° 或 ±90°，再在回调时提交状态。 */
    private void settleLayerTo(float targetAngle, String moveToCommit) {
        if (animationMove == 0) return;
        final float start = animationAngle;
        final String committedMove = moveToCommit;
        layerSettleAnimator = ValueAnimator.ofFloat(start, targetAngle);
        layerSettleAnimator.setDuration(Math.max(110L, (long) (250L * Math.abs(targetAngle - start) / 90f)));
        layerSettleAnimator.setInterpolator(new PathInterpolator(.15f, .78f, .16f, 1f));
        layerSettleAnimator.addUpdateListener(animation -> {
            animationAngle = (float) animation.getAnimatedValue();
            invalidate();
        });
        layerSettleAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                layerSettleAnimator = null;
                if (!cancelled && committedMove != null) {
                    facelets = nextFacelets(animationFacelets, committedMove);
                    clearLayerPreview();
                    invalidate();
                    if (directMoveListener != null) directMoveListener.onMove(committedMove);
                } else {
                    clearLayerPreview();
                    invalidate();
                }
            }
        });
        layerSettleAnimator.start();
    }

    private void clearLayerPreview() {
        animationFacelets = null;
        animationMove = 0;
        animationAngle = 0f;
        layerDirectionLocked = false;
        pendingLayerMove = null;
        directMoveStarted = false;
    }

    private StickerPolygon findStickerAt(float x, float y) {
        float cx = getWidth() * .5f;
        float cy = getHeight() * .505f;
        float scale = Math.min(getWidth(), getHeight()) * .205f;
        StickerPolygon best = null;
        for (int face = 0; face < 6; face++) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    StickerPolygon candidate = buildSticker(face, row, col, cx, cy, scale);
                    boolean hit = contains(candidate.points, x, y) || distanceToPolygon(candidate.points, x, y) <= dp(9);
                    if (candidate.visible && hit && (best == null || candidate.depth > best.depth)) best = candidate;
                }
            }
        }
        return best;
    }

    private static float distanceToPolygon(PointF[] points, float x, float y) {
        float best = Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            PointF a = points[i];
            PointF b = points[(i + 1) % 4];
            float dx = b.x - a.x;
            float dy = b.y - a.y;
            float length2 = dx * dx + dy * dy;
            float t = length2 == 0f ? 0f : clamp(((x - a.x) * dx + (y - a.y) * dy) / length2, 0f, 1f);
            float px = a.x + dx * t;
            float py = a.y + dy * t;
            best = Math.min(best, (float) Math.hypot(x - px, y - py));
        }
        return best;
    }

    private static boolean contains(PointF[] points, float x, float y) {
        float sign = 0f;
        for (int i = 0; i < 4; i++) {
            PointF a = points[i];
            PointF b = points[(i + 1) % 4];
            float cross = (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x);
            if (Math.abs(cross) < .5f) continue;
            if (sign == 0f) sign = Math.signum(cross);
            else if (sign * cross < 0f) return false;
        }
        return true;
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

    public static final class CameraPose {
        final float yaw;
        final float pitch;
        CameraPose(float yaw, float pitch) { this.yaw = yaw; this.pitch = pitch; }
    }

    private static final class StickerPolygon {
        final int face;
        final int row;
        final int col;
        final int stickerIndex;
        final Path path;
        final PointF[] points;
        final float depth;
        final boolean visible;
        StickerPolygon(int face, int row, int col, int index, Path path, PointF[] points, float depth, boolean visible) {
            this.face = face;
            this.row = row;
            this.col = col;
            this.stickerIndex = index;
            this.path = path;
            this.points = points;
            this.depth = depth;
            this.visible = visible;
        }
    }
}
