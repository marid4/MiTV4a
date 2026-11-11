package com.crb.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Floating Glass Highlight View (Semi-Transparan agar Wallpaper tetap terlihat jelas).
 */
public class GlassView extends View {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF sheenRect = new RectF();

    private int accentColor = 0xFFFFFFFF;
    private float cornerRadiusDp = 16f;

    public GlassView(Context context) {
        super(context);
        init();
    }

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GlassView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        rimPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStyle(Paint.Style.STROKE);
    }

    public void setAccentColor(int color) {
        if (this.accentColor != color) {
            this.accentColor = color;
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            fillPaint.setShader(new LinearGradient(
                    0, 0, 0, h,
                    0x1AFFFFFF, 0x08FFFFFF,
                    Shader.TileMode.CLAMP
            ));
            sheenPaint.setShader(new LinearGradient(
                    0, 0, 0, h * 0.45f,
                    0x22FFFFFF, 0x00FFFFFF,
                    Shader.TileMode.CLAMP
            ));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float radius = cornerRadiusDp * density;
        float inset = 3f * density;

        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);

        int r = Color.red(accentColor);
        int g = Color.green(accentColor);
        int b = Color.blue(accentColor);

        for (int i = 2; i >= 1; i--) {
            float expand = i * 1.4f * density;
            glowPaint.setStrokeWidth(1.5f * density);
            glowPaint.setColor(Color.argb(18 / i, r, g, b));
            RectF glowRect = new RectF(
                    rect.left - expand, rect.top - expand,
                    rect.right + expand, rect.bottom + expand
            );
            canvas.drawRoundRect(glowRect, radius + expand * 0.5f, radius + expand * 0.5f, glowPaint);
        }

        canvas.drawRoundRect(rect, radius, radius, fillPaint);

        sheenRect.set(rect.left + density, rect.top + density, rect.right - density, rect.top + rect.height() * 0.38f);
        canvas.drawRoundRect(sheenRect, radius, radius, sheenPaint);

        rimPaint.setStrokeWidth(1.0f * density);
        rimPaint.setColor(0x66FFFFFF);
        canvas.drawRoundRect(rect, radius, radius, rimPaint);
    }
}
