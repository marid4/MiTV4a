package com.crb.tv;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Pemutar Suara Adzan Jernih untuk Crb Launcher (com.crb.tv):
 * 1. Mengunduh otomatis & menyimpan (cache lokal) rekaman suara Muadzin asli berkualitas studio (.mp3)
 *    dari CDN resmi AlAdhan (Syaikh Mishary Rashid Alafasy, Ahmad Al-Nafees, Makkah, Mansour Al-Zahrani).
 * 2. Mendukung file MP3 Adzan kustom dari memori internal / transfer Wi-Fi HP.
 * 3. Cadangan Offline 100% Jernih menggunakan R.raw.adzan_chime (44.1kHz MP3) + Suara Vokal TextToSpeech Android.
 */
public final class AdzanAudioPlayer {

    public static final String[] MUADZIN_NAMES = new String[]{
            "1. Syaikh Mishary Rashid Alafasy (Suara Merdu & Jernih)",
            "2. Syaikh Ahmad Al-Nafees (Maqam Hijaz Khusyuk)",
            "3. Adzan Masjidil Haram Makkah Al-Mukarramah",
            "4. Syaikh Mansour Al-Zahrani",
            "5. Syaikh Hafiz Mustafa Ozcan"
    };

    public static final String[] MUADZIN_URLS = new String[]{
            "https://cdn.aladhan.com/audio/adhans/a9.mp3",
            "https://cdn.aladhan.com/audio/adhans/a1.mp3",
            "https://cdn.aladhan.com/audio/adhans/a4.mp3",
            "https://cdn.aladhan.com/audio/adhans/a11-mansour-al-zahrani.mp3",
            "https://cdn.aladhan.com/audio/adhans/a2.mp3"
    };

    public interface OnAdzanListener {
        void onLafadzChanged(String arabicTranslit, String indonesianMeaning);
        void onAdzanFinished();
    }

    private MediaPlayer mediaPlayer;
    private TextToSpeech ttsEngine;
    private volatile boolean isPlaying = false;
    private volatile boolean isDownloading = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private OnAdzanListener activeListener;

    public synchronized boolean isPlaying() {
        return isPlaying;
    }

    public synchronized void stop() {
        isPlaying = false;
        activeListener = null;
        mainHandler.removeCallbacksAndMessages(null);
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (Exception ignored) {
            }
            try {
                mediaPlayer.release();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }
        if (ttsEngine != null) {
            try {
                ttsEngine.stop();
                ttsEngine.shutdown();
            } catch (Exception ignored) {
            }
            ttsEngine = null;
        }
    }

    public static String getMuadzinLabel(int idx) {
        int safe = Math.max(0, Math.min(MUADZIN_NAMES.length - 1, idx));
        return MUADZIN_NAMES[safe];
    }

    public File getCachedMuadzinFile(Context context, int muadzinIdx) {
        int safe = Math.max(0, Math.min(MUADZIN_URLS.length - 1, muadzinIdx));
        File dir = new File(context.getFilesDir(), "adzan");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "muadzin_voice_" + safe + ".mp3");
    }

    /**
     * Mengunduh rekaman suara Adzan Muadzin asli di background agar tersimpan di memori TV
     * dan siap diputar kapan saja (baik saat online maupun offline).
     */
    public void prefetchMuadzinAudioAsync(final Context context, final int muadzinIdx, final Runnable onDone) {
        final File target = getCachedMuadzinFile(context, muadzinIdx);
        if (target.exists() && target.length() > 50000) {
            if (onDone != null) mainHandler.post(onDone);
            return;
        }
        if (isDownloading) return;
        isDownloading = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                BufferedInputStream bis = null;
                FileOutputStream fos = null;
                File tempFile = new File(target.getParentFile(), target.getName() + ".tmp");
                try {
                    int safe = Math.max(0, Math.min(MUADZIN_URLS.length - 1, muadzinIdx));
                    URL url = new URL(MUADZIN_URLS[safe]);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);
                    conn.setRequestProperty("User-Agent", "CrbLauncher/3.6 (Android TV)");

                    if (conn.getResponseCode() == 200) {
                        bis = new BufferedInputStream(conn.getInputStream());
                        fos = new FileOutputStream(tempFile);
                        byte[] buf = new byte[16384];
                        int r;
                        while ((r = bis.read(buf)) != -1) {
                            fos.write(buf, 0, r);
                        }
                        fos.flush();
                        fos.close();
                        fos = null;

                        if (tempFile.exists() && tempFile.length() > 50000) {
                            if (target.exists()) target.delete();
                            tempFile.renameTo(target);
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    isDownloading = false;
                    try { if (bis != null) bis.close(); } catch (Exception ignored) {}
                    try { if (fos != null) fos.close(); } catch (Exception ignored) {}
                    if (tempFile.exists()) tempFile.delete();
                    if (conn != null) conn.disconnect();
                    if (onDone != null) {
                        mainHandler.post(onDone);
                    }
                }
            }
        }, "MuadzinDownloader").start();
    }

    public File findAvailableCustomAdzanFile(Context context, String configuredPath) {
        return findAvailableCustomAdzanFile(context, configuredPath, 0);
    }

    public synchronized void playAdzan(
            final Context context,
            final String prayerName,
            final String customAdzanPath,
            final OnAdzanListener listener) {
        playAdzan(context, prayerName, customAdzanPath, 0, listener);
    }

    public File findAvailableCustomAdzanFile(Context context, String configuredPath, int muadzinIdx) {
        // 1. Jika pengguna memilih file MP3 Adzan kustom sendiri
        if (configuredPath != null && !configuredPath.trim().isEmpty()) {
            File f = new File(configuredPath.trim());
            if (f.exists() && f.isFile() && f.length() > 10000) {
                return f;
            }
        }
        // 2. Cek file adzan.mp3 di folder Download TV/HP
        try {
            File extRoot = Environment.getExternalStorageDirectory();
            if (extRoot != null) {
                String[] candidates = new String[]{
                        "Download/adzan.mp3",
                        "Download/Adzan.mp3",
                        "Music/adzan.mp3",
                        "adzan.mp3"
                };
                for (String rel : candidates) {
                    File f = new File(extRoot, rel);
                    if (f.exists() && f.isFile() && f.length() > 10000) {
                        return f;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        // 3. Cek rekaman Muadzin asli yang sudah terunduh di cache lokal
        File cachedMuadzin = getCachedMuadzinFile(context, muadzinIdx);
        if (cachedMuadzin.exists() && cachedMuadzin.length() > 50000) {
            return cachedMuadzin;
        }
        return null;
    }

    public synchronized void playAdzan(
            final Context context,
            final String prayerName,
            final String customAdzanPath,
            final int muadzinIdx,
            final OnAdzanListener listener) {
        stop();
        isPlaying = true;
        activeListener = listener;

        ensureTvAudioVolumeAudible(context);

        final boolean isSubuh = (prayerName != null && prayerName.toLowerCase(Locale.US).contains("subuh"));
        scheduleLafadzTimeline(prayerName, isSubuh, listener);

        // 1. Putar file MP3 kustom atau rekaman Muadzin asli dari cache lokal jika sudah ada
        File localMp3 = findAvailableCustomAdzanFile(context, customAdzanPath, muadzinIdx);
        if (localMp3 != null) {
            if (playLocalFileWithMediaPlayer(localMp3, listener)) {
                return;
            }
        }

        // 2. Jika belum ada di cache lokal, stream langsung MP3 Muadzin asli dari CDN sambil mengunduh ke cache!
        prefetchMuadzinAudioAsync(context, muadzinIdx, null);
        int safeIdx = Math.max(0, Math.min(MUADZIN_URLS.length - 1, muadzinIdx));
        String streamUrl = MUADZIN_URLS[safeIdx];
        if (playStreamUrlOrFallback(context, streamUrl, prayerName, listener)) {
            return;
        }

        // 3. Cadangan Offline 100% Jernih (R.raw.adzan_chime + Suara Vokal TTS Android)
        playCleanOfflineFallback(context, prayerName, listener);
    }

    private void ensureTvAudioVolumeAudible(Context context) {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.requestAudioFocus(
                        null,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                );
                int curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                if (curVol <= 1 && maxVol > 0) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(2, maxVol / 2), 0);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private boolean playLocalFileWithMediaPlayer(File audioFile, final OnAdzanListener listener) {
        FileInputStream fis = null;
        try {
            mediaPlayer = new MediaPlayer();
            configureMediaPlayerAudioAttributes(mediaPlayer);
            fis = new FileInputStream(audioFile);
            mediaPlayer.setDataSource(fis.getFD());
            mediaPlayer.setVolume(1.0f, 1.0f);
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    stop();
                    if (listener != null) {
                        listener.onAdzanFinished();
                    }
                }
            });
            mediaPlayer.prepare();
            mediaPlayer.start();
            return true;
        } catch (Exception e) {
            if (mediaPlayer != null) {
                try { mediaPlayer.release(); } catch (Exception ignored) {}
                mediaPlayer = null;
            }
            return false;
        } finally {
            if (fis != null) {
                try { fis.close(); } catch (Exception ignored) {}
            }
        }
    }

    private boolean playStreamUrlOrFallback(
            final Context context,
            final String url,
            final String prayerName,
            final OnAdzanListener listener) {
        try {
            mediaPlayer = new MediaPlayer();
            configureMediaPlayerAudioAttributes(mediaPlayer);
            mediaPlayer.setDataSource(url);
            mediaPlayer.setVolume(1.0f, 1.0f);
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    if (isPlaying) {
                        mp.start();
                    }
                }
            });
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    stop();
                    if (listener != null) {
                        listener.onAdzanFinished();
                    }
                }
            });
            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    playCleanOfflineFallback(context, prayerName, listener);
                    return true;
                }
            });
            mediaPlayer.prepareAsync();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void playCleanOfflineFallback(
            final Context context,
            final String prayerName,
            final OnAdzanListener listener) {
        try {
            if (mediaPlayer != null) {
                try { mediaPlayer.release(); } catch (Exception ignored) {}
            }
            mediaPlayer = new MediaPlayer();
            configureMediaPlayerAudioAttributes(mediaPlayer);
            int rawId = context.getResources().getIdentifier("adzan_chime", "raw", context.getPackageName());
            AssetFileDescriptor afd = (rawId != 0) ? context.getResources().openRawResourceFd(rawId) : null;
            if (afd != null) {
                mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
                mediaPlayer.setVolume(1.0f, 1.0f);
                mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        speakVocalAdzanWithTts(context, prayerName, listener);
                    }
                });
                mediaPlayer.prepare();
                mediaPlayer.start();
                return;
            }
        } catch (Exception ignored) {
        }
        speakVocalAdzanWithTts(context, prayerName, listener);
    }

    private void speakVocalAdzanWithTts(
            final Context context,
            final String prayerName,
            final OnAdzanListener listener) {
        if (!isPlaying) return;
        try {
            ttsEngine = new TextToSpeech(context.getApplicationContext(), new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (!isPlaying || ttsEngine == null) return;
                    if (status == TextToSpeech.SUCCESS) {
                        int res = ttsEngine.setLanguage(new Locale("id", "ID"));
                        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                            ttsEngine.setLanguage(Locale.US);
                        }
                        ttsEngine.setSpeechRate(0.85f);
                        String speech = "Allahu Akbar, Allahu Akbar. "
                                + "Asyhadu alla ilaha illallah. "
                                + "Asyhadu anna Muhammadar Rasulullah. "
                                + "Hayya alash sholah, Hayya alal falah. "
                                + "Allahu Akbar, Allahu Akbar, La ilaha illallah. "
                                + "Telah masuk waktu sholat " + prayerName + " untuk wilayah Kota Cirebon.";
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            ttsEngine.speak(speech, TextToSpeech.QUEUE_FLUSH, null, "adzan_tts");
                        } else {
                            ttsEngine.speak(speech, TextToSpeech.QUEUE_FLUSH, null);
                        }
                    }
                }
            });
        } catch (Exception ignored) {
        }
    }

    private void configureMediaPlayerAudioAttributes(MediaPlayer mp) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                    .build());
        } else {
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
        }
    }

    private void scheduleLafadzTimeline(final String prayerName, boolean isSubuh, final OnAdzanListener listener) {
        if (listener == null) return;

        postLafadzAt(0L,
                "Allahu Akbar, Allahu Akbar",
                "Allah Maha Besar, Allah Maha Besar");
        postLafadzAt(14000L,
                "Asyhadu alla ilaha illallah",
                "Aku bersaksi bahwa tiada Tuhan selain Allah");
        postLafadzAt(32000L,
                "Asyhadu anna Muhammadar Rasulullah",
                "Aku bersaksi bahwa Nabi Muhammad adalah utusan Allah");
        postLafadzAt(52000L,
                "Hayya 'alash sholah",
                "Marilah mendirikan sholat (" + prayerName + ")");
        postLafadzAt(70000L,
                "Hayya 'alal falah",
                "Marilah meraih kemenangan");

        long nextMs = 88000L;
        if (isSubuh) {
            postLafadzAt(nextMs,
                    "Ash-sholatu khoirum minan naum",
                    "Sholat itu lebih baik daripada tidur");
            nextMs += 16000L;
        }
        postLafadzAt(nextMs,
                "Allahu Akbar, Allahu Akbar - La ilaha illallah",
                "Allah Maha Besar - Tiada Tuhan selain Allah");
    }

    private void postLafadzAt(long delayMs, final String arab, final String meaning) {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isPlaying && activeListener != null) {
                    activeListener.onLafadzChanged(arab, meaning);
                }
            }
        }, delayMs);
    }
}
