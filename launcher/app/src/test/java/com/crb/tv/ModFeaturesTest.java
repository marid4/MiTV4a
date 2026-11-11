package com.crb.tv;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

public class ModFeaturesTest {

    @Test
    public void testCirebonPrayerTimesKemenagAccuracy() {
        // Verifikasi jadwal Kota Cirebon tanggal 9 November 2026 (Data Kemenag RI: Imsak 03:49, Subuh 03:59, Ashar 14:51, Maghrib 17:44, Isya 18:57)
        PrayerTimesCalculator.DailyPrayerSchedule s = PrayerTimesCalculator.calculateKemenag(
                "Kota Cirebon", 2026, 11, 9,
                PrayerTimesCalculator.DEFAULT_CIREBON_LAT,
                PrayerTimesCalculator.DEFAULT_CIREBON_LNG,
                PrayerTimesCalculator.DEFAULT_TIMEZONE_WIB,
                PrayerTimesCalculator.DEFAULT_IHTIYAT_MINUTES
        );
        assertEquals("03:49", s.getImsak());
        assertEquals("03:59", s.getSubuh());
        assertEquals("14:51", s.getAshar());
        assertEquals("17:44", s.getMaghrib());
        assertEquals("18:57", s.getIsya());
    }

    @Test
    public void testNextPrayerCountdown() {
        PrayerTimesCalculator.DailyPrayerSchedule s = PrayerTimesCalculator.calculateKemenag(
                "Kota Cirebon", 2026, 11, 9,
                PrayerTimesCalculator.DEFAULT_CIREBON_LAT,
                PrayerTimesCalculator.DEFAULT_CIREBON_LNG,
                PrayerTimesCalculator.DEFAULT_TIMEZONE_WIB,
                PrayerTimesCalculator.DEFAULT_IHTIYAT_MINUTES
        );
        // Pukul 10:50:00 WIB -> Menuju Dzuhur
        PrayerTimesCalculator.NextPrayerInfo next = PrayerTimesCalculator.getNextPrayer(s, 10, 50, 0);
        assertEquals("Dzuhur", next.name);
        assertTrue(next.remainingSeconds > 0);
    }

    @Test
    public void testFallbackFootballScheduleNotEmpty() {
        List<FootballScheduleManager.FootballMatch> list = FootballScheduleManager.getCuratedFallbackSchedule();
        assertNotNull(list);
        assertFalse(list.isEmpty());
        assertEquals("BRI Liga 1 Indonesia", list.get(0).leagueName);
    }
}
