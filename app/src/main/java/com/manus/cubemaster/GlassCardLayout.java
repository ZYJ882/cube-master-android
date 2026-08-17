package com.manus.cubemaster;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/** 可复用的液态玻璃卡片：半透明层、高光边缘和低对比阴影。 */
public class GlassCardLayout extends LinearLayout {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int tint = Color.argb(46, 255, 255, 255);
    private float corner;

    public GlassCardLayout(Context context) { super(context); init(); }
    public GlassCardLayout(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setWillNotDraw(false);
        setOrientation(VERTICAL);
        corner = dp(28);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(1));
        borderPaint.setColor(Color.argb(112, 225, 244, 255));
        glowPaint.setColor(Color.argb(44, 0, 0, 0));
        glowPaint.setShadowLayer(dp(16), 0, dp(8), Color.argb(60, 0, 0, 0));
    }

    public void setGlassTint(int color) {
        tint = color;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        RectF rect = new RectF(dp(2), dp(2), getWidth() - dp(2), getHeight() - dp(2));
        canvas.drawRoundRect(rect, corner, corner, glowPaint);
        fillPaint.setShader(new LinearGradient(0, 0, getWidth(), getHeight(),
                new int[]{Color.argb(Math.min(120, Color.alpha(tint) + 30), 255, 255, 255), tint, Color.argb(Math.max(18, Color.alpha(tint) - 16), 173, 203, 255)},
                new float[]{0f, .42f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, corner, corner, fillPaint);
        fillPaint.setShader(null);
        canvas.drawRoundRect(rect, corner, corner, borderPaint);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
}
