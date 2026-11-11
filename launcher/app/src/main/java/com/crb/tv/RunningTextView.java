package com.crb.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/**
 * View Teks Berjalan (Running Text / Marquee) 60fps untuk Crb Launcher:
 * Berjalan mulus terus-menerus dari kanan ke kiri tanpa terhenti meskipun fokus remote berpindah.
 */
public class RunningTextView extends View {

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String text = "Selamat Datang di Crb Launcher Kota Cirebon  ***  Utamakan Sholat Tepat Waktu  ***  ";
    private float scrollXPos = 0f;
    private float textWidth = 0f;
    private float speedPxPerFrame = 1.8f;
    private boolean isRunning = true;

    public RunningTextView(Context context) {
        super(context);
        init();
    }

    public RunningTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RunningTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        textPaint.setColor(0xFFFDE047);
        textPaint.setTextSize(11.5f * scaledDensity);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setShadowLayer(4f, 0f, 1.5f, 0xEE000000);
        measureCurrentText();
    }

    public void setRunningText(String newText) {
        if (newText == null || newText.trim().isEmpty()) {
            newText = "Selamat Datang di Crb Launcher Kota Cirebon";
        }
        String formatted = newText.trim() + "     ***     ";
        if (!formatted.equals(this.text)) {
            this.text = formatted;
            measureCurrentText();
            invalidate();
        }
    }

    public void setTextColorValue(int color) {
        textPaint.setColor(color);
        invalidate();
    }

    public void setSpeedLevel(int speedLevel) {
        float density = getResources().getDisplayMetrics().density;
        if (speedLevel <= 1) {
            speedPxPerFrame = 0.9f * density; // Lambat
        } else if (speedLevel == 2) {
            speedPxPerFrame = 1.6f * density; // Normal
        } else {
            speedPxPerFrame = 2.6f * density; // Cepat
        }
    }

    public void setRunning(boolean running) {
        this.isRunning = running;
        if (running) {
            postInvalidateOnAnimation();
        }
    }

    private void measureCurrentText() {
        textWidth = Math.max(100f, textPaint.measureText(text));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        measureCurrentText();
        if (scrollXPos == 0f) {
            scrollXPos = w * 0.25f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (viewWidth <= 0 || viewHeight <= 0 || textWidth <= 0f) return;

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baselineY = (viewHeight - fm.bottom - fm.top) / 2f;

        float drawX = scrollXPos;
        while (drawX < viewWidth) {
            canvas.drawText(text, drawX, baselineY, textPaint);
            drawX += textWidth;
        }

        if (isRunning && getVisibility() == VISIBLE) {
            scrollXPos -= speedPxPerFrame;
            if (scrollXPos <= -textWidth) {
                scrollXPos += textWidth;
            }
            postInvalidateOnAnimation();
        }
    }
}
