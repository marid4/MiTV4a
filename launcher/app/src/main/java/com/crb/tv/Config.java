package com.crb.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Konfigurasi deklaratif Crb Launcher (com.crb.tv):
 * Mendukung Rolling Background, Screen Saver, Running Text (Edit Text di Setting),
 * Auto-Start saat Power On, Suara Adzan Muadzin Asli, Cuaca Karya Mulya, dan LiveScore.mobi.
 */
public final class Config {

    public static final String PREFS_NAME = "tvhome_prefs";

    public final List<String> dockApps = new ArrayList<>();
    public final List<String> hiddenApps = new ArrayList<>();
    public final List<String> favoriteApps = new ArrayList<>();

    public int columns = 1;
    public int rowsBelowDock = 2;
    public boolean fillDock = true;
    public boolean dockTitle = true;
    public boolean dockRow = true;
    public boolean dockBand = true;
    public boolean dockGlass = true;
    public int dockOpacity = 65;
    public boolean ambientGlow = true;
    public boolean appGlow = true;
    public boolean animations = true;
    public boolean advancedEffects = true;
    public boolean reducedMotion = false;
    public boolean focusOvershoot = true;
    public boolean alwaysShowNames = true;
    public boolean showGreeting = true;
    public String greetingName = "Cirebon";
    public boolean showStats = false;
    public boolean showNetworkPill = true;
    public boolean showInputsPill = true;
    public boolean tileMenu = true;
    public boolean appLibrary = true;
    public boolean moduleCards = true;
    public String clockFormat = "24";
    public int backgroundColor = Color.parseColor("#05070B");
    public int skyIntensity = 75;
    public String liveTvPackage = "";
    public String liveTvAction = "";

    // Pengaturan Wallpaper / Background (.jpg, .png, .gif, .mp4) & Rolling Background
    public int wallpaperPreset = Sky.WALLPAPER_CIREBON_EMERALD;
    public int wallpaperDimPercent = 0;
    public String customWallpaperPath = "";
    public boolean rollingBackgroundEnabled = false;
    public int rollingBackgroundIntervalSec = 30;
    public boolean rollingIncludePresets = true;
    public String rollingCustomFolder = "";

    // Modifikasi 1: Jadwal Sholat Kota Cirebon + Suara Adzan Muadzin Jernih
    public boolean showPrayerTimes = true;
    public String prayerCityName = "Kota Cirebon (Karya Mulya / Kesambi)";
    public double prayerLatitude = WeatherManager.KARYA_MULYA_LAT;
    public double prayerLongitude = WeatherManager.KARYA_MULYA_LNG;
    public int prayerTimezoneOffset = PrayerTimesCalculator.DEFAULT_TIMEZONE_WIB;
    public int prayerIhtiyatMinutes = PrayerTimesCalculator.DEFAULT_IHTIYAT_MINUTES;
    public boolean prayerOnlineSync = true;
    public boolean enableAdzanSound = true;
    public String customAdzanPath = "";
    public int adzanMuadzinIndex = 0;

    // Modifikasi 2: Cuaca Wilayah Karya Mulya, Kota Cirebon
    public boolean showWeatherKaryaMulya = true;

    // Modifikasi 3: Jadwal Match Football Real dari LiveScore.mobi
    public boolean showFootballSchedule = true;
    public boolean footballOnlineSync = true;
    public int footballRotateSeconds = 7;
    public String footballFilterSlug = "ALL";
    public final List<String> footballLeagues = new ArrayList<>();

    // Modifikasi 4: Menu Grid Kiri Berisi App Favorit
    public boolean favoriteAppsInitialized = false;

    // Modifikasi 5: Running Text (Teks Berjalan yang bisa diedit di Settings)
    public boolean showRunningText = true;
    public String runningTextContent = "Selamat Datang di Crb Launcher Kota Cirebon  |  Utamakan Sholat Tepat Waktu  |  Wilayah Kelurahan Karya Mulya, Kec. Kesambi, Kota Cirebon";
    public int runningTextSpeed = 2; // 1=Lambat, 2=Normal, 3=Cepat
    public boolean runningTextIncludePrayerWeather = true;

    // Modifikasi 6: Screen Saver & Auto-Start saat Power On
    public boolean screensaverEnabled = true;
    public int screensaverTimeoutSec = 300; // Default 5 menit (300 detik)
    public int screensaverStyle = 0; // 0=Jam+Sholat+Cuaca di atas Background, 1=Background Penuh+Jam Kecil, 2=Layar Hitam OLED
    public boolean autoStartOnPowerOn = true;

    public static Config load(Context context) {
        Config cfg = new Config();
        Resources res = context.getResources();

        cfg.dockApps.addAll(Arrays.asList(res.getStringArray(R.array.dock_apps)));
        cfg.hiddenApps.addAll(Arrays.asList(res.getStringArray(R.array.hidden_apps)));
        cfg.columns = clamp(res.getInteger(R.integer.columns), 1, 4);
        cfg.rowsBelowDock = clamp(res.getInteger(R.integer.rows_below_dock), 0, 4);
        cfg.fillDock = res.getBoolean(R.bool.fill_dock);
        cfg.dockTitle = res.getBoolean(R.bool.dock_title);
        cfg.dockRow = res.getBoolean(R.bool.dock_row);
        cfg.dockBand = res.getBoolean(R.bool.dock_band);
        cfg.dockGlass = res.getBoolean(R.bool.dock_glass);
        cfg.dockOpacity = clamp(res.getInteger(R.integer.dock_opacity), 30, 100);
        cfg.ambientGlow = res.getBoolean(R.bool.ambient_glow);
        cfg.appGlow = res.getBoolean(R.bool.app_glow);
        cfg.animations = res.getBoolean(R.bool.animations);
        cfg.advancedEffects = res.getBoolean(R.bool.advanced_effects);
        cfg.reducedMotion = res.getBoolean(R.bool.reduced_motion);
        cfg.focusOvershoot = res.getBoolean(R.bool.focus_overshoot);
        cfg.alwaysShowNames = res.getBoolean(R.bool.always_show_names);
        cfg.showGreeting = res.getBoolean(R.bool.show_greeting);
        cfg.greetingName = res.getString(R.string.greeting_name);
        cfg.showStats = res.getBoolean(R.bool.show_stats);
        cfg.showNetworkPill = res.getBoolean(R.bool.show_network_pill);
        cfg.showInputsPill = res.getBoolean(R.bool.show_inputs_pill);
        cfg.tileMenu = res.getBoolean(R.bool.tile_menu);
        cfg.appLibrary = res.getBoolean(R.bool.app_library);
        cfg.moduleCards = res.getBoolean(R.bool.module_cards);
        cfg.clockFormat = res.getString(R.string.clock_format);
        cfg.backgroundColor = parseColorSafe(res.getString(R.string.background_color), 0xFF05070B);
        cfg.skyIntensity = clamp(res.getInteger(R.integer.sky_intensity), 0, 100);
        cfg.liveTvPackage = res.getString(R.string.live_tv_package);
        cfg.liveTvAction = res.getString(R.string.live_tv_action);

        cfg.showPrayerTimes = res.getBoolean(R.bool.show_prayer_times);
        cfg.prayerCityName = "Kota Cirebon (Karya Mulya / Kesambi)";
        cfg.prayerLatitude = WeatherManager.KARYA_MULYA_LAT;
        cfg.prayerLongitude = WeatherManager.KARYA_MULYA_LNG;
        cfg.prayerTimezoneOffset = res.getInteger(R.integer.prayer_timezone_offset);
        cfg.prayerIhtiyatMinutes = res.getInteger(R.integer.prayer_ihtiyat_minutes);
        cfg.prayerOnlineSync = res.getBoolean(R.bool.prayer_online_sync);

        cfg.showFootballSchedule = res.getBoolean(R.bool.show_football_schedule);
        cfg.footballOnlineSync = res.getBoolean(R.bool.football_online_sync);
        cfg.footballRotateSeconds = clamp(res.getInteger(R.integer.football_rotate_seconds), 4, 60);
        cfg.footballLeagues.addAll(Arrays.asList(res.getStringArray(R.array.football_leagues)));

        File themeJson = new File(context.getFilesDir(), "config.json");
        if (themeJson.exists() && themeJson.isFile()) {
            applyJsonOverlay(cfg, themeJson);
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        cfg.columns = clamp(prefs.getInt("pref_columns_left", 1), 1, 4);
        cfg.showPrayerTimes = prefs.getBoolean("pref_show_prayer", cfg.showPrayerTimes);
        cfg.showFootballSchedule = prefs.getBoolean("pref_show_football", cfg.showFootballSchedule);
        cfg.showWeatherKaryaMulya = prefs.getBoolean("pref_show_weather", true);
        cfg.enableAdzanSound = prefs.getBoolean("pref_enable_adzan_sound", true);
        cfg.customAdzanPath = prefs.getString("pref_custom_adzan_path", "");
        cfg.adzanMuadzinIndex = clamp(prefs.getInt("pref_adzan_muadzin_idx", 0), 0, 4);
        cfg.showStats = prefs.getBoolean("pref_show_stats", cfg.showStats);
        cfg.reducedMotion = prefs.getBoolean("pref_reduced_motion", cfg.reducedMotion);
        cfg.prayerCityName = prefs.getString("pref_prayer_city", cfg.prayerCityName);
        cfg.wallpaperPreset = prefs.getInt("pref_wallpaper_preset", cfg.wallpaperPreset);
        cfg.wallpaperDimPercent = clamp(prefs.getInt("pref_wallpaper_dim", cfg.wallpaperDimPercent), 0, 80);
        cfg.customWallpaperPath = prefs.getString("pref_custom_wallpaper_path", "");
        cfg.rollingBackgroundEnabled = prefs.getBoolean("pref_rolling_bg_enabled", false);
        cfg.rollingBackgroundIntervalSec = clamp(prefs.getInt("pref_rolling_bg_interval_sec", 30), 5, 3600);
        cfg.rollingIncludePresets = prefs.getBoolean("pref_rolling_include_presets", true);
        cfg.rollingCustomFolder = prefs.getString("pref_rolling_custom_folder", "");
        cfg.footballFilterSlug = prefs.getString("pref_football_filter", "ALL");

        cfg.showRunningText = prefs.getBoolean("pref_show_running_text", true);
        cfg.runningTextContent = prefs.getString("pref_running_text_content", cfg.runningTextContent);
        cfg.runningTextSpeed = clamp(prefs.getInt("pref_running_text_speed", 2), 1, 3);
        cfg.runningTextIncludePrayerWeather = prefs.getBoolean("pref_running_text_include_info", true);

        cfg.screensaverEnabled = prefs.getBoolean("pref_screensaver_enabled", true);
        cfg.screensaverTimeoutSec = clamp(prefs.getInt("pref_screensaver_timeout_sec", 300), 15, 7200);
        cfg.screensaverStyle = clamp(prefs.getInt("pref_screensaver_style", 0), 0, 2);
        cfg.autoStartOnPowerOn = prefs.getBoolean("pref_auto_start_power_on", true);

        cfg.favoriteAppsInitialized = prefs.getBoolean("pref_favorites_initialized", false);
        String favCsv = prefs.getString("pref_favorite_apps_csv", "");
        cfg.favoriteApps.clear();
        if (favCsv != null && !favCsv.trim().isEmpty()) {
            String[] parts = favCsv.split(",");
            for (String p : parts) {
                String clean = p.trim();
                if (!clean.isEmpty() && !cfg.favoriteApps.contains(clean)) {
                    cfg.favoriteApps.add(clean);
                }
            }
        }

        try {
            cfg.prayerLatitude = Double.parseDouble(prefs.getString("pref_prayer_lat", String.valueOf(cfg.prayerLatitude)));
            cfg.prayerLongitude = Double.parseDouble(prefs.getString("pref_prayer_lng", String.valueOf(cfg.prayerLongitude)));
        } catch (Exception ignored) {
        }

        return cfg;
    }

    public void saveFavoriteApps(Context context) {
        Set<String> unique = new LinkedHashSet<>(favoriteApps);
        StringBuilder sb = new StringBuilder();
        for (String pkg : unique) {
            if (pkg == null || pkg.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append(",");
            sb.append(pkg.trim());
        }
        favoriteAppsInitialized = true;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean("pref_favorites_initialized", true)
                .putString("pref_favorite_apps_csv", sb.toString())
                .apply();
    }

    public boolean isFavorite(String pkg) {
        return pkg != null && favoriteApps.contains(pkg);
    }

    public boolean toggleFavorite(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        boolean added;
        if (favoriteApps.contains(pkg)) {
            favoriteApps.remove(pkg);
            added = false;
        } else {
            favoriteApps.add(pkg);
            added = true;
        }
        saveFavoriteApps(context);
        return added;
    }

    public static void saveUserPref(Context context, String key, boolean value) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply();
    }

    public static void saveUserIntPref(Context context, String key, int value) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(key, value).apply();
    }

    public static void saveUserStringPref(Context context, String key, String value) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(key, value).apply();
    }

    public static void savePrayerLocation(Context context, String cityName, double lat, double lng) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString("pref_prayer_city", cityName)
                .putString("pref_prayer_lat", String.valueOf(lat))
                .putString("pref_prayer_lng", String.valueOf(lng))
                .apply();
    }

    private static void applyJsonOverlay(Config cfg, File jsonFile) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(jsonFile), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            JSONObject obj = new JSONObject(sb.toString());

            if (obj.has("columns")) cfg.columns = clamp(obj.optInt("columns", cfg.columns), 1, 4);
            if (obj.has("rows_below_dock")) cfg.rowsBelowDock = clamp(obj.optInt("rows_below_dock", cfg.rowsBelowDock), 0, 4);
            if (obj.has("dock_opacity")) cfg.dockOpacity = clamp(obj.optInt("dock_opacity", cfg.dockOpacity), 30, 100);
            if (obj.has("sky_intensity")) cfg.skyIntensity = clamp(obj.optInt("sky_intensity", cfg.skyIntensity), 0, 100);
            if (obj.has("wallpaper_dim")) cfg.wallpaperDimPercent = clamp(obj.optInt("wallpaper_dim", cfg.wallpaperDimPercent), 0, 80);
            if (obj.has("reduced_motion")) cfg.reducedMotion = obj.optBoolean("reduced_motion", cfg.reducedMotion);
            if (obj.has("show_stats")) cfg.showStats = obj.optBoolean("show_stats", cfg.showStats);
            if (obj.has("show_greeting")) cfg.showGreeting = obj.optBoolean("show_greeting", cfg.showGreeting);
            if (obj.has("greeting_name")) cfg.greetingName = obj.optString("greeting_name", cfg.greetingName);
            if (obj.has("background_color")) {
                cfg.backgroundColor = parseColorSafe(obj.optString("background_color"), cfg.backgroundColor);
            }
            if (obj.has("show_prayer_times")) cfg.showPrayerTimes = obj.optBoolean("show_prayer_times", cfg.showPrayerTimes);
            if (obj.has("prayer_city_name")) cfg.prayerCityName = obj.optString("prayer_city_name", cfg.prayerCityName);
            if (obj.has("show_football_schedule")) cfg.showFootballSchedule = obj.optBoolean("show_football_schedule", cfg.showFootballSchedule);

            JSONArray dockArr = obj.optJSONArray("dock_apps");
            if (dockArr != null) {
                cfg.dockApps.clear();
                for (int i = 0; i < dockArr.length(); i++) {
                    String pkg = dockArr.optString(i, "");
                    if (!pkg.isEmpty()) cfg.dockApps.add(pkg);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    private static int parseColorSafe(String hex, int fallback) {
        try {
            return Color.parseColor(hex);
        } catch (Exception e) {
            return fallback;
        }
    }
}
