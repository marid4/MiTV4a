package com.crb.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.io.File;
import java.util.Calendar;

/**
 * Generator Wallpaper HD & Pemuat Foto Kustom dari Penyimpanan TV/HP:
 * Menyediakan 6 tema wallpaper artistik bawaan (hemat RAM, resolusi 960x540 RGB_565)
 * serta mendukung pemuatan gambar wallpaper kustom (.jpg/.png) milik pengguna.
 */
public final class Sky {

    public static final int WALLPAPER_TIME_OF_DAY = 0;
    public static final int WALLPAPER_CIREBON_EMERALD = 1;
    public static final int WALLPAPER_STADIUM_NIGHT = 2;
    public static final int WALLPAPER_AURORA_BOREALIS = 3;
    public static final int WALLPAPER_SUNSET_MOUNTAINS = 4;
    public static final int WALLPAPER_OLED_PITCH_BLACK = 5;
    public static final int WALLPAPER_CUSTOM_IMAGE = 6;

    public static final String[] WALLPAPER_NAMES = new String[] {
            "Langit Dinamis Waktu Nyata (Pagi/Siang/Senja/Malam)",
            "Masjid Raya & Langit Zamrud Cirebon (Emerald Night)",
            "Stadion Sepak Bola Malam Hari (Champions Stadium Arena)",
            "Aurora Borealis (Teal & Violet Night Sky)",
            "Pegunungan Ciremai Senja (Sunset Mountain Silhouette)",
            "OLED Pitch Black (Hitam Pekat Hemat Layar)",
            "Foto Kustom dari Memori Internal / Galeri TV & HP"
    };

    public static Bitmap loadOrRenderWallpaper(Context context, int preset, int intensityPercent, String customPath) {
        if (preset == WALLPAPER_CUSTOM_IMAGE) {
            Bitmap customBmp = decodeCustomWallpaper(context, customPath);
            if (customBmp != null) {
                return customBmp;
            }
        }
        return renderPresetWallpaper(preset, intensityPercent);
    }

    private static Bitmap decodeCustomWallpaper(Context context, String customPath) {
        try {
            File target = null;
            if (customPath != null && !customPath.isEmpty()) {
                File f = new File(customPath);
                if (f.exists() && f.isFile()) target = f;
            }
            if (target == null) {
                File internalWall = new File(context.getFilesDir(), "custom_wallpaper.jpg");
                if (internalWall.exists() && internalWall.isFile()) {
                    target = internalWall;
                } else {
                    File themeWall = new File(context.getFilesDir(), "wallpaper.jpg");
                    if (themeWall.exists() && themeWall.isFile()) {
                        target = themeWall;
                    }
                }
            }
            if (target == null) return null;

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(target.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sampleSize = 1;
            while (bounds.outWidth / sampleSize > 1920 || bounds.outHeight / sampleSize > 1080) {
                sampleSize *= 2;
            }

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(target.getAbsolutePath(), opts);
        } catch (Exception e) {
            return null;
        }
    }

    public static Bitmap renderPresetWallpaper(int preset, int intensityPercent) {
        int w = 960;
        int h = 540;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bmp);

        if (preset == WALLPAPER_OLED_PITCH_BLACK) {
            canvas.drawColor(0xFF000000);
            return bmp;
        }

        int topColor;
        int midColor;
        int bottomColor;
        int glowColor;
        int accentGlowColor;

        if (preset == WALLPAPER_CIREBON_EMERALD) {
            topColor = 0xFF041B15;
            midColor = 0xFF06372A;
            bottomColor = 0xFF051821;
            glowColor = 0x7710B981;
            accentGlowColor = 0x55FDE047;
        } else if (preset == WALLPAPER_STADIUM_NIGHT) {
            topColor = 0xFF060E24;
            midColor = 0xFF0C224A;
            bottomColor = 0xFF072B1F;
            glowColor = 0x8838BDF8;
            accentGlowColor = 0x6610B981;
        } else if (preset == WALLPAPER_AURORA_BOREALIS) {
            topColor = 0xFF09071E;
            midColor = 0xFF1A103C;
            bottomColor = 0xFF082032;
            glowColor = 0x882DD4BF;
            accentGlowColor = 0x77A855F7;
        } else if (preset == WALLPAPER_SUNSET_MOUNTAINS) {
            topColor = 0xFF140B29;
            midColor = 0xFF3B183F;
            bottomColor = 0xFF5A2618;
            glowColor = 0x88F59E0B;
            accentGlowColor = 0x66EC4899;
        } else {
            // WALLPAPER_TIME_OF_DAY
            Calendar cal = Calendar.getInstance();
            float timeHours = cal.get(Calendar.HOUR_OF_DAY) + (cal.get(Calendar.MINUTE) / 60.0f);
            if (timeHours >= 4.0f && timeHours < 6.5f) {
                topColor = 0xFF081226;
                midColor = 0xFF152A4A;
                bottomColor = 0xFF283B52;
                glowColor = 0x6610B981;
                accentGlowColor = 0x55F59E0B;
            } else if (timeHours >= 6.5f && timeHours < 15.5f) {
                topColor = 0xFF0A1931;
                midColor = 0xFF123059;
                bottomColor = 0xFF18456B;
                glowColor = 0x6638BDF8;
                accentGlowColor = 0x44A7F3D0;
            } else if (timeHours >= 15.5f && timeHours < 18.5f) {
                topColor = 0xFF14112E;
                midColor = 0xFF331B3F;
                bottomColor = 0xFF4D2419;
                glowColor = 0x77F59E0B;
                accentGlowColor = 0x55EF4444;
            } else {
                topColor = 0xFF040711;
                midColor = 0xFF0B1528;
                bottomColor = 0xFF091D26;
                glowColor = 0x5538BDF8;
                accentGlowColor = 0x446366F1;
            }
        }

        float scale = Math.max(0.25f, Math.min(1.0f, intensityPercent / 100.0f));
        topColor = scaleColor(topColor, scale);
        midColor = scaleColor(midColor, scale);
        bottomColor = scaleColor(bottomColor, scale);

        // 1. Base Sky Gradient
        Paint skyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        skyPaint.setShader(new LinearGradient(
                0, 0, 0, h,
                new int[] { topColor, midColor, bottomColor },
                new float[] { 0f, 0.58f, 1.0f },
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0, 0, w, h, skyPaint);

        // 2. Primary Atmospheric Radial Glow on the Right/Center Showcase Area
        Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setShader(new RadialGradient(
                w * 0.68f, h * 0.42f, w * 0.48f,
                glowColor, 0x00000000,
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0, 0, w, h, glowPaint);

        // 3. Secondary Accent Glow
        Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        accentPaint.setShader(new RadialGradient(
                w * 0.82f, h * 0.25f, w * 0.35f,
                accentGlowColor, 0x00000000,
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0, 0, w, h, accentPaint);

        // 4. Draw Subtle Starfield & Crescent / Mountain / Stadium / Mosque Silhouette on the open Right Side
        drawArtisticScenery(canvas, w, h, preset);

        return bmp;
    }

    private static void drawArtisticScenery(Canvas canvas, int w, int h, int preset) {
        Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        starPaint.setColor(0x55FFFFFF);
        int[][] stars = new int[][] {
                {420, 60, 2}, {510, 95, 1}, {610, 48, 2}, {720, 82, 2}, {840, 55, 1},
                {890, 120, 2}, {660, 135, 1}, {550, 160, 2}, {780, 150, 1}, {460, 130, 1}
        };
        for (int[] s : stars) {
            canvas.drawCircle(s[0], s[1], s[2], starPaint);
        }

        // Draw layered mountain/horizon silhouettes on the right showcase area (Gunung Ciremai / Horizon)
        Paint backHill = new Paint(Paint.ANTI_ALIAS_FLAG);
        backHill.setStyle(Paint.Style.FILL);
        backHill.setColor(0x44050811);

        Path hill1 = new Path();
        hill1.moveTo(0, h);
        hill1.lineTo(0, h * 0.72f);
        hill1.cubicTo(w * 0.25f, h * 0.66f, w * 0.45f, h * 0.50f, w * 0.66f, h * 0.46f);
        hill1.cubicTo(w * 0.80f, h * 0.44f, w * 0.90f, h * 0.58f, w, h * 0.64f);
        hill1.lineTo(w, h);
        hill1.close();
        canvas.drawPath(hill1, backHill);

        Paint frontHill = new Paint(Paint.ANTI_ALIAS_FLAG);
        frontHill.setStyle(Paint.Style.FILL);
        frontHill.setColor(0x88030509);

        Path hill2 = new Path();
        hill2.moveTo(0, h);
        hill2.lineTo(0, h * 0.80f);
        hill2.cubicTo(w * 0.30f, h * 0.75f, w * 0.55f, h * 0.68f, w * 0.76f, h * 0.62f);
        hill2.cubicTo(w * 0.88f, h * 0.59f, w * 0.95f, h * 0.68f, w, h * 0.72f);
        hill2.lineTo(w, h);
        hill2.close();
        canvas.drawPath(hill2, frontHill);

        if (preset == WALLPAPER_CIREBON_EMERALD) {
            // Subtle Crescent Moon in the upper-right sky
            Paint moonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            moonPaint.setColor(0xCCFDE047);
            canvas.drawCircle(w * 0.78f, h * 0.22f, 26f, moonPaint);
            moonPaint.setColor(0xFF052920);
            canvas.drawCircle(w * 0.78f + 9f, h * 0.22f - 5f, 23f, moonPaint);
        } else if (preset == WALLPAPER_STADIUM_NIGHT) {
            // Subtle Stadium Pitch Lines in the lower-right horizon
            Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(1.5f);
            linePaint.setColor(0x3338BDF8);
            canvas.drawCircle(w * 0.68f, h * 0.72f, 58f, linePaint);
            canvas.drawLine(w * 0.38f, h * 0.72f, w * 0.95f, h * 0.72f, linePaint);
        }
    }

    private static int scaleColor(int color, float factor) {
        int r = Math.min(255, (int) (Color.red(color) * factor));
        int g = Math.min(255, (int) (Color.green(color) * factor));
        int b = Math.min(255, (int) (Color.blue(color) * factor));
        return Color.rgb(r, g, b);
    }
}
