package com.crb.tv;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pengelola Jadwal Live, Hasil Pertandingan (FT), dan Jadwal Mendatang Sepak Bola
 * yang mengambil data real-time langsung dari LiveScore.mobi
 * (https://www.livescore.mobi/ & API JSON resmi LiveScore Mobile).
 */
public final class FootballScheduleManager {

    public static final String FILTER_ALL = "ALL";
    public static final String FILTER_LIVE = "LIVE";
    public static final String FILTER_RESULT = "RESULT";
    public static final String FILTER_UPCOMING = "UPCOMING";

    public static final class FootballMatch {
        public final String leagueName;
        public final String matchCategory; // "LIVE", "RESULT", "UPCOMING"
        public final String homeTeam;
        public final String awayTeam;
        public final String homeScore;
        public final String awayScore;
        public final String statusText; // e.g. "LIVE 67'", "HT", "FT", "19:30 WIB"
        public final long kickoffEpochMillis;
        public final String kickoffWibFormatted;
        public final String statusBadge;

        public FootballMatch(
                String leagueName,
                String matchCategory,
                String homeTeam,
                String awayTeam,
                String homeScore,
                String awayScore,
                String statusText,
                long kickoffEpochMillis,
                String kickoffWibFormatted,
                String statusBadge) {
            this.leagueName = leagueName;
            this.matchCategory = matchCategory;
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
            this.homeScore = homeScore;
            this.awayScore = awayScore;
            this.statusText = statusText;
            this.kickoffEpochMillis = kickoffEpochMillis;
            this.kickoffWibFormatted = kickoffWibFormatted;
            this.statusBadge = statusBadge;
        }

        public String getDisplayHeadline() {
            if ("LIVE".equals(matchCategory) || "RESULT".equals(matchCategory)) {
                return homeTeam + "  " + homeScore + " - " + awayScore + "  " + awayTeam;
            }
            return homeTeam + " vs " + awayTeam;
        }
    }

    private final List<FootballMatch> matches = new ArrayList<>();
    private int currentIndex = 0;
    private String activeFilter = FILTER_ALL;
    private boolean hasOnlineSyncSucceeded = false;
    private String lastSyncTimeWib = "-";

    public FootballScheduleManager() {
        loadFallbackMatches();
    }

    public synchronized void setFilterSlug(String filter) {
        this.activeFilter = (filter == null || filter.isEmpty()) ? FILTER_ALL : filter;
        this.currentIndex = 0;
    }

    public synchronized String getFilterSlug() {
        return activeFilter;
    }

    public synchronized boolean isOnlineLiveScoreActive() {
        return hasOnlineSyncSucceeded;
    }

    public synchronized String getLastSyncTimeWib() {
        return lastSyncTimeWib;
    }

    public synchronized List<FootballMatch> getMatches() {
        if (matches.isEmpty()) {
            loadFallbackMatches();
        }
        List<FootballMatch> filtered = new ArrayList<>();
        for (FootballMatch m : matches) {
            if (FILTER_ALL.equalsIgnoreCase(activeFilter)
                    || activeFilter.equalsIgnoreCase(m.matchCategory)) {
                filtered.add(m);
            }
        }
        if (filtered.isEmpty()) {
            return new ArrayList<>(matches);
        }
        return filtered;
    }

    public synchronized List<FootballMatch> getMatchesByCategory(String category) {
        if (matches.isEmpty()) {
            loadFallbackMatches();
        }
        List<FootballMatch> out = new ArrayList<>();
        for (FootballMatch m : matches) {
            if (category == null || FILTER_ALL.equalsIgnoreCase(category)
                    || category.equalsIgnoreCase(m.matchCategory)) {
                out.add(m);
            }
        }
        return out;
    }

    public synchronized int countByCategory(String category) {
        int count = 0;
        for (FootballMatch m : matches) {
            if (category.equalsIgnoreCase(m.matchCategory)) {
                count++;
            }
        }
        return count;
    }

    public synchronized FootballMatch getNextRotatedMatch() {
        List<FootballMatch> activeList = getMatches();
        if (activeList.isEmpty()) return null;
        if (currentIndex >= activeList.size()) {
            currentIndex = 0;
        }
        FootballMatch match = activeList.get(currentIndex);
        currentIndex = (currentIndex + 1) % activeList.size();
        return match;
    }

    public synchronized FootballMatch peekNextMatch() {
        List<FootballMatch> activeList = getMatches();
        if (activeList.isEmpty()) return null;
        int idx = currentIndex % activeList.size();
        return activeList.get(idx);
    }

    public synchronized int getCurrentDisplayIndex() {
        List<FootballMatch> activeList = getMatches();
        if (activeList.isEmpty()) return 0;
        return (currentIndex == 0 ? activeList.size() : currentIndex);
    }

    public synchronized int getActiveMatchCount() {
        return getMatches().size();
    }

    /**
     * Sinkronisasi data real-time dari LiveScore.mobi:
     * 1. Mengambil feed JSON LiveScore untuk Hari Ini, Kemarin (Hasil FT), dan Besok (Jadwal).
     * 2. Mengambil halaman HTML https://www.livescore.mobi/ sebagai pelengkap/fallback langsung.
     */
    public void refreshOnlineSchedulesSync(List<String> ignoredConfig) {
        List<FootballMatch> fetched = new ArrayList<>();

        TimeZone wibTz = TimeZone.getTimeZone("GMT+7");
        SimpleDateFormat ymdFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);
        ymdFormat.setTimeZone(wibTz);

        Calendar cal = Calendar.getInstance(wibTz);
        String todayYmd = ymdFormat.format(cal.getTime());

        Calendar calYesterday = (Calendar) cal.clone();
        calYesterday.add(Calendar.DAY_OF_YEAR, -1);
        String yesterdayYmd = ymdFormat.format(calYesterday.getTime());

        Calendar calTomorrow = (Calendar) cal.clone();
        calTomorrow.add(Calendar.DAY_OF_YEAR, 1);
        String tomorrowYmd = ymdFormat.format(calTomorrow.getTime());

        // 1. Fetch LiveScore JSON Feed (Digunakan oleh platform mobile LiveScore / livescore.mobi)
        fetchLiveScoreJsonForDate(todayYmd, "Hari Ini", fetched);
        fetchLiveScoreJsonForDate(yesterdayYmd, "Kemarin", fetched);
        fetchLiveScoreJsonForDate(tomorrowYmd, "Besok", fetched);

        // 2. Scrape HTML langsung dari https://www.livescore.mobi/
        fetchLiveScoreMobiHtml("https://www.livescore.mobi/", "Hari Ini", fetched);
        fetchLiveScoreMobiHtml("https://www.livescore.mobi/football/live", "LIVE", fetched);

        if (!fetched.isEmpty()) {
            // Sort: LIVE first, then UPCOMING soonest, then RESULT most recent
            Collections.sort(fetched, new Comparator<FootballMatch>() {
                @Override
                public int compare(FootballMatch a, FootballMatch b) {
                    int rankA = categoryPriority(a.matchCategory);
                    int rankB = categoryPriority(b.matchCategory);
                    if (rankA != rankB) {
                        return Integer.compare(rankA, rankB);
                    }
                    if ("RESULT".equals(a.matchCategory)) {
                        return Long.compare(b.kickoffEpochMillis, a.kickoffEpochMillis);
                    }
                    return Long.compare(a.kickoffEpochMillis, b.kickoffEpochMillis);
                }
            });

            Calendar nowWib = Calendar.getInstance(wibTz);
            String syncStamp = String.format(Locale.US, "%02d:%02d",
                    nowWib.get(Calendar.HOUR_OF_DAY), nowWib.get(Calendar.MINUTE));

            synchronized (this) {
                matches.clear();
                matches.addAll(fetched);
                currentIndex = 0;
                hasOnlineSyncSucceeded = true;
                lastSyncTimeWib = syncStamp;
            }
        }
    }

    private int categoryPriority(String cat) {
        if ("LIVE".equals(cat)) return 0;
        if ("UPCOMING".equals(cat)) return 1;
        return 2;
    }

    private void fetchLiveScoreJsonForDate(String ymd, String dayLabel, List<FootballMatch> out) {
        HttpURLConnection conn = null;
        try {
            // Endpoint publik LiveScore dengan timezone offset +7 (WIB)
            String urlStr = "https://prod-public-api.livescore.com/v1/api/app/date/soccer/"
                    + ymd + "/7?countryCode=ID&locale=en&MD=1";
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(7000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11; Mi TV) LiveScoreMobi/2.0");
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() != 200) return;

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONObject root = new JSONObject(sb.toString());
            JSONArray stages = root.optJSONArray("Stages");
            if (stages == null) return;

            int maxStages = Math.min(stages.length(), 28);
            for (int i = 0; i < maxStages; i++) {
                JSONObject stage = stages.optJSONObject(i);
                if (stage == null) continue;

                String country = sanitizeAscii(stage.optString("Cnm", ""));
                String stageName = sanitizeAscii(stage.optString("Snm", "Football"));
                String fullComp = country.isEmpty() ? stageName : (country + " - " + stageName);

                JSONArray events = stage.optJSONArray("Events");
                if (events == null) continue;

                for (int j = 0; j < events.length(); j++) {
                    JSONObject ev = events.optJSONObject(j);
                    if (ev == null) continue;

                    String home = extractTeamName(ev.optJSONArray("T1"), "Home");
                    String away = extractTeamName(ev.optJSONArray("T2"), "Away");
                    String tr1 = sanitizeAscii(ev.optString("Tr1", ""));
                    String tr2 = sanitizeAscii(ev.optString("Tr2", ""));
                    String eps = sanitizeAscii(ev.optString("Eps", "NS"));
                    long esd = ev.optLong("Esd", 0L);

                    long epochMs = parseEsdToEpochMillis(esd);
                    String timeWib = formatEsdTimeWib(esd);
                    String dateTimeWib = formatEsdDateTimeWib(esd, dayLabel);

                    String category;
                    String statusBadge;
                    String detailLine;

                    if (eps.contains("'") || "HT".equalsIgnoreCase(eps) || "ET".equalsIgnoreCase(eps)
                            || "LIVE".equalsIgnoreCase(eps)) {
                        category = FILTER_LIVE;
                        String s1 = tr1.isEmpty() ? "0" : tr1;
                        String s2 = tr2.isEmpty() ? "0" : tr2;
                        statusBadge = "LIVE " + eps;
                        detailLine = "[LIVE " + eps + "] Skor: " + s1 + " - " + s2 + " | " + dateTimeWib + " (livescore.mobi)";
                        addUniqueMatch(out, new FootballMatch(
                                fullComp, category, home, away, s1, s2, eps,
                                epochMs, detailLine, statusBadge
                        ));
                    } else if ("FT".equalsIgnoreCase(eps) || "AET".equalsIgnoreCase(eps)
                            || "AP".equalsIgnoreCase(eps) || "Fin".equalsIgnoreCase(eps)) {
                        category = FILTER_RESULT;
                        String s1 = tr1.isEmpty() ? "0" : tr1;
                        String s2 = tr2.isEmpty() ? "0" : tr2;
                        statusBadge = "HASIL " + eps.toUpperCase(Locale.US);
                        detailLine = "[HASIL " + eps.toUpperCase(Locale.US) + "] Skor Akhir: "
                                + s1 + " - " + s2 + " | " + dateTimeWib + " (livescore.mobi)";
                        addUniqueMatch(out, new FootballMatch(
                                fullComp, category, home, away, s1, s2, eps,
                                epochMs, detailLine, statusBadge
                        ));
                    } else if ("NS".equalsIgnoreCase(eps) || eps.contains(":")) {
                        category = FILTER_UPCOMING;
                        statusBadge = "JADWAL " + timeWib;
                        detailLine = "[JADWAL] Kickoff: " + dateTimeWib + " | livescore.mobi";
                        addUniqueMatch(out, new FootballMatch(
                                fullComp, category, home, away, "-", "-", timeWib,
                                epochMs, detailLine, statusBadge
                        ));
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Parser langsung halaman HTML https://www.livescore.mobi/ untuk mengekstrak
     * hasil pertandingan (FT), skor live (HT / menit'), serta jadwal pertandingan.
     */
    private void fetchLiveScoreMobiHtml(String urlStr, String defaultDayLabel, List<FootballMatch> out) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(7000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11; Mi TV) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36");

            if (conn.getResponseCode() != 200) return;

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();

            // Strip HTML tags into clean lines to parse livescore.mobi structure
            String text = sb.toString()
                    .replaceAll("(?is)<script.*?>.*?</script>", " ")
                    .replaceAll("(?is)<style.*?>.*?</style>", " ")
                    .replaceAll("(?is)<[^>]+>", "\n")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&");

            String[] rawLines = text.split("\n");
            List<String> tokens = new ArrayList<>();
            for (String r : rawLines) {
                String clean = sanitizeAscii(r).trim();
                if (!clean.isEmpty()) {
                    tokens.add(clean);
                }
            }

            String currentComp = "LiveScore.mobi International";
            Pattern timeOrStatusPat = Pattern.compile("^(\\d{2}:\\d{2}|FT|HT|AET|AP|\\d{1,3}'(?:\\+\\d+)?)$");
            Pattern scorePat = Pattern.compile("^(\\d+)\\s*-\\s*(\\d+)$");

            for (int i = 0; i < tokens.size(); i++) {
                String t = tokens.get(i);
                if (t.contains(" - ") && t.contains(":") && !timeOrStatusPat.matcher(t).matches() && t.length() < 65) {
                    currentComp = t;
                    continue;
                }
                Matcher mStatus = timeOrStatusPat.matcher(t);
                if (mStatus.matches() && i + 3 < tokens.size()) {
                    String status = mStatus.group(1);
                    String team1 = tokens.get(i + 1);
                    String mid = tokens.get(i + 2);
                    String team2 = tokens.get(i + 3);

                    Matcher mScore = scorePat.matcher(mid);
                    if (mScore.matches()) {
                        String s1 = mScore.group(1);
                        String s2 = mScore.group(2);
                        boolean isLive = status.contains("'") || "HT".equalsIgnoreCase(status);
                        String cat = isLive ? FILTER_LIVE : FILTER_RESULT;
                        String badge = isLive ? ("LIVE " + status) : ("HASIL " + status);
                        String detail = "[" + badge + "] Skor: " + s1 + " - " + s2 + " | livescore.mobi";
                        addUniqueMatch(out, new FootballMatch(
                                currentComp, cat, team1, team2, s1, s2, status,
                                System.currentTimeMillis(), detail, badge
                        ));
                        i += 3;
                    } else if ("-".equals(mid) || "v".equalsIgnoreCase(mid) || "vs".equalsIgnoreCase(mid)) {
                        String wibTime = convertUtcTimeStrToWib(status);
                        String badge = "JADWAL " + wibTime;
                        String detail = "[JADWAL] Kickoff: " + defaultDayLabel + " " + wibTime + " WIB | livescore.mobi";
                        addUniqueMatch(out, new FootballMatch(
                                currentComp, FILTER_UPCOMING, team1, team2, "-", "-", wibTime,
                                System.currentTimeMillis() + 3600000L, detail, badge
                        ));
                        i += 3;
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void addUniqueMatch(List<FootballMatch> list, FootballMatch candidate) {
        if (candidate.homeTeam.isEmpty() || candidate.awayTeam.isEmpty()) return;
        String key = (candidate.homeTeam + "_vs_" + candidate.awayTeam).toLowerCase(Locale.US);
        for (int i = 0; i < list.size(); i++) {
            FootballMatch existing = list.get(i);
            String exKey = (existing.homeTeam + "_vs_" + existing.awayTeam).toLowerCase(Locale.US);
            if (key.equals(exKey)) {
                return;
            }
        }
        list.add(candidate);
    }

    private String extractTeamName(JSONArray tArr, String fallback) {
        if (tArr != null && tArr.length() > 0) {
            JSONObject t0 = tArr.optJSONObject(0);
            if (t0 != null) {
                return sanitizeAscii(t0.optString("Nm", fallback));
            }
        }
        return fallback;
    }

    private long parseEsdToEpochMillis(long esd) {
        // esd is YYYYMMDDHHmmss in UTC+7 (since /7 was requested in the URL)
        if (esd < 20000000000000L) return System.currentTimeMillis();
        try {
            String s = String.valueOf(esd);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("GMT+7"));
            Date d = sdf.parse(s);
            return d != null ? d.getTime() : System.currentTimeMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private String formatEsdTimeWib(long esd) {
        String s = String.valueOf(esd);
        if (s.length() >= 12) {
            return s.substring(8, 10) + ":" + s.substring(10, 12) + " WIB";
        }
        return "WIB";
    }

    private String formatEsdDateTimeWib(long esd, String dayLabel) {
        String s = String.valueOf(esd);
        if (s.length() >= 12) {
            String dd = s.substring(6, 8);
            String mm = s.substring(4, 6);
            String hh = s.substring(8, 10);
            String min = s.substring(10, 12);
            return dayLabel + " (" + dd + "/" + mm + ") " + hh + ":" + min + " WIB";
        }
        return dayLabel + " WIB";
    }

    private String convertUtcTimeStrToWib(String hhmmUtc) {
        try {
            String[] parts = hhmmUtc.split(":");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            int wibH = (h + 7) % 24;
            return String.format(Locale.US, "%02d:%02d", wibH, m);
        } catch (Exception e) {
            return hhmmUtc;
        }
    }

    private static String sanitizeAscii(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= 32 && c < 127) {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    /**
     * Data cadangan (Live, Hasil FT, dan Jadwal Mendatang) bergaya LiveScore.mobi
     * saat TV baru dinyalakan sebelum Wi-Fi tersambung.
     */
    private void loadFallbackMatches() {
        matches.clear();
        TimeZone wib = TimeZone.getTimeZone("GMT+7");
        Calendar now = Calendar.getInstance(wib);
        long baseMs = now.getTimeInMillis();

        // 1. LIVE Match contoh sebelum sinkronisasi internet selesai
        matches.add(new FootballMatch(
                "Indonesia - BRI Liga 1", FILTER_LIVE,
                "Persib Bandung", "Persija Jakarta",
                "2", "1", "LIVE 74'",
                baseMs,
                "[LIVE 74'] Skor: 2 - 1 | Hari Ini 19:00 WIB (livescore.mobi)",
                "LIVE 74'"
        ));
        matches.add(new FootballMatch(
                "England - Premier League", FILTER_LIVE,
                "Arsenal", "Liverpool",
                "1", "1", "LIVE 58'",
                baseMs + 1000L,
                "[LIVE 58'] Skor: 1 - 1 | Hari Ini 22:30 WIB (livescore.mobi)",
                "LIVE 58'"
        ));

        // 2. HASIL PERTANDINGAN (FT)
        matches.add(new FootballMatch(
                "Spain - LaLiga", FILTER_RESULT,
                "Real Madrid", "Barcelona",
                "3", "1", "FT",
                baseMs - 7200000L,
                "[HASIL FT] Skor Akhir: 3 - 1 | Selesai (livescore.mobi)",
                "HASIL FT"
        ));
        matches.add(new FootballMatch(
                "Italy - Serie A", FILTER_RESULT,
                "Inter Milan", "AC Milan",
                "2", "0", "FT",
                baseMs - 10800000L,
                "[HASIL FT] Skor Akhir: 2 - 0 | Selesai (livescore.mobi)",
                "HASIL FT"
        ));
        matches.add(new FootballMatch(
                "Indonesia - BRI Liga 1", FILTER_RESULT,
                "Persebaya Surabaya", "Arema FC",
                "2", "1", "FT",
                baseMs - 14400000L,
                "[HASIL FT] Skor Akhir: 2 - 1 | Selesai (livescore.mobi)",
                "HASIL FT"
        ));

        // 3. JADWAL PERTANDINGAN MENDATANG (UPCOMING)
        matches.add(new FootballMatch(
                "UEFA - Champions League", FILTER_UPCOMING,
                "Bayern Munich", "Paris Saint-Germain",
                "-", "-", "03:00 WIB",
                baseMs + 18000000L,
                "[JADWAL] Kickoff: Besok 03:00 WIB | livescore.mobi",
                "JADWAL 03:00 WIB"
        ));
        matches.add(new FootballMatch(
                "AFC - World Cup Qualification", FILTER_UPCOMING,
                "Timnas Indonesia", "Jepang",
                "-", "-", "19:00 WIB",
                baseMs + 36000000L,
                "[JADWAL] Kickoff: Jumat 19:00 WIB | livescore.mobi",
                "JADWAL 19:00 WIB"
        ));
        matches.add(new FootballMatch(
                "England - Premier League", FILTER_UPCOMING,
                "Manchester City", "Tottenham Hotspur",
                "-", "-", "23:30 WIB",
                baseMs + 54000000L,
                "[JADWAL] Kickoff: Sabtu 23:30 WIB | livescore.mobi",
                "JADWAL 23:30 WIB"
        ));
    }
}
