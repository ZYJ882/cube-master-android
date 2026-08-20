package com.manus.cubemaster;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** 低饱和流体渐变背景，为半透明玻璃组件提供视觉深度。 */
public final class LiquidBackdropView extends View {
    private static final int ORB_COUNT = 4;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RadialGradient[] orbShaders = new RadialGradient[ORB_COUNT];
    private final float[] orbX = new float[ORB_COUNT];
    private final float[] orbY = new float[ORB_COUNT];
    private final float[] orbRadius = new float[ORB_COUNT];
    private LinearGradient backgroundShader;

    public LiquidBackdropView(Context context) { super(context); init(); }
    public LiquidBackdropView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        gridPaint.setColor(Color.argb(15, 224, 246, 255));
        gridPaint.setStrokeWidth(1f);
    }

    /**
     * 渐变仅取决于视图尺寸，放在这里缓存以避免 onDraw 在滚动、转场或失效重绘时反复分配对象。
     */
    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width <= 0 || height <= 0) {
            backgroundShader = null;
            for (int index = 0; index < ORB_COUNT; index++) orbShaders[index] = null;
            return;
        }
        backgroundShader = new LinearGradient(0, 0, width, height,
                new int[]{Color.rgb(8, 15, 34), Color.rgb(15, 28, 59), Color.rgb(10, 18, 42)},
                new float[]{0f, .54f, 1f}, Shader.TileMode.CLAMP);
        float unit = Math.min(width, height);
        setOrb(0, width * .13f, height * .13f, unit * .72f, Color.argb(96, 73, 216, 255));
        setOrb(1, width * .92f, height * .31f, unit * .65f, Color.argb(76, 189, 108, 255));
        setOrb(2, width * .54f, height * .77f, unit * .9f, Color.argb(58, 156, 90, 255));
        setOrb(3, width * .08f, height * .93f, unit * .68f, Color.argb(55, 212, 139, 255));
    }

    private void setOrb(int index, float x, float y, float radius, int inner) {
        orbX[index] = x;
        orbY[index] = y;
        orbRadius[index] = radius;
        orbShaders[index] = new RadialGradient(x, y, radius, new int[]{inner, Color.TRANSPARENT},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        paint.setShader(backgroundShader);
        canvas.drawRect(0, 0, width, height, paint);

        for (int index = 0; index < ORB_COUNT; index++) {
            paint.setShader(orbShaders[index]);
            canvas.drawCircle(orbX[index], orbY[index], orbRadius[index], paint);
        }

        int spacing = dp(26);
        for (int x = -spacing; x < width + spacing; x += spacing) canvas.drawLine(x, 0, x, height, gridPaint);
        for (int y = -spacing; y < height + spacing; y += spacing) canvas.drawLine(0, y, width, y, gridPaint);
        paint.setShader(null);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
}
