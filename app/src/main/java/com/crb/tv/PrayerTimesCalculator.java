package com.crb.tv;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Perhitungan Jadwal Sholat Kota Cirebon berdasarkan standar resmi
 * Kementerian Agama RI (Bimas Islam / MABIMS):
 * - Sudut Subuh (Fajr): -20.0 derajat
 * - Sudut Isya (Isha): -18.0 derajat
 * - Ashar: Syafi'i (panjang bayangan = tinggi benda + bayangan saat Dzuhur)
 * - Ihtiyat: +2 menit untuk Subuh, Dzuhur, Ashar, Maghrib, Isya
 * - Imsak: 10 menit sebelum Subuh
 * - Koordinat Default Kota Cirebon: Lat -6.7320, Lng 108.5523, Zona Waktu WIB (UTC+7)
 *
 * Bekerja 100% secara offline dengan rumus astronomi presisi tinggi, serta
 * mendukung sinkronisasi opsional dengan API Aladhan (Method 20 = Kemenag RI).
 */
public final class PrayerTimesCalculator {

    public static final double DEFAULT_CIREBON_LAT = -6.7320;
    public static final double DEFAULT_CIREBON_LNG = 108.5523;
    public static final int DEFAULT_TIMEZONE_WIB = 7;
    public static final int DEFAULT_IHTIYAT_MINUTES = 2;

    public static final class DailyPrayerSchedule {
        public final String cityName;
        public final int year;
        public final int month; // 1-12
        public final int day;   // 1-31
        public final int imsakMinutes;
        public final int subuhMinutes;
        public final int terbitMinutes;
        public final int dhuhaMinutes;
        public final int dzuhurMinutes;
        public final int asharMinutes;
        public final int maghribMinutes;
        public final int isyaMinutes;
        public final boolean fromOnlineApi;

        public DailyPrayerSchedule(
                String cityName, int year, int month, int day,
                int imsakMinutes, int subuhMinutes, int terbitMinutes, int dhuhaMinutes,
                int dzuhurMinutes, int asharMinutes, int maghribMinutes, int isyaMinutes,
                boolean fromOnlineApi) {
            this.cityName = cityName;
            this.year = year;
            this.month = month;
            this.day = day;
            this.imsakMinutes = imsakMinutes;
            this.subuhMinutes = subuhMinutes;
            this.terbitMinutes = terbitMinutes;
            this.dhuhaMinutes = dhuhaMinutes;
            this.dzuhurMinutes = dzuhurMinutes;
            this.asharMinutes = asharMinutes;
            this.maghribMinutes = maghribMinutes;
            this.isyaMinutes = isyaMinutes;
            this.fromOnlineApi = fromOnlineApi;
        }

        public String getImsak() { return formatHhMm(imsakMinutes); }
        public String getSubuh() { return formatHhMm(subuhMinutes); }
        public String getTerbit() { return formatHhMm(terbitMinutes); }
        public String getDhuha() { return formatHhMm(dhuhaMinutes); }
        public String getDzuhur() { return formatHhMm(dzuhurMinutes); }
        public String getAshar() { return formatHhMm(asharMinutes); }
        public String getMaghrib() { return formatHhMm(maghribMinutes); }
        public String getIsya() { return formatHhMm(isyaMinutes); }
    }

    public static final class NextPrayerInfo {
        public final String name;
        public final String timeFormatted;
        public final int targetSecondsOfDay;
        public final int remainingSeconds;
        public final int activeIndex; // 0=Imsak, 1=Subuh, 2=Dzuhur, 3=Ashar, 4=Maghrib, 5=Isya

        public NextPrayerInfo(String name, String timeFormatted, int targetSecondsOfDay, int remainingSeconds, int activeIndex) {
            this.name = name;
            this.timeFormatted = timeFormatted;
            this.targetSecondsOfDay = targetSecondsOfDay;
            this.remainingSeconds = remainingSeconds;
            this.activeIndex = activeIndex;
        }

        public String formatCountdown() {
            int hrs = remainingSeconds / 3600;
            int mins = (remainingSeconds % 3600) / 60;
            int secs = remainingSeconds % 60;
            return String.format(Locale.US, "Menuju %s | %02d:%02d:%02d", name, hrs, mins, secs);
        }
    }

    /**
     * Menghitung jadwal sholat harian berdasarkan rumus astronomi Kemenag RI.
     */
    public static DailyPrayerSchedule calculateKemenag(
            String cityName, int year, int month, int day,
            double latitude, double longitude, int timezoneHours, int ihtiyatMinutes) {

        double jd = julianDate(year, month, day) - (longitude / (15.0 * 24.0));
        double[] sun = sunPosition(jd);
        double decl = sun[0];
        double eqTime = sun[1];

        // Transit (Dzuhur astronomis)
        double transit = 12.0 + timezoneHours - (longitude / 15.0) - eqTime;

        // Sudut waktu (Hour Angle)
        double haSubuh = hourAngle(latitude, decl, -20.0);
        double haTerbit = hourAngle(latitude, decl, -0.8333);
        double haDhuha = hourAngle(latitude, decl, 4.5);
        double haAshar = asrHourAngle(latitude, decl, 1.0); // Syafi'i = 1
        double haMaghrib = haTerbit;
        double haIsya = hourAngle(latitude, decl, -18.0);

        double rawSubuh = transit - haSubuh / 15.0;
        double rawTerbit = transit - haTerbit / 15.0;
        double rawDhuha = transit - haDhuha / 15.0;
        double rawDzuhur = transit;
        double rawAshar = transit + haAshar / 15.0;
        double rawMaghrib = transit + haMaghrib / 15.0;
        double rawIsya = transit + haIsya / 15.0;

        int subuhMin = toMinutesWithIhtiyat(rawSubuh, ihtiyatMinutes);
        int imsakMin = subuhMin - 10;
        int terbitMin = toMinutesWithIhtiyat(rawTerbit, -2); // Terbit dikurangi 2m ihtiyat
        int dhuhaMin = toMinutesWithIhtiyat(rawDhuha, ihtiyatMinutes);
        int dzuhurMin = toMinutesWithIhtiyat(rawDzuhur, ihtiyatMinutes + 1); // +3m total transit safety
        int asharMin = toMinutesWithIhtiyat(rawAshar, ihtiyatMinutes);
        int maghribMin = toMinutesWithIhtiyat(rawMaghrib, ihtiyatMinutes);
        int isyaMin = toMinutesWithIhtiyat(rawIsya, ihtiyatMinutes);

        return new DailyPrayerSchedule(
                cityName, year, month, day,
                imsakMin, subuhMin, terbitMin, dhuhaMin,
                dzuhurMin, asharMin, maghribMin, isyaMin,
                false
        );
    }

    public static DailyPrayerSchedule calculateCirebonToday() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"));
        return calculateKemenag(
                "Kota Cirebon, Jawa Barat",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
                DEFAULT_CIREBON_LAT,
                DEFAULT_CIREBON_LNG,
                DEFAULT_TIMEZONE_WIB,
                DEFAULT_IHTIYAT_MINUTES
        );
    }

    /**
     * Menentukan waktu sholat berikutnya beserta hitung mundur (countdown) detik demi detik.
     */
    public static NextPrayerInfo getNextPrayer(DailyPrayerSchedule sched, int currentHour, int currentMinute, int currentSecond) {
        int nowSec = currentHour * 3600 + currentMinute * 60 + currentSecond;

        int[] timesMin = new int[] {
                sched.imsakMinutes,
                sched.subuhMinutes,
                sched.dzuhurMinutes,
                sched.asharMinutes,
                sched.maghribMinutes,
                sched.isyaMinutes
        };
        String[] names = new String[] {
                "Imsak", "Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya"
        };

        for (int i = 0; i < timesMin.length; i++) {
            int targetSec = timesMin[i] * 60;
            if (targetSec > nowSec) {
                int diff = targetSec - nowSec;
                return new NextPrayerInfo(names[i], formatHhMm(timesMin[i]), targetSec, diff, i);
            }
        }

        // Jika sudah lewat Isya, maka target berikutnya adalah Subuh besok pagi
        int subuhTomorrowSec = (24 * 3600 - nowSec) + (sched.subuhMinutes * 60);
        return new NextPrayerInfo("Subuh", formatHhMm(sched.subuhMinutes), sched.subuhMinutes * 60, subuhTomorrowSec, 1);
    }

    /**
     * Sinkronisasi opsional dengan API Aladhan (Method 20 = Kemenag RI) untuk Kota Cirebon.
     * Jika tidak ada internet atau gagal, otomatis menggunakan hasil perhitungan offline Kemenag.
     */
    public static DailyPrayerSchedule fetchOnlineOrFallback(
            String cityName, int year, int month, int day,
            double lat, double lng, int tz, int ihtiyat) {
        DailyPrayerSchedule fallback = calculateKemenag(cityName, year, month, day, lat, lng, tz, ihtiyat);
        HttpURLConnection conn = null;
        try {
            String dateStr = String.format(Locale.US, "%02d-%02d-%04d", day, month, year);
            String urlStr = String.format(Locale.US,
                    "https://api.aladhan.com/v1/timings/%s?latitude=%.4f&longitude=%.4f&method=20&tune=2,2,0,3,2,2,0,2,0",
                    dateStr, lat, lng);
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                String json = sb.toString();

                int imsak = extractHhMm(json, "Imsak", fallback.imsakMinutes);
                int fajr = extractHhMm(json, "Fajr", fallback.subuhMinutes);
                int sunrise = extractHhMm(json, "Sunrise", fallback.terbitMinutes);
                int dhuhr = extractHhMm(json, "Dhuhr", fallback.dzuhurMinutes);
                int asr = extractHhMm(json, "Asr", fallback.asharMinutes);
                int maghrib = extractHhMm(json, "Maghrib", fallback.maghribMinutes);
                int isha = extractHhMm(json, "Isha", fallback.isyaMinutes);

                return new DailyPrayerSchedule(
                        cityName, year, month, day,
                        imsak, fajr, sunrise, fallback.dhuhaMinutes,
                        dhuhr, asr, maghrib, isha, true
                );
            }
        } catch (Exception ignored) {
            // Fallback ke perhitungan astronomi Kemenag RI lokal
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return fallback;
    }

    private static int extractHhMm(String json, String key, int defaultVal) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"(\\d{2}):(\\d{2})");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
        }
        return defaultVal;
    }

    public static String formatHhMm(int totalMinutes) {
        int normalized = ((totalMinutes % 1440) + 1440) % 1440;
        int h = normalized / 60;
        int m = normalized % 60;
        return String.format(Locale.US, "%02d:%02d", h, m);
    }

    private static int toMinutesWithIhtiyat(double hours, int ihtiyatMinutes) {
        int baseMinutes = (int) Math.ceil(hours * 60.0);
        return baseMinutes + ihtiyatMinutes;
    }

    private static double julianDate(int year, int month, int day) {
        int y = year;
        int m = month;
        if (m <= 2) {
            y -= 1;
            m += 12;
        }
        int a = y / 100;
        int b = 2 - a + (a / 4);
        return Math.floor(365.25 * (y + 4716))
                + Math.floor(30.6001 * (m + 1))
                + day + b - 1524.5;
    }

    private static double[] sunPosition(double jd) {
        double d = jd - 2451545.0;
        double g = fixAngle(357.529 + 0.98560028 * d);
        double q = fixAngle(280.459 + 0.98564736 * d);
        double l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2.0 * g));
        double e = 23.439 - 0.00000036 * d;
        double ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0;
        ra = fixHour(ra);
        double decl = darcsin(dsin(e) * dsin(l));
        double eqTime = q / 15.0 - ra;
        if (eqTime > 12.0) eqTime -= 24.0;
        if (eqTime < -12.0) eqTime += 24.0;
        return new double[] { decl, eqTime };
    }

    private static double hourAngle(double lat, double decl, double altitude) {
        double num = dsin(altitude) - dsin(lat) * dsin(decl);
        double den = dcos(lat) * dcos(decl);
        double val = Math.max(-1.0, Math.min(1.0, num / den));
        return darccos(val);
    }

    private static double asrHourAngle(double lat, double decl, double shadowFactor) {
        double angle = darccot(shadowFactor + dtan(Math.abs(lat - decl)));
        return hourAngle(lat, decl, angle);
    }

    /**
     * Konversi Tanggal Masehi ke Kalender Hijriyah (Kuwaiti / Kemenag Approximation)
     * untuk ditampilkan di baris tanggal atas layar TV.
     */
    public static String getHijriDateString(int year, int month, int day) {
        double jd = julianDate(year, month, day) + 0.5;
        int l = (int) jd - 1948440 + 10632;
        int n = (l - 1) / 10631;
        l = l - 10631 * n + 354;
        int j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((43 * l) / 15238);
        l = l - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29;
        int hMonth = (24 * l) / 709;
        int hDay = l - (709 * hMonth) / 24;
        int hYear = 30 * n + j - 30;

        String[] bulanHijriyah = new String[] {
                "", "Muharram", "Safar", "Rabiul Awal", "Rabiul Akhir",
                "Jumadil Awal", "Jumadil Akhir", "Rajab", "Sya'ban",
                "Ramadhan", "Syawal", "Dzulqa'dah", "Dzulhijjah"
        };
        String mName = (hMonth >= 1 && hMonth <= 12) ? bulanHijriyah[hMonth] : "Hijriyah";
        return hDay + " " + mName + " " + hYear + " H";
    }

    private static double fixAngle(double a) {
        a = a - 360.0 * Math.floor(a / 360.0);
        return a < 0 ? a + 360.0 : a;
    }

    private static double fixHour(double a) {
        a = a - 24.0 * Math.floor(a / 24.0);
        return a < 0 ? a + 24.0 : a;
    }

    private static double dsin(double d) { return Math.sin(Math.toRadians(d)); }
    private static double dcos(double d) { return Math.cos(Math.toRadians(d)); }
    private static double dtan(double d) { return Math.tan(Math.toRadians(d)); }
    private static double darcsin(double x) { return Math.toDegrees(Math.asin(x)); }
    private static double darccos(double x) { return Math.toDegrees(Math.acos(x)); }
    private static double darctan2(double y, double x) { return Math.toDegrees(Math.atan2(y, x)); }
    private static double darccot(double x) { return Math.toDegrees(Math.atan(1.0 / x)); }
}
