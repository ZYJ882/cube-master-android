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
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public LiquidBackdropView(Context context) { super(context); init(); }
    public LiquidBackdropView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        gridPaint.setColor(Color.argb(15, 224, 246, 255));
        gridPaint.setStrokeWidth(1f);
    }

    @Override protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        paint.setShader(new LinearGradient(0, 0, width, height,
                new int[]{Color.rgb(8, 15, 34), Color.rgb(15, 28, 59), Color.rgb(10, 18, 42)},
                new float[]{0f, .54f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);

        drawOrb(canvas, width * .13f, height * .13f, Math.min(width, height) * .72f,
                Color.argb(96, 73, 216, 255), Color.TRANSPARENT);
        drawOrb(canvas, width * .92f, height * .31f, Math.min(width, height) * .65f,
                Color.argb(76, 189, 108, 255), Color.TRANSPARENT);
        drawOrb(canvas, width * .54f, height * .77f, Math.min(width, height) * .9f,
                Color.argb(58, 156, 90, 255), Color.TRANSPARENT);
        drawOrb(canvas, width * .08f, height * .93f, Math.min(width, height) * .68f,
                Color.argb(55, 212, 139, 255), Color.TRANSPARENT);

        int spacing = dp(26);
        for (int x = -spacing; x < width + spacing; x += spacing) canvas.drawLine(x, 0, x, height, gridPaint);
        for (int y = -spacing; y < height + spacing; y += spacing) canvas.drawLine(0, y, width, y, gridPaint);
        paint.setShader(null);
    }

    private void drawOrb(Canvas canvas, float cx, float cy, float radius, int inner, int outer) {
        paint.setShader(new RadialGradient(cx, cy, radius, new int[]{inner, outer}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, paint);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
}
