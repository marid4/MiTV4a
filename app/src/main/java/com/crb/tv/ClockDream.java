package com.crb.tv;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

/**
 * Screensaver (Daydream) CleanYourTV + Jadwal Sholat Kota Cirebon:
 * Menampilkan jam besar, tanggal Masehi & Hijriyah, serta hitung mundur waktu sholat Kota Cirebon
 * yang bergeser perlahan setiap menit untuk mencegah OLED burn-in.
 */
public class ClockDream extends DreamService {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private LinearLayout contentBox;
    private TextView clockText;
    private TextView dateText;
    private TextView prayerText;
    private TextView scheduleBarText;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            updateClockAndPrayer();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        contentBox = new LinearLayout(this);
        contentBox.setOrientation(LinearLayout.VERTICAL);
        contentBox.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        root.addView(contentBox, lp);

        clockText = new TextView(this);
        clockText.setTextColor(0xEEFFFFFF);
        clockText.setTextSize(68f);
        clockText.setGravity(Gravity.CENTER);
        contentBox.addView(clockText);

        dateText = new TextView(this);
        dateText.setTextColor(0xAAFFFFFF);
        dateText.setTextSize(16f);
        dateText.setGravity(Gravity.CENTER);
        contentBox.addView(dateText);

        prayerText = new TextView(this);
        prayerText.setTextColor(0xFF10B981);
        prayerText.setTextSize(18f);
        prayerText.setGravity(Gravity.CENTER);
        prayerText.setPadding(0, 18, 0, 6);
        contentBox.addView(prayerText);

        scheduleBarText = new TextView(this);
        scheduleBarText.setTextColor(0x99FFFFFF);
        scheduleBarText.setTextSize(13f);
        scheduleBarText.setGravity(Gravity.CENTER);
        contentBox.addView(scheduleBarText);

        setContentView(root);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        handler.post(tickRunnable);
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacks(tickRunnable);
        super.onDreamingStopped();
    }

    private void updateClockAndPrayer() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"));
        SimpleDateFormat clockFmt = new SimpleDateFormat("HH:mm", Locale.US);
        clockFmt.setTimeZone(TimeZone.getTimeZone("Asia/Jakarta"));
        clockText.setText(clockFmt.format(new Date()));

        SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE, d MMMM yyyy", new Locale("id", "ID"));
        dateFmt.setTimeZone(TimeZone.getTimeZone("Asia/Jakarta"));
        String hijri = PrayerTimesCalculator.getHijriDateString(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
        );
        dateText.setText(dateFmt.format(new Date()) + "  |  " + hijri);

        PrayerTimesCalculator.DailyPrayerSchedule sched = PrayerTimesCalculator.calculateCirebonToday();
        PrayerTimesCalculator.NextPrayerInfo next = PrayerTimesCalculator.getNextPrayer(
                sched,
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                cal.get(Calendar.SECOND)
        );
        prayerText.setText("Kota Cirebon | " + next.formatCountdown());
        scheduleBarText.setText(String.format(Locale.US,
                "Imsak %s  |  Subuh %s  |  Dzuhur %s  |  Ashar %s  |  Maghrib %s  |  Isya %s",
                sched.getImsak(), sched.getSubuh(), sched.getDzuhur(),
                sched.getAshar(), sched.getMaghrib(), sched.getIsya()
        ));

        // Anti-burn-in drift setiap menit penuh
        if (cal.get(Calendar.SECOND) == 0 && contentBox != null) {
            float dx = (random.nextInt(81) - 40);
            float dy = (random.nextInt(61) - 30);
            contentBox.animate().translationX(dx).translationY(dy).setDuration(800L).start();
        }
    }
}
