package com.crb.tv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Menggambar pita Dock bulat (Apple TV 4K style) di belakang baris pertama (Dock Row)
 * langsung pada Canvas RecyclerView tanpa menambah View overhead.
 */
public final class DockDecoration extends RecyclerView.ItemDecoration {

    private final Paint bandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bandRect = new RectF();
    private final int columns;
    private final boolean enabled;

    public DockDecoration(int columns, int opacityPercent, boolean glassStyle, boolean enabled) {
        this.columns = Math.max(1, columns);
        this.enabled = enabled;
        int alpha = (int) (255 * (Math.max(30, Math.min(100, opacityPercent)) / 100.0f));
        int baseColor = glassStyle ? 0x1E293B : 0x141416;
        bandPaint.setStyle(Paint.Style.FILL);
        bandPaint.setColor((alpha << 24) | baseColor);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(0x28FFFFFF);
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (!enabled || parent.getChildCount() == 0) return;

        float density = parent.getResources().getDisplayMetrics().density;
        borderPaint.setStrokeWidth(1.0f * density);

        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = Float.MIN_VALUE;
        float bottom = Float.MIN_VALUE;
        boolean foundDockChild = false;

        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);
            int adapterPos = parent.getChildAdapterPosition(child);
            if (adapterPos >= 0 && adapterPos < columns) {
                foundDockChild = true;
                left = Math.min(left, child.getLeft());
                top = Math.min(top, child.getTop());
                right = Math.max(right, child.getRight());
                bottom = Math.max(bottom, child.getBottom());
            }
        }

        if (foundDockChild) {
            float padH = 10f * density;
            float padV = 6f * density;
            bandRect.set(
                    Math.max(0, left - padH),
                    top - padV,
                    Math.min(parent.getWidth(), right + padH),
                    bottom + padV
            );
            float radius = 22f * density;
            c.drawRoundRect(bandRect, radius, radius, bandPaint);
            c.drawRoundRect(bandRect, radius, radius, borderPaint);
        }
    }
}
