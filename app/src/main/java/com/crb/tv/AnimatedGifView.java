package com.crb.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Movie;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * View khusus untuk memutar Background Animasi GIF (.gif) secara mulus dan hemat RAM:
 * - Pada Android 9+ (API 28+): menggunakan ImageDecoder + AnimatedImageDrawable perangkat keras
 * - Pada Android 8.0/8.1 (API 26-27): menggunakan android.graphics.Movie dengan penskalaan center-crop.
 */
@SuppressWarnings("deprecation")
public class AnimatedGifView extends View {

    private Movie gifMovie;
    private long movieStartMs = 0L;
    private Drawable animatedDrawable;
    private boolean isPlaying = false;

    public AnimatedGifView(Context context) {
        super(context);
    }

    public AnimatedGifView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AnimatedGifView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public boolean setGifFile(File file) {
        stopGif();
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ImageDecoder.Source source = ImageDecoder.createSource(file);
                Drawable d = ImageDecoder.decodeDrawable(source);
                this.animatedDrawable = d;
                setBackground(d);
                if (d instanceof AnimatedImageDrawable) {
                    ((AnimatedImageDrawable) d).setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
                    ((AnimatedImageDrawable) d).start();
                }
                isPlaying = true;
                invalidate();
                return true;
            } catch ( Exception ignored) {
                // Fallback ke Movie di bawah jika ImageDecoder gagal
            }
        }

        InputStream is = null;
        try {
            is = new FileInputStream(file);
            gifMovie = Movie.decodeStream(is);
            movieStartMs = 0L;
            if (gifMovie != null && gifMovie.width() > 0 && gifMovie.height() > 0) {
                isPlaying = true;
                invalidate();
                return true;
            }
        } catch (Exception ignored) {
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception ignored) {}
            }
        }
        return false;
    }

    public void stopGif() {
        isPlaying = false;
        gifMovie = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && animatedDrawable instanceof AnimatedImageDrawable) {
            try {
                ((AnimatedImageDrawable) animatedDrawable).stop();
            } catch (Exception ignored) {
            }
        }
        animatedDrawable = null;
        setBackground(null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!isPlaying || gifMovie == null) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        if (movieStartMs == 0L) {
            movieStartMs = now;
        }
        int dur = gifMovie.duration();
        if (dur <= 0) {
            dur = 1000;
        }
        int relTime = (int) ((now - movieStartMs) % dur);
        gifMovie.setTime(relTime);

        int vw = getWidth();
        int vh = getHeight();
        int mw = gifMovie.width();
        int mh = gifMovie.height();

        if (vw > 0 && vh > 0 && mw > 0 && mh > 0) {
            float scale = Math.max((float) vw / (float) mw, (float) vh / (float) mh);
            float dx = (vw - (mw * scale)) * 0.5f;
            float dy = (vh - (mh * scale)) * 0.5f;
            canvas.save();
            canvas.translate(dx, dy);
            canvas.scale(scale, scale);
            gifMovie.draw(canvas, 0, 0);
            canvas.restore();
        }
        postInvalidateOnAnimation();
    }
}
