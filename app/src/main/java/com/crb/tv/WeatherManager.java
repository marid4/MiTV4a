package com.crb.tv;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Pengelola Cuaca Real-Time untuk wilayah Kelurahan Karya Mulya,
 * Kecamatan Kesambi, Kota Cirebon (-6.7358, 108.5385).
 * Mengambil data cuaca dari Open-Meteo API tanpa memerlukan API key,
 * dilengkapi estimasi cuaca tropis Cirebon jika TV sedang offline.
 */
public final class WeatherManager {

    public static final String DEFAULT_AREA_NAME = "Karya Mulya, Kota Cirebon";
    public static final double KARYA_MULYA_LAT = -6.7358;
    public static final double KARYA_MULYA_LNG = 108.5385;

    public static final class WeatherInfo {
        public final String areaName;
        public final double temperatureC;
        public final int weatherCode;
        public final String conditionText;
        public final double windSpeedKmh;
        public final int humidityPercent;
        public final boolean isOnlineData;
        public final String updatedTimeWib;

        public WeatherInfo(
                String areaName, double temperatureC, int weatherCode,
                String conditionText, double windSpeedKmh, int humidityPercent,
                boolean isOnlineData, String updatedTimeWib) {
            this.areaName = areaName;
            this.temperatureC = temperatureC;
            this.weatherCode = weatherCode;
            this.conditionText = conditionText;
            this.windSpeedKmh = windSpeedKmh;
            this.humidityPercent = humidityPercent;
            this.isOnlineData = isOnlineData;
            this.updatedTimeWib = updatedTimeWib;
        }

        public String toCompactSummary() {
            int tempRounded = (int) Math.round(temperatureC);
            return areaName + ": " + tempRounded + " C | " + conditionText
                    + " | Lembab " + humidityPercent + "%";
        }

        public String toDetailedReport() {
            int tempRounded = (int) Math.round(temperatureC);
            return "Wilayah     : Kelurahan Karya Mulya, Kec. Kesambi, Kota Cirebon\n"
                    + "Koordinat   : " + KARYA_MULYA_LAT + ", " + KARYA_MULYA_LNG + "\n"
                    + "Suhu Udara  : " + String.format("%.1f C (%d C)", temperatureC, tempRounded) + "\n"
                    + "Kondisi     : " + conditionText + "\n"
                    + "Kelembaban  : " + humidityPercent + " %\n"
                    + "Angin       : " + String.format("%.1f km/jam", windSpeedKmh) + "\n"
                    + "Sumber Data : " + (isOnlineData ? "Open-Meteo Live Weather API" : "Estimasi Iklim Cirebon (Offline)") + "\n"
                    + "Update      : " + updatedTimeWib + " WIB";
        }
    }

    private WeatherInfo currentWeather;

    public WeatherManager() {
        this.currentWeather = buildFallbackWeather();
    }

    public synchronized WeatherInfo getCurrentWeather() {
        if (currentWeather == null) {
            currentWeather = buildFallbackWeather();
        }
        return currentWeather;
    }

    public WeatherInfo fetchKaryaMulyaWeatherSync() {
        HttpURLConnection conn = null;
        try {
            String urlStr = "https://api.open-meteo.com/v1/forecast?latitude="
                    + KARYA_MULYA_LAT + "&longitude=" + KARYA_MULYA_LNG
                    + "&current_weather=true&hourly=relativehumidity_2m&timezone=Asia%2FJakarta";
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(7000);
            conn.setRequestProperty("User-Agent", "CleanYourTV-Cirebon-Launcher/2.0");

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject root = new JSONObject(sb.toString());
                JSONObject cw = root.optJSONObject("current_weather");
                if (cw != null) {
                    double temp = cw.optDouble("temperature", 30.0);
                    double wind = cw.optDouble("windspeed", 8.5);
                    int code = cw.optInt("weathercode", 1);

                    int humidity = 72;
                    JSONObject hourly = root.optJSONObject("hourly");
                    if (hourly != null) {
                        JSONArray humArr = hourly.optJSONArray("relativehumidity_2m");
                        if (humArr != null && humArr.length() > 0) {
                            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+7"));
                            int hourIdx = cal.get(Calendar.HOUR_OF_DAY);
                            if (hourIdx >= 0 && hourIdx < humArr.length()) {
                                humidity = humArr.optInt(hourIdx, 72);
                            } else {
                                humidity = humArr.optInt(0, 72);
                            }
                        }
                    }

                    Calendar now = Calendar.getInstance(TimeZone.getTimeZone("GMT+7"));
                    String timeStr = String.format("%02d:%02d",
                            now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
                    String cond = mapWmoCodeToIndonesian(code, now.get(Calendar.HOUR_OF_DAY));

                    WeatherInfo info = new WeatherInfo(
                            DEFAULT_AREA_NAME, temp, code, cond, wind, humidity, true, timeStr
                    );
                    synchronized (this) {
                        this.currentWeather = info;
                    }
                    return info;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        WeatherInfo fallback = buildFallbackWeather();
        synchronized (this) {
            if (this.currentWeather == null) {
                this.currentWeather = fallback;
            }
        }
        return getCurrentWeather();
    }

    private WeatherInfo buildFallbackWeather() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+7"));
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        String timeStr = String.format("%02d:%02d", hour, minute);

        double temp;
        String cond;
        int humidity;
        double wind;

        if (hour >= 6 && hour < 10) {
            temp = 28.0;
            cond = "Cerah Berawan";
            humidity = 76;
            wind = 7.5;
        } else if (hour >= 10 && hour < 15) {
            temp = 32.0;
            cond = "Cerah Terik";
            humidity = 65;
            wind = 11.0;
        } else if (hour >= 15 && hour < 18) {
            temp = 30.0;
            cond = "Cerah Berawan";
            humidity = 72;
            wind = 9.5;
        } else {
            temp = 26.5;
            cond = "Berawan Sejuk";
            humidity = 82;
            wind = 6.0;
        }

        return new WeatherInfo(
                DEFAULT_AREA_NAME, temp, 1, cond, wind, humidity, false, timeStr
        );
    }

    public static String mapWmoCodeToIndonesian(int code, int hour) {
        switch (code) {
            case 0:
                return (hour >= 6 && hour < 18) ? "Cerah" : "Malam Cerah";
            case 1:
            case 2:
                return "Cerah Berawan";
            case 3:
                return "Berawan";
            case 45:
            case 48:
                return "Berkabut";
            case 51:
            case 53:
            case 55:
                return "Gerimis";
            case 61:
                return "Hujan Ringan";
            case 63:
                return "Hujan Sedang";
            case 65:
                return "Hujan Lebat";
            case 80:
            case 81:
            case 82:
                return "Hujan Lokal";
            case 95:
            case 96:
            case 99:
                return "Hujan Petir";
            default:
                return "Cerah Berawan";
        }
    }
}
