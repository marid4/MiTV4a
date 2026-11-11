package com.crb.tv;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity Utama Crb Launcher (com.crb.tv):
 * - Menu Grid Kiri berisi App Favorit & berada di bawah Jam (tidak menutupi jam)
 * - Card Jadwal Sepak Bola (real dari livescore.mobi: Live, Hasil FT & Jadwal) di ATAS layar
 * - Card Jadwal Sholat Kota Cirebon + Suara Adzan Muadzin Jernih di ATAS layar
 * - Cuaca Real-Time wilayah Kelurahan Karya Mulya, Kec. Kesambi, Kota Cirebon
 * - Rolling Background (.jpg, .png, .gif, .mp4), Running Text (Edit di Setting), Screen Saver,
 *   Auto-Start saat Power On, serta Kotak Dialog Transparan dengan teks tajam.
 */
public final class MainActivity extends Activity implements AppAdapter.OnAppInteractionListener {

    private static final int REQUEST_PICK_BACKGROUND_MEDIA = 9001;
    private static final int REQUEST_PICK_ADZAN_AUDIO = 9002;

    // Zona Kursor Remote TV (State-Driven Cursor Machine)
    private static final int ZONE_GRID = 0;
    private static final int ZONE_WIDGETS = 1;
    private static final int ZONE_TOP_BAR = 2;

    private Config config;
    private AppAdapter appAdapter;
    private GridLayoutManager gridLayoutManager;
    private FootballScheduleManager footballManager;
    private WeatherManager weatherManager;
    private AdzanAudioPlayer adzanPlayer;
    private PrayerTimesCalculator.DailyPrayerSchedule todayPrayerTimes;
    private WallpaperTransferServer transferServer;

    // Views
    private ImageView skyView;
    private ImageView customBgImage;
    private AnimatedGifView customBgGif;
    private VideoView customBgVideo;
    private ImageView ambientView;
    private View scrimView;
    private GlassView dockGlassView;
    private LinearLayout topBar;
    private LinearLayout topLeftClockBox;
    private TextView greetingText;
    private TextView clockText;
    private TextView dateText;
    private TextView weatherKaryaMulyaText;
    private TextView statsText;
    private TextView prayerNextPill;
    private TextView footballPill;
    private TextView wallpaperPill;
    private TextView inputsPill;
    private TextView networkPill;
    private TextView settingsPill;

    private LinearLayout footballCard;
    private TextView footballLeagueTitle;
    private TextView footballStatusBadge;
    private TextView footballMatchTeams;
    private TextView footballMatchTime;

    private LinearLayout prayerCard;
    private TextView prayerHeaderCity;
    private TextView prayerCountdownBadge;
    private TextView prayImsak;
    private TextView praySubuh;
    private TextView prayDzuhur;
    private TextView prayAshar;
    private TextView prayMaghrib;
    private TextView prayIsya;

    private FrameLayout leftSidebarPanel;
    private RecyclerView appGrid;
    private LinearLayout selectedAppBannerBox;
    private TextView dockFocusTitle;
    private TextView dockFocusHint;

    private LinearLayout runningTextContainer;
    private TextView runningTextBadge;
    private RunningTextView runningTextView;

    private FrameLayout screensaverOverlay;
    private LinearLayout screensaverFloatingBox;
    private TextView screensaverClock;
    private TextView screensaverDate;
    private TextView screensaverInfo;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();

    private boolean isReorderMode = false;
    private int currentAmbientColor = 0xFF38BDF8;
    private int lastCalculatedDayOfYear = -1;
    private String activeVideoPath = "";
    private String lastTriggeredAdzanKey = "";
    private AlertDialog activeAdzanDialog;
    private int rollingBgIndex = 0;
    private boolean isScreensaverActive = false;
    private int screensaverDriftStep = 0;

    // State Kursor Remote TV
    private int currentZone = ZONE_GRID;
    private int gridCursor = 0;
    private int widgetCursor = 0;
    private int topBarCursor = 0;
    private final List<TextView> activeTopBarPills = new ArrayList<>();

    private final Runnable clockTickRunnable = new Runnable() {
        @Override
        public void run() {
            updateClockAndPrayerCountdown();
            mainHandler.postDelayed(this, 1000L);
        }
    };

    private final Runnable footballRotateRunnable = new Runnable() {
        @Override
        public void run() {
            rotateFootballMatchCard();
            long delayMs = Math.max(4, config != null ? config.footballRotateSeconds : 7) * 1000L;
            mainHandler.postDelayed(this, delayMs);
        }
    };

    private final Runnable skyRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            renderSkyBackground();
            syncWeatherKaryaMulyaAsync(false);
            mainHandler.postDelayed(this, 15 * 60 * 1000L);
        }
    };

    private final Runnable rollingBgRunnable = new Runnable() {
        @Override
        public void run() {
            if (config != null && config.rollingBackgroundEnabled) {
                advanceRollingBackground(false);
                long delayMs = Math.max(5, config.rollingBackgroundIntervalSec) * 1000L;
                mainHandler.postDelayed(this, delayMs);
            }
        }
    };

    private final Runnable idleScreensaverRunnable = new Runnable() {
        @Override
        public void run() {
            if (config != null && config.screensaverEnabled && !isScreensaverActive) {
                activateInAppScreensaver();
            }
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (appAdapter != null) {
                appAdapter.reloadApps();
                clampGridCursor();
                renderCursorHighlights();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        setContentView(R.layout.activity_main);

        config = Config.load(this);
        footballManager = new FootballScheduleManager();
        footballManager.setFilterSlug(config.footballFilterSlug);
        weatherManager = new WeatherManager();
        adzanPlayer = new AdzanAudioPlayer();

        bindViews();
        setupSidebarLayoutAndStyle();
        adjustTopBarForScreenSize();
        setupClickListeners();
        setupRecyclerView();
        rebuildActiveTopBarList();

        recalculateCirebonPrayerTimes(true);
        applyCustomBackgroundMedia();
        renderSkyBackground();
        updateClockAndPrayerCountdown();
        updateWeatherKaryaMulyaDisplay();
        updateRunningTextBar();
        rotateFootballMatchCard();
        updateNetworkStatusPill();

        // Pre-download rekaman suara Muadzin asli di background agar siap berbunyi jernih
        adzanPlayer.prefetchMuadzinAudioAsync(this, config.adzanMuadzinIndex, null);

        if (config.footballOnlineSync) {
            syncFootballMatchesAsync(false);
        }
        syncWeatherKaryaMulyaAsync(false);

        IntentFilter pkgFilter = new IntentFilter();
        pkgFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkgFilter.addDataScheme("package");
        registerReceiver(packageReceiver, pkgFilter);

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                currentZone = ZONE_GRID;
                gridCursor = 0;
                clampGridCursor();
                renderCursorHighlights();
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        adjustTopBarForScreenSize();
    }

    private void bindViews() {
        skyView = findViewById(R.id.sky_view);
        customBgImage = findViewById(R.id.custom_bg_image);
        customBgGif = findViewById(R.id.custom_bg_gif);
        customBgVideo = findViewById(R.id.custom_bg_video);
        ambientView = findViewById(R.id.ambient_view);
        scrimView = findViewById(R.id.scrim_view);
        dockGlassView = findViewById(R.id.dock_glass_view);

        topBar = findViewById(R.id.top_bar);
        topLeftClockBox = findOptionalViewByName("top_left_clock_box");
        greetingText = findViewById(R.id.greeting_text);
        if (topLeftClockBox == null && greetingText != null && greetingText.getParent() instanceof LinearLayout) {
            topLeftClockBox = (LinearLayout) greetingText.getParent();
        }
        clockText = findViewById(R.id.clock_text);
        dateText = findViewById(R.id.date_text);
        weatherKaryaMulyaText = findViewById(R.id.weather_karya_mulya_text);
        statsText = findViewById(R.id.stats_text);
        prayerNextPill = findViewById(R.id.prayer_next_pill);
        footballPill = findViewById(R.id.football_pill);
        wallpaperPill = findViewById(R.id.wallpaper_pill);
        inputsPill = findViewById(R.id.inputs_pill);
        networkPill = findViewById(R.id.network_pill);
        settingsPill = findViewById(R.id.settings_pill);

        footballCard = findViewById(R.id.football_card);
        footballLeagueTitle = findViewById(R.id.football_league_title);
        footballStatusBadge = findViewById(R.id.football_status_badge);
        footballMatchTeams = findViewById(R.id.football_match_teams);
        footballMatchTime = findViewById(R.id.football_match_time);

        prayerCard = findViewById(R.id.prayer_card);
        prayerHeaderCity = findViewById(R.id.prayer_header_city);
        prayerCountdownBadge = findViewById(R.id.prayer_countdown_badge);
        prayImsak = findViewById(R.id.pray_imsak);
        praySubuh = findViewById(R.id.pray_subuh);
        prayDzuhur = findViewById(R.id.pray_dzuhur);
        prayAshar = findViewById(R.id.pray_ashar);
        prayMaghrib = findViewById(R.id.pray_maghrib);
        prayIsya = findViewById(R.id.pray_isya);

        leftSidebarPanel = findViewById(R.id.left_sidebar_panel);
        appGrid = findViewById(R.id.app_grid);
        selectedAppBannerBox = findViewById(R.id.selected_app_banner_box);
        dockFocusTitle = findViewById(R.id.dock_focus_title);
        dockFocusHint = findViewById(R.id.dock_focus_hint);

        runningTextContainer = findOptionalViewByName("running_text_container");
        runningTextBadge = findOptionalViewByName("running_text_badge");
        runningTextView = findOptionalViewByName("running_text_view");
        if (runningTextContainer == null || runningTextView == null) {
            createDynamicRunningTextBar();
        }

        screensaverOverlay = findOptionalViewByName("screensaver_overlay");
        screensaverFloatingBox = findOptionalViewByName("screensaver_floating_box");
        screensaverClock = findOptionalViewByName("screensaver_clock");
        screensaverDate = findOptionalViewByName("screensaver_date");
        screensaverInfo = findOptionalViewByName("screensaver_info");
        if (screensaverOverlay == null || screensaverClock == null) {
            createDynamicScreensaverOverlay();
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends View> T findOptionalViewByName(String idName) {
        int resId = getResources().getIdentifier(idName, "id", getPackageName());
        if (resId == 0) return null;
        return (T) findViewById(resId);
    }

    private void createDynamicRunningTextBar() {
        ViewGroup contentContainer = findViewById(R.id.content_container);
        if (contentContainer == null) return;
        float density = getResources().getDisplayMetrics().density;

        runningTextContainer = new LinearLayout(this);
        runningTextContainer.setOrientation(LinearLayout.HORIZONTAL);
        runningTextContainer.setGravity(Gravity.CENTER_VERTICAL);
        runningTextContainer.setPadding((int) (8 * density), 0, (int) (8 * density), 0);
        runningTextContainer.setClickable(true);
        LinearLayout.LayoutParams lpBar = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (26 * density)
        );
        lpBar.topMargin = (int) (5 * density);
        runningTextContainer.setLayoutParams(lpBar);

        runningTextBadge = new TextView(this);
        runningTextBadge.setText("INFO CRB");
        runningTextBadge.setTextColor(0xFFFFFFFF);
        runningTextBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f);
        runningTextBadge.setTypeface(Typeface.DEFAULT_BOLD);
        runningTextBadge.setSingleLine(true);
        runningTextBadge.setPadding((int) (6 * density), (int) (1 * density), (int) (6 * density), (int) (1 * density));
        LinearLayout.LayoutParams lpBadge = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lpBadge.rightMargin = (int) (8 * density);
        runningTextBadge.setLayoutParams(lpBadge);
        runningTextContainer.addView(runningTextBadge);

        runningTextView = new RunningTextView(this);
        LinearLayout.LayoutParams lpRt = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1.0f
        );
        runningTextView.setLayoutParams(lpRt);
        runningTextContainer.addView(runningTextView);

        contentContainer.addView(runningTextContainer);
    }

    private void createDynamicScreensaverOverlay() {
        ViewGroup root = findViewById(R.id.root);
        if (root == null) return;
        float density = getResources().getDisplayMetrics().density;

        screensaverOverlay = new FrameLayout(this);
        screensaverOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        screensaverOverlay.setBackgroundColor(0x44000000);
        screensaverOverlay.setVisibility(View.GONE);
        screensaverOverlay.setClickable(true);
        screensaverOverlay.setFocusable(true);

        screensaverFloatingBox = new LinearLayout(this);
        screensaverFloatingBox.setOrientation(LinearLayout.VERTICAL);
        screensaverFloatingBox.setGravity(Gravity.CENTER);
        screensaverFloatingBox.setPadding(
                (int) (28 * density), (int) (20 * density),
                (int) (28 * density), (int) (20 * density)
        );
        FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        screensaverFloatingBox.setLayoutParams(boxLp);

        screensaverClock = new TextView(this);
        screensaverClock.setText("09:45:00");
        screensaverClock.setTextColor(0xFFFFFFFF);
        screensaverClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 52f);
        screensaverClock.setShadowLayer(8f, 0f, 3f, 0xEE000000);
        screensaverFloatingBox.addView(screensaverClock);

        screensaverDate = new TextView(this);
        screensaverDate.setText("Kota Cirebon");
        screensaverDate.setTextColor(0xFFFDE047);
        screensaverDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        screensaverDate.setTypeface(Typeface.DEFAULT_BOLD);
        screensaverDate.setShadowLayer(5f, 0f, 2f, 0xEE000000);
        screensaverDate.setPadding(0, (int) (4 * density), 0, 0);
        screensaverFloatingBox.addView(screensaverDate);

        screensaverInfo = new TextView(this);
        screensaverInfo.setText("Jadwal Sholat & Cuaca Karya Mulya Kota Cirebon");
        screensaverInfo.setTextColor(0xFF7DD3FC);
        screensaverInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        screensaverInfo.setShadowLayer(4f, 0f, 1.5f, 0xEE000000);
        screensaverInfo.setPadding(0, (int) (6 * density), 0, 0);
        screensaverFloatingBox.addView(screensaverInfo);

        TextView ssHint = new TextView(this);
        ssHint.setText("Crb Launcher Screen Saver - Tekan tombol apa saja untuk kembali");
        ssHint.setTextColor(0xAAFFFFFF);
        ssHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        ssHint.setShadowLayer(3f, 0f, 1f, 0xEE000000);
        ssHint.setPadding(0, (int) (10 * density), 0, 0);
        screensaverFloatingBox.addView(ssHint);

        screensaverOverlay.addView(screensaverFloatingBox);
        root.addView(screensaverOverlay);
    }

    /**
     * Menyesuaikan susunan Top Bar secara otomatis:
     * - Pada TV / Landscape (lebar >= 560dp): 1 baris horizontal sejajar.
     * - Pada HP Portrait (lebar < 560dp): disusun vertikal rapi agar kartu tidak tergencet.
     */
    private void adjustTopBarForScreenSize() {
        if (topBar == null || topLeftClockBox == null || footballCard == null || prayerCard == null) return;
        float density = getResources().getDisplayMetrics().density;
        int screenWidthDp = getResources().getConfiguration().screenWidthDp;

        if (screenWidthDp > 0 && screenWidthDp < 560) {
            topBar.setOrientation(LinearLayout.VERTICAL);

            LinearLayout.LayoutParams lpClock = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            topLeftClockBox.setLayoutParams(lpClock);

            LinearLayout.LayoutParams lpFb = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpFb.topMargin = (int) (4 * density);
            footballCard.setLayoutParams(lpFb);

            LinearLayout.LayoutParams lpPr = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpPr.topMargin = (int) (4 * density);
            prayerCard.setLayoutParams(lpPr);
        } else {
            topBar.setOrientation(LinearLayout.HORIZONTAL);

            LinearLayout.LayoutParams lpClock = new LinearLayout.LayoutParams(
                    (int) (250 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
            topLeftClockBox.setLayoutParams(lpClock);

            LinearLayout.LayoutParams lpFb = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.96f);
            lpFb.leftMargin = (int) (8 * density);
            lpFb.rightMargin = (int) (8 * density);
            footballCard.setLayoutParams(lpFb);

            LinearLayout.LayoutParams lpPr = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.22f);
            prayerCard.setLayoutParams(lpPr);
        }
    }

    /**
     * Helper untuk menampilkan AlertDialog dengan latar belakang Kaca Transparan
     * namun teks tetap sangat jelas dan tajam (dilengkapi bayangan teks kontras).
     */
    private AlertDialog showTranslucentDialog(AlertDialog.Builder builder) {
        final AlertDialog dialog = builder.create();
        dialog.show();
        applyTranslucentStyleToDialog(dialog);
        return dialog;
    }

    private void applyTranslucentStyleToDialog(final AlertDialog dialog) {
        if (dialog == null) return;
        try {
            Window window = dialog.getWindow();
            if (window != null) {
                float density = getResources().getDisplayMetrics().density;
                GradientDrawable glassBg = new GradientDrawable();
                glassBg.setCornerRadius(18f * density);
                // Latar kaca gelap semi-transparan (~50% alpha) agar wallpaper di belakang tetap terlihat jelas
                glassBg.setColor(0x8207111F);
                glassBg.setStroke((int) (1.6f * density), 0xAA38BDF8);
                window.setBackgroundDrawable(glassBg);
                window.setDimAmount(0.18f);

                final View decor = window.getDecorView();
                if (decor != null) {
                    styleDialogViewsForReadability(decor);
                    decor.post(new Runnable() {
                        @Override
                        public void run() {
                            styleDialogViewsForReadability(decor);
                        }
                    });
                }
            }
            ListView lv = dialog.getListView();
            if (lv != null) {
                lv.setBackgroundColor(Color.TRANSPARENT);
                lv.setCacheColorHint(Color.TRANSPARENT);
                lv.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                    @Override
                    public void onChildViewAdded(View parent, View child) {
                        styleDialogViewsForReadability(child);
                    }

                    @Override
                    public void onChildViewRemoved(View parent, View child) {
                    }
                });
            }
        } catch (Exception ignored) {
        }
    }

    private void styleDialogViewsForReadability(View view) {
        if (view == null) return;
        if (view instanceof EditText) {
            EditText et = (EditText) view;
            et.setTextColor(0xFFFFFFFF);
            et.setHintTextColor(0x99FFFFFF);
            et.setShadowLayer(3f, 0f, 1f, 0xEE000000);
            float density = getResources().getDisplayMetrics().density;
            GradientDrawable inputBg = new GradientDrawable();
            inputBg.setCornerRadius(10f * density);
            inputBg.setColor(0x660F172A);
            inputBg.setStroke((int) (1.2f * density), 0xAA38BDF8);
            et.setBackground(inputBg);
            et.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));
            return;
        }
        if (view instanceof Button) {
            Button btn = (Button) view;
            btn.setTextColor(0xFF38BDF8);
            btn.setShadowLayer(4f, 0f, 1.5f, 0xFF000000);
            return;
        }
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            int curColor = tv.getCurrentTextColor();
            int r = Color.red(curColor);
            int g = Color.green(curColor);
            int b = Color.blue(curColor);
            if (r < 170 && g < 170 && b < 170) {
                tv.setTextColor(0xFFFFFFFF);
            }
            tv.setShadowLayer(4.5f, 0f, 1.5f, 0xEE000000);
        }
        if (view instanceof ViewGroup) {
            int id = view.getId();
            if (id != View.NO_ID) {
                try {
                    String resName = getResources().getResourceEntryName(id);
                    if (resName != null && (resName.contains("Panel") || resName.contains("body") || resName.contains("template"))) {
                        view.setBackgroundColor(Color.TRANSPARENT);
                    }
                } catch (Exception ignored) {
                }
            }
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                styleDialogViewsForReadability(vg.getChildAt(i));
            }
        }
    }

    private void setupSidebarLayoutAndStyle() {
        float density = getResources().getDisplayMetrics().density;

        int cols = Math.max(1, Math.min(2, config.columns));
        int sidebarWidthDp = (cols == 1) ? 74 : 138;
        if (leftSidebarPanel != null) {
            ViewGroup.LayoutParams lp = leftSidebarPanel.getLayoutParams();
            if (lp != null) {
                lp.width = (int) (sidebarWidthDp * density);
                leftSidebarPanel.setLayoutParams(lp);
            }
            leftSidebarPanel.setClipChildren(true);
            leftSidebarPanel.setClipToPadding(true);
        }

        if (dockGlassView != null) {
            dockGlassView.setVisibility(config.dockGlass ? View.VISIBLE : View.GONE);
            dockGlassView.setAlpha(Math.min(1.0f, config.dockOpacity / 100.0f));
        }

        if (selectedAppBannerBox != null) {
            GradientDrawable bannerBg = new GradientDrawable();
            bannerBg.setCornerRadius(10f * density);
            bannerBg.setColor(0x22080D1A);
            selectedAppBannerBox.setBackground(bannerBg);
        }

        if (weatherKaryaMulyaText != null) {
            GradientDrawable weatherBg = new GradientDrawable();
            weatherBg.setCornerRadius(8f * density);
            weatherBg.setColor(0x360B192C);
            weatherBg.setStroke((int) (1f * density), 0x4438BDF8);
            weatherKaryaMulyaText.setBackground(weatherBg);
            weatherKaryaMulyaText.setVisibility(config.showWeatherKaryaMulya ? View.VISIBLE : View.GONE);
        }

        if (runningTextContainer != null) {
            GradientDrawable rtBg = new GradientDrawable();
            rtBg.setCornerRadius(8f * density);
            rtBg.setColor(0x3607111E);
            rtBg.setStroke((int) (1f * density), 0x4438BDF8);
            runningTextContainer.setBackground(rtBg);
            runningTextContainer.setVisibility(config.showRunningText ? View.VISIBLE : View.GONE);
        }

        if (runningTextBadge != null) {
            GradientDrawable rtBadgeBg = new GradientDrawable();
            rtBadgeBg.setCornerRadius(5f * density);
            rtBadgeBg.setColor(0x770284C7);
            runningTextBadge.setBackground(rtBadgeBg);
        }

        if (screensaverFloatingBox != null) {
            GradientDrawable ssBoxBg = new GradientDrawable();
            ssBoxBg.setCornerRadius(20f * density);
            ssBoxBg.setColor(0x6607111E);
            ssBoxBg.setStroke((int) (1.5f * density), 0x7738BDF8);
            screensaverFloatingBox.setBackground(ssBoxBg);
        }

        applyScrimDimming();

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(6f * density);
        badgeBg.setColor(0xAAEF4444);
        footballStatusBadge.setBackground(badgeBg);

        greetingText.setVisibility(config.showGreeting ? View.VISIBLE : View.GONE);
        statsText.setVisibility(config.showStats ? View.VISIBLE : View.GONE);
        inputsPill.setVisibility(config.showInputsPill ? View.VISIBLE : View.GONE);
        networkPill.setVisibility(config.showNetworkPill ? View.VISIBLE : View.GONE);
        prayerNextPill.setVisibility(config.showPrayerTimes ? View.VISIBLE : View.GONE);
        footballPill.setVisibility(config.showFootballSchedule ? View.VISIBLE : View.GONE);

        prayerCard.setVisibility(config.showPrayerTimes ? View.VISIBLE : View.GONE);
        footballCard.setVisibility(config.showFootballSchedule ? View.VISIBLE : View.GONE);

        rebuildActiveTopBarList();
        renderCursorHighlights();
    }

    private void rebuildActiveTopBarList() {
        activeTopBarPills.clear();
        if (prayerNextPill.getVisibility() == View.VISIBLE) activeTopBarPills.add(prayerNextPill);
        if (footballPill.getVisibility() == View.VISIBLE) activeTopBarPills.add(footballPill);
        if (wallpaperPill.getVisibility() == View.VISIBLE) activeTopBarPills.add(wallpaperPill);
        if (inputsPill.getVisibility() == View.VISIBLE) activeTopBarPills.add(inputsPill);
        if (networkPill.getVisibility() == View.VISIBLE) activeTopBarPills.add(networkPill);
        if (settingsPill.getVisibility() == View.VISIBLE) activeTopBarPills.add(settingsPill);
        if (topBarCursor >= activeTopBarPills.size()) {
            topBarCursor = Math.max(0, activeTopBarPills.size() - 1);
        }
    }

    private void applyScrimDimming() {
        int alpha = (int) (Math.max(0, Math.min(80, config.wallpaperDimPercent)) * 2.55f);
        scrimView.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
    }

    // =========================================================================
    // RUNNING TEXT (TEKS BERJALAN) & EDIT TEXT DI SETTING
    // =========================================================================

    private void updateRunningTextBar() {
        if (runningTextContainer == null || runningTextView == null) return;
        runningTextContainer.setVisibility(config.showRunningText ? View.VISIBLE : View.GONE);
        runningTextView.setSpeedLevel(config.runningTextSpeed);
        runningTextView.setRunning(config.showRunningText);

        StringBuilder fullText = new StringBuilder();
        if (config.runningTextContent != null && !config.runningTextContent.trim().isEmpty()) {
            fullText.append(config.runningTextContent.trim());
        } else {
            fullText.append("Selamat Datang di Crb Launcher Kota Cirebon");
        }

        if (config.runningTextIncludePrayerWeather) {
            if (todayPrayerTimes != null) {
                fullText.append("   |   Jadwal Sholat Cirebon Hari Ini: Imsak ")
                        .append(todayPrayerTimes.getImsak())
                        .append(", Subuh ").append(todayPrayerTimes.getSubuh())
                        .append(", Dzuhur ").append(todayPrayerTimes.getDzuhur())
                        .append(", Ashar ").append(todayPrayerTimes.getAshar())
                        .append(", Maghrib ").append(todayPrayerTimes.getMaghrib())
                        .append(", Isya ").append(todayPrayerTimes.getIsya()).append(" WIB");
            }
            if (weatherManager != null) {
                WeatherManager.WeatherInfo w = weatherManager.getCurrentWeather();
                if (w != null) {
                    fullText.append("   |   Cuaca ").append(w.toCompactSummary());
                }
            }
        }
        runningTextView.setRunningText(fullText.toString());
    }

    private void showEditRunningTextDialog() {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding((int) (24 * density), (int) (18 * density), (int) (24 * density), (int) (12 * density));
        container.setBackgroundColor(Color.TRANSPARENT);

        TextView subtitle = new TextView(this);
        subtitle.setText("Ketik pesan Teks Berjalan (Running Text) yang akan tampil di bagian bawah layar utama:");
        subtitle.setTextColor(0xFF7DD3FC);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        subtitle.setPadding(0, 0, 0, (int) (10 * density));
        container.addView(subtitle);

        final EditText input = new EditText(this);
        input.setText(config.runningTextContent);
        input.setSelection(input.getText().length());
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setMaxLines(4);
        container.addView(input);

        String speedName = (config.runningTextSpeed == 1) ? "Lambat" : (config.runningTextSpeed == 3 ? "Cepat" : "Normal");
        TextView statusInfo = new TextView(this);
        statusInfo.setText("\nStatus: " + (config.showRunningText ? "TAMPIL" : "SEMBUNYI")
                + "  |  Kecepatan: " + speedName
                + "  |  Info Sholat & Cuaca Otomatis: " + (config.runningTextIncludePrayerWeather ? "ON" : "OFF"));
        statusInfo.setTextColor(0xFFFDE047);
        statusInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        container.addView(statusInfo);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Edit Running Text (Teks Berjalan)")
                .setView(container)
                .setPositiveButton("Simpan Teks", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String newText = input.getText() != null ? input.getText().toString().trim() : "";
                        if (!newText.isEmpty()) {
                            config.runningTextContent = newText;
                            Config.saveUserStringPref(MainActivity.this, "pref_running_text_content", config.runningTextContent);
                        }
                        config.showRunningText = true;
                        Config.saveUserPref(MainActivity.this, "pref_show_running_text", true);
                        updateRunningTextBar();
                        Toast.makeText(MainActivity.this, "Running Text berhasil disimpan!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Opsi & Kecepatan", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String newText = input.getText() != null ? input.getText().toString().trim() : "";
                        if (!newText.isEmpty()) {
                            config.runningTextContent = newText;
                            Config.saveUserStringPref(MainActivity.this, "pref_running_text_content", config.runningTextContent);
                        }
                        showRunningTextOptionsDialog();
                    }
                })
                .setNegativeButton("Batal", null);
        showTranslucentDialog(builder);
    }

    private void showRunningTextOptionsDialog() {
        String speedName = (config.runningTextSpeed == 1) ? "Lambat" : (config.runningTextSpeed == 3 ? "Cepat" : "Normal");
        String[] opts = new String[]{
                "1. Tampilkan Running Text di Layar Bawah: " + (config.showRunningText ? "AKTIF (ON)" : "SEMBUNYI (OFF)"),
                "2. Kecepatan Gerak Teks: " + speedName + " (Klik untuk Ubah)",
                "3. Sertakan Info Jadwal Sholat & Cuaca di Ujung Teks: " + (config.runningTextIncludePrayerWeather ? "ON" : "OFF"),
                "4. Kembali ke Edit Teks Manual"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Pengaturan Tampilan Running Text")
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            config.showRunningText = !config.showRunningText;
                            Config.saveUserPref(MainActivity.this, "pref_show_running_text", config.showRunningText);
                            updateRunningTextBar();
                        } else if (which == 1) {
                            config.runningTextSpeed = (config.runningTextSpeed % 3) + 1;
                            Config.saveUserIntPref(MainActivity.this, "pref_running_text_speed", config.runningTextSpeed);
                            updateRunningTextBar();
                            showRunningTextOptionsDialog();
                        } else if (which == 2) {
                            config.runningTextIncludePrayerWeather = !config.runningTextIncludePrayerWeather;
                            Config.saveUserPref(MainActivity.this, "pref_running_text_include_info", config.runningTextIncludePrayerWeather);
                            updateRunningTextBar();
                            showRunningTextOptionsDialog();
                        } else if (which == 3) {
                            showEditRunningTextDialog();
                        }
                    }
                })
                .setNegativeButton("Tutup", null);
        showTranslucentDialog(builder);
    }

    // =========================================================================
    // SCREEN SAVER (OTOMATIS SAAT IDLE & TOMBOL AKTIFKAN SEKARANG)
    // =========================================================================

    private void resetIdleScreensaverTimer() {
        mainHandler.removeCallbacks(idleScreensaverRunnable);
        if (config != null && config.screensaverEnabled && !isScreensaverActive) {
            long timeoutMs = Math.max(15, config.screensaverTimeoutSec) * 1000L;
            mainHandler.postDelayed(idleScreensaverRunnable, timeoutMs);
        }
    }

    private void activateInAppScreensaver() {
        if (screensaverOverlay == null) return;
        isScreensaverActive = true;
        if (config.screensaverStyle == 2) {
            screensaverOverlay.setBackgroundColor(0xEE000000);
        } else {
            screensaverOverlay.setBackgroundColor(0x38000000);
        }
        screensaverOverlay.setVisibility(View.VISIBLE);
        updateClockAndPrayerCountdown();
    }

    private void dismissInAppScreensaver() {
        if (!isScreensaverActive) return;
        isScreensaverActive = false;
        if (screensaverOverlay != null) {
            screensaverOverlay.setVisibility(View.GONE);
        }
        resetIdleScreensaverTimer();
    }

    private void showScreensaverSettingsDialog() {
        final String[] styleNames = new String[]{
                "Jam Digital + Jadwal Sholat + Cuaca di Atas Wallpaper",
                "Rolling Wallpaper Penuh + Jam Mengambang Transparan",
                "Layar Hitam Pekat OLED (Hemat Daya Layar TV)"
        };
        int curStyle = Math.max(0, Math.min(styleNames.length - 1, config.screensaverStyle));

        String[] items = new String[]{
                "1. Aktifkan / Tes Screen Saver Sekarang",
                "2. Screen Saver Otomatis Saat TV Idle: " + (config.screensaverEnabled ? "AKTIF (ON)" : "MATI (OFF)"),
                "3. Waktu Tunggu Screen Saver: " + (config.screensaverTimeoutSec / 60) + " Menit (" + config.screensaverTimeoutSec + " dtk)",
                "4. Gaya Tampilan Screen Saver: " + styleNames[curStyle],
                "5. Buka Pengaturan Screen Saver Sistem Android TV (Daydream)"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Pengaturan Screen Saver Crb Launcher")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            activateInAppScreensaver();
                        } else if (which == 1) {
                            config.screensaverEnabled = !config.screensaverEnabled;
                            Config.saveUserPref(MainActivity.this, "pref_screensaver_enabled", config.screensaverEnabled);
                            resetIdleScreensaverTimer();
                            Toast.makeText(
                                    MainActivity.this,
                                    "Screen Saver Otomatis: " + (config.screensaverEnabled ? "AKTIF" : "NONAKTIF"),
                                    Toast.LENGTH_SHORT
                            ).show();
                            showScreensaverSettingsDialog();
                        } else if (which == 2) {
                            showScreensaverTimeoutPickerDialog();
                        } else if (which == 3) {
                            config.screensaverStyle = (config.screensaverStyle + 1) % styleNames.length;
                            Config.saveUserIntPref(MainActivity.this, "pref_screensaver_style", config.screensaverStyle);
                            showScreensaverSettingsDialog();
                        } else if (which == 4) {
                            try {
                                startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            } catch (Exception e) {
                                openAndroidSettings();
                            }
                        }
                    }
                })
                .setNegativeButton("Tutup", null);
        showTranslucentDialog(builder);
    }

    private void showScreensaverTimeoutPickerDialog() {
        final String[] labels = new String[]{
                "30 Detik (Untuk Tes Cepat)",
                "1 Menit",
                "3 Menit",
                "5 Menit (Rekomendasi)",
                "10 Menit",
                "15 Menit",
                "30 Menit"
        };
        final int[] secs = new int[]{30, 60, 180, 300, 600, 900, 1800};

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Pilih Waktu Tunggu Screen Saver")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        config.screensaverTimeoutSec = secs[which];
                        Config.saveUserIntPref(MainActivity.this, "pref_screensaver_timeout_sec", config.screensaverTimeoutSec);
                        config.screensaverEnabled = true;
                        Config.saveUserPref(MainActivity.this, "pref_screensaver_enabled", true);
                        resetIdleScreensaverTimer();
                        Toast.makeText(
                                MainActivity.this,
                                "Screen Saver diatur ke: " + labels[which],
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                })
                .setNegativeButton("Batal", null);
        showTranslucentDialog(builder);
    }

    /**
     * Menerapkan file gambar/video Background (.jpg, .png, .gif, .mp4).
     */
    private void applyCustomBackgroundMedia() {
        String path = config.customWallpaperPath != null ? config.customWallpaperPath.trim() : "";
        if (path.isEmpty()) {
            stopCustomBackgroundMedia();
            skyView.setVisibility(View.VISIBLE);
            return;
        }

        File file = new File(path);
        if (!file.exists() || !file.isFile() || file.length() <= 0) {
            stopCustomBackgroundMedia();
            skyView.setVisibility(View.VISIBLE);
            return;
        }

        String lower = file.getName().toLowerCase(Locale.US);
        try {
            if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mkv")) {
                customBgImage.setVisibility(View.GONE);
                customBgGif.stopGif();
                customBgGif.setVisibility(View.GONE);
                skyView.setVisibility(View.GONE);

                customBgVideo.setVisibility(View.VISIBLE);
                if (!path.equals(activeVideoPath) || !customBgVideo.isPlaying()) {
                    activeVideoPath = path;
                    customBgVideo.setVideoPath(file.getAbsolutePath());
                    customBgVideo.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                            mp.setLooping(true);
                            mp.setVolume(0f, 0f);
                            customBgVideo.start();
                        }
                    });
                    customBgVideo.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                        @Override
                        public boolean onError(MediaPlayer mp, int what, int extra) {
                            stopCustomBackgroundMedia();
                            skyView.setVisibility(View.VISIBLE);
                            return true;
                        }
                    });
                }
            } else if (lower.endsWith(".gif")) {
                stopVideoOnly();
                customBgImage.setVisibility(View.GONE);
                if (customBgGif.setGifFile(file)) {
                    customBgGif.setVisibility(View.VISIBLE);
                    skyView.setVisibility(View.GONE);
                } else {
                    customBgGif.setVisibility(View.GONE);
                    Bitmap bmp = decodeSampledBitmap(file, 1920, 1080);
                    if (bmp != null) {
                        customBgImage.setImageBitmap(bmp);
                        customBgImage.setVisibility(View.VISIBLE);
                        skyView.setVisibility(View.GONE);
                    } else {
                        skyView.setVisibility(View.VISIBLE);
                    }
                }
            } else {
                stopVideoOnly();
                customBgGif.stopGif();
                customBgGif.setVisibility(View.GONE);
                Bitmap bmp = decodeSampledBitmap(file, 1920, 1080);
                if (bmp != null) {
                    customBgImage.setImageBitmap(bmp);
                    customBgImage.setVisibility(View.VISIBLE);
                    skyView.setVisibility(View.GONE);
                } else {
                    customBgImage.setVisibility(View.GONE);
                    skyView.setVisibility(View.VISIBLE);
                }
            }
        } catch (Exception e) {
            stopCustomBackgroundMedia();
            skyView.setVisibility(View.VISIBLE);
        }
    }

    private void stopVideoOnly() {
        activeVideoPath = "";
        try {
            if (customBgVideo != null) {
                if (customBgVideo.isPlaying()) {
                    customBgVideo.stopPlayback();
                }
                customBgVideo.setVisibility(View.GONE);
            }
        } catch (Exception ignored) {
        }
    }

    private void stopCustomBackgroundMedia() {
        stopVideoOnly();
        if (customBgGif != null) {
            customBgGif.stopGif();
            customBgGif.setVisibility(View.GONE);
        }
        if (customBgImage != null) {
            customBgImage.setImageDrawable(null);
            customBgImage.setVisibility(View.GONE);
        }
    }

    private Bitmap decodeSampledBitmap(File file, int reqWidth, int reqHeight) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);

            int height = options.outHeight;
            int width = options.outWidth;
            int inSampleSize = 1;
            if (height > reqHeight || width > reqWidth) {
                int halfHeight = height / 2;
                int halfWidth = width / 2;
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2;
                }
            }
            options.inJustDecodeBounds = false;
            options.inSampleSize = inSampleSize;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Exception e) {
            return null;
        }
    }

    private void renderCursorHighlights() {
        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < activeTopBarPills.size(); i++) {
            TextView pill = activeTopBarPills.get(i);
            boolean selected = (currentZone == ZONE_TOP_BAR && topBarCursor == i);
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(999f);
            if (selected) {
                gd.setColor(0x880284C7);
                gd.setStroke((int) (2f * density), 0xFFFFFFFF);
                pill.setScaleX(1.06f);
                pill.setScaleY(1.06f);
            } else {
                if (pill == prayerNextPill) {
                    gd.setColor(config.enableAdzanSound ? 0x48065F46 : 0x381E293B);
                    gd.setStroke((int) (1f * density), 0x8834D399);
                } else if (pill == footballPill) {
                    gd.setColor(0x481E3A8A);
                    gd.setStroke((int) (1f * density), 0x8860A5FA);
                } else if (pill == wallpaperPill) {
                    gd.setColor(0x4878350F);
                    gd.setStroke((int) (1f * density), 0x88FBBF24);
                } else {
                    gd.setColor(0x381E293B);
                    gd.setStroke((int) (1f * density), 0x44FFFFFF);
                }
                pill.setScaleX(1.0f);
                pill.setScaleY(1.0f);
            }
            pill.setBackground(gd);
        }

        boolean footballSelected = (currentZone == ZONE_WIDGETS && widgetCursor == 0);
        GradientDrawable fbBg = new GradientDrawable();
        fbBg.setCornerRadius(12f * density);
        fbBg.setColor(footballSelected ? 0x68172554 : 0x3D0B132B);
        fbBg.setStroke(
                (int) ((footballSelected ? 2.2f : 1.0f) * density),
                footballSelected ? 0xFFFDE047 : 0x5538BDF8
        );
        footballCard.setBackground(fbBg);
        footballCard.setScaleX(footballSelected ? 1.02f : 1.0f);
        footballCard.setScaleY(footballSelected ? 1.02f : 1.0f);

        boolean prayerSelected = (currentZone == ZONE_WIDGETS && widgetCursor == 1);
        GradientDrawable prBg = new GradientDrawable();
        prBg.setCornerRadius(12f * density);
        prBg.setColor(prayerSelected ? 0x68064E3B : 0x3D06281E);
        prBg.setStroke(
                (int) ((prayerSelected ? 2.2f : 1.0f) * density),
                prayerSelected ? 0xFFFDE047 : 0x5510B981
        );
        prayerCard.setBackground(prBg);
        prayerCard.setScaleX(prayerSelected ? 1.02f : 1.0f);
        prayerCard.setScaleY(prayerSelected ? 1.02f : 1.0f);

        if (appAdapter != null) {
            int targetPos = (currentZone == ZONE_GRID) ? gridCursor : -1;
            if (appAdapter.getSelectedPosition() != targetPos) {
                appAdapter.setSelectedPosition(targetPos);
            }
        }
    }

    private void setupClickListeners() {
        weatherKaryaMulyaText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showKaryaMulyaWeatherDialog();
            }
        });
        settingsPill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLauncherSettingsDialog();
            }
        });
        networkPill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openWifiSettings();
            }
        });
        inputsPill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showManageFavoritesDialog();
            }
        });
        wallpaperPill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBackgroundPickerDialog();
            }
        });
        footballPill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFootballScheduleDialog();
            }
        });
        prayerNextPill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCirebonPrayerDetailDialog();
            }
        });
        prayerCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentZone = ZONE_WIDGETS;
                widgetCursor = 1;
                renderCursorHighlights();
                showCirebonPrayerDetailDialog();
            }
        });
        footballCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentZone = ZONE_WIDGETS;
                widgetCursor = 0;
                renderCursorHighlights();
                showFootballScheduleDialog();
            }
        });
        if (runningTextContainer != null) {
            runningTextContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showEditRunningTextDialog();
                }
            });
        }
        if (screensaverOverlay != null) {
            screensaverOverlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismissInAppScreensaver();
                }
            });
        }
    }

    private void setupRecyclerView() {
        int cols = Math.max(1, Math.min(2, config.columns));
        gridLayoutManager = new GridLayoutManager(this, cols);
        appGrid.setLayoutManager(gridLayoutManager);
        appGrid.setItemAnimator(null);
        appGrid.setClipChildren(true);
        appGrid.setClipToPadding(true);

        appAdapter = new AppAdapter(this, config, this);
        appGrid.setAdapter(appAdapter);
        appAdapter.reloadApps();
    }

    private void clampGridCursor() {
        if (appAdapter == null) return;
        int count = appAdapter.getItemCount();
        if (count <= 0) {
            gridCursor = 0;
        } else if (gridCursor >= count) {
            gridCursor = count - 1;
        } else if (gridCursor < 0) {
            gridCursor = 0;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mainHandler.removeCallbacks(clockTickRunnable);
        mainHandler.post(clockTickRunnable);

        mainHandler.removeCallbacks(footballRotateRunnable);
        mainHandler.postDelayed(footballRotateRunnable, 6000L);

        mainHandler.removeCallbacks(skyRefreshRunnable);
        mainHandler.postDelayed(skyRefreshRunnable, 15 * 60 * 1000L);

        rescheduleRollingBackgroundTimer();
        resetIdleScreensaverTimer();

        updateNetworkStatusPill();
        if (config.showStats) {
            updateSystemStats();
        }
        applyCustomBackgroundMedia();
        updateRunningTextBar();
        renderCursorHighlights();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(clockTickRunnable);
        mainHandler.removeCallbacks(footballRotateRunnable);
        mainHandler.removeCallbacks(skyRefreshRunnable);
        mainHandler.removeCallbacks(rollingBgRunnable);
        mainHandler.removeCallbacks(idleScreensaverRunnable);
        if (customBgGif != null) {
            customBgGif.stopGif();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(packageReceiver);
        } catch (Exception ignored) {
        }
        if (transferServer != null) {
            transferServer.stop();
        }
        if (adzanPlayer != null) {
            adzanPlayer.stop();
        }
        stopCustomBackgroundMedia();
        bgExecutor.shutdownNow();
    }

    // =========================================================================
    // ENGINE REMOTE TV (STATE-DRIVEN D-PAD CURSOR NAVIGATION)
    // =========================================================================

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (isScreensaverActive && ev.getAction() == MotionEvent.ACTION_DOWN) {
            dismissInAppScreensaver();
            return true;
        }
        resetIdleScreensaverTimer();
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();

        if (isScreensaverActive) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                dismissInAppScreensaver();
            }
            return true;
        }
        resetIdleScreensaverTimer();

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (isCenterConfirmKey(keyCode)) {
                if (event.getRepeatCount() == 1 && currentZone == ZONE_GRID) {
                    triggerCurrentSelection(true);
                    return true;
                }
                return true;
            }
            if (isDirectionalKey(keyCode)) {
                return handleDirectionalNavigation(keyCode);
            }
            if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
                showLauncherSettingsDialog();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_PROG_RED || keyCode == KeyEvent.KEYCODE_F1) {
                showBackgroundPickerDialog();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_PROG_GREEN || keyCode == KeyEvent.KEYCODE_F2) {
                showCirebonPrayerDetailDialog();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_PROG_YELLOW || keyCode == KeyEvent.KEYCODE_F3) {
                showFootballScheduleDialog();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_PROG_BLUE || keyCode == KeyEvent.KEYCODE_F4) {
                showEditRunningTextDialog();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                if (adzanPlayer != null && adzanPlayer.isPlaying()) {
                    adzanPlayer.stop();
                    Toast.makeText(this, "Suara Adzan dimatikan", Toast.LENGTH_SHORT).show();
                    return true;
                }
                if (isReorderMode) {
                    isReorderMode = false;
                    dockFocusHint.setText("Urutan Favorit disimpan | OK: Buka | Tahan OK: Atur Favorit");
                    Toast.makeText(this, "Urutan App Favorit disimpan", Toast.LENGTH_SHORT).show();
                    return true;
                }
                if (currentZone != ZONE_GRID) {
                    currentZone = ZONE_GRID;
                    renderCursorHighlights();
                    return true;
                }
                return true;
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            if (isCenterConfirmKey(keyCode)) {
                if (event.isLongPress()) {
                    return true;
                }
                triggerCurrentSelection(false);
                return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    private boolean isDirectionalKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_CHANNEL_UP
                || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN;
    }

    private boolean isCenterConfirmKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_A
                || keyCode == KeyEvent.KEYCODE_SPACE;
    }

    private boolean handleDirectionalNavigation(int keyCode) {
        int cols = Math.max(1, Math.min(2, config.columns));
        int itemCount = appAdapter != null ? appAdapter.getItemCount() : 0;

        if (isReorderMode && currentZone == ZONE_GRID && itemCount > 1) {
            int targetPos = gridCursor;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && gridCursor > 0) {
                targetPos = gridCursor - 1;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && gridCursor < itemCount - 1) {
                targetPos = gridCursor + 1;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP && gridCursor - cols >= 0) {
                targetPos = gridCursor - cols;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && gridCursor + cols < itemCount) {
                targetPos = gridCursor + cols;
            }
            if (targetPos != gridCursor) {
                if (appAdapter.moveItem(gridCursor, targetPos)) {
                    gridCursor = targetPos;
                    appGrid.scrollToPosition(gridCursor);
                    renderCursorHighlights();
                }
            }
            return true;
        }

        if (currentZone == ZONE_GRID) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_CHANNEL_UP) {
                if (gridCursor - cols >= 0) {
                    gridCursor -= cols;
                    appGrid.scrollToPosition(gridCursor);
                } else {
                    if (!activeTopBarPills.isEmpty()) {
                        currentZone = ZONE_TOP_BAR;
                        topBarCursor = 0;
                    } else if (isAnyWidgetVisible()) {
                        currentZone = ZONE_WIDGETS;
                        widgetCursor = getFirstVisibleWidgetIndex();
                    }
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                if (gridCursor + cols < itemCount) {
                    gridCursor += cols;
                    appGrid.scrollToPosition(gridCursor);
                } else if (gridCursor < itemCount - 1) {
                    gridCursor = itemCount - 1;
                    appGrid.scrollToPosition(gridCursor);
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (gridCursor % cols > 0) {
                    gridCursor--;
                    appGrid.scrollToPosition(gridCursor);
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if ((gridCursor % cols) < (cols - 1) && (gridCursor + 1) < itemCount) {
                    gridCursor++;
                    appGrid.scrollToPosition(gridCursor);
                } else {
                    if (isAnyWidgetVisible()) {
                        currentZone = ZONE_WIDGETS;
                        widgetCursor = getFirstVisibleWidgetIndex();
                    } else if (!activeTopBarPills.isEmpty()) {
                        currentZone = ZONE_TOP_BAR;
                        topBarCursor = 0;
                    }
                }
            }
            renderCursorHighlights();
            return true;
        }

        if (currentZone == ZONE_WIDGETS) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (widgetCursor == 1 && footballCard.getVisibility() == View.VISIBLE) {
                    widgetCursor = 0;
                } else if (!activeTopBarPills.isEmpty()) {
                    currentZone = ZONE_TOP_BAR;
                    topBarCursor = activeTopBarPills.size() - 1;
                } else {
                    currentZone = ZONE_GRID;
                    clampGridCursor();
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (widgetCursor == 0 && prayerCard.getVisibility() == View.VISIBLE) {
                    widgetCursor = 1;
                } else if (widgetCursor == 0) {
                    rotateFootballMatchCard();
                    Toast.makeText(this, "Match LiveScore berikutnya", Toast.LENGTH_SHORT).show();
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                currentZone = ZONE_GRID;
                clampGridCursor();
                appGrid.scrollToPosition(gridCursor);
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_CHANNEL_UP) {
                if (!activeTopBarPills.isEmpty()) {
                    currentZone = ZONE_TOP_BAR;
                    topBarCursor = activeTopBarPills.size() - 1;
                }
            }
            renderCursorHighlights();
            return true;
        }

        if (currentZone == ZONE_TOP_BAR) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (topBarCursor > 0) {
                    topBarCursor--;
                } else {
                    currentZone = ZONE_GRID;
                    gridCursor = 0;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (topBarCursor < activeTopBarPills.size() - 1) {
                    topBarCursor++;
                } else if (isAnyWidgetVisible()) {
                    currentZone = ZONE_WIDGETS;
                    widgetCursor = getFirstVisibleWidgetIndex();
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                currentZone = ZONE_GRID;
                clampGridCursor();
                appGrid.scrollToPosition(gridCursor);
            }
            renderCursorHighlights();
            return true;
        }

        return true;
    }

    private boolean isAnyWidgetVisible() {
        return footballCard.getVisibility() == View.VISIBLE || prayerCard.getVisibility() == View.VISIBLE;
    }

    private int getFirstVisibleWidgetIndex() {
        if (footballCard.getVisibility() == View.VISIBLE) return 0;
        return 1;
    }

    private void triggerCurrentSelection(boolean isLongPress) {
        if (currentZone == ZONE_GRID) {
            List<AppAdapter.AppEntry> list = appAdapter.getItems();
            if (gridCursor >= 0 && gridCursor < list.size()) {
                AppAdapter.AppEntry entry = list.get(gridCursor);
                if (isLongPress) {
                    onAppLongClicked(entry, gridCursor);
                } else {
                    onAppClicked(entry, gridCursor);
                }
            }
        } else if (currentZone == ZONE_WIDGETS) {
            if (widgetCursor == 0 && footballCard.getVisibility() == View.VISIBLE) {
                showFootballScheduleDialog();
            } else if (prayerCard.getVisibility() == View.VISIBLE) {
                showCirebonPrayerDetailDialog();
            }
        } else if (currentZone == ZONE_TOP_BAR) {
            if (topBarCursor >= 0 && topBarCursor < activeTopBarPills.size()) {
                TextView pill = activeTopBarPills.get(topBarCursor);
                pill.performClick();
            }
        }
    }

    // =========================================================================
    // 1. LOGIKA JADWAL SHOLAT KOTA CIREBON + SUARA ADZAN MUADZIN JERNIH
    // =========================================================================

    private void recalculateCirebonPrayerTimes(boolean allowOnlineSync) {
        TimeZone wib = TimeZone.getTimeZone("GMT+7");
        final Calendar cal = Calendar.getInstance(wib);
        lastCalculatedDayOfYear = cal.get(Calendar.DAY_OF_YEAR);

        final int year = cal.get(Calendar.YEAR);
        final int month = cal.get(Calendar.MONTH) + 1;
        final int day = cal.get(Calendar.DAY_OF_MONTH);

        todayPrayerTimes = PrayerTimesCalculator.calculateKemenag(
                config.prayerCityName,
                year,
                month,
                day,
                config.prayerLatitude,
                config.prayerLongitude,
                config.prayerTimezoneOffset,
                config.prayerIhtiyatMinutes
        );
        renderPrayerTimesCard();
        updateRunningTextBar();

        if (allowOnlineSync && config.prayerOnlineSync) {
            bgExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    final PrayerTimesCalculator.DailyPrayerSchedule synced =
                            PrayerTimesCalculator.fetchOnlineOrFallback(
                                    config.prayerCityName,
                                    year,
                                    month,
                                    day,
                                    config.prayerLatitude,
                                    config.prayerLongitude,
                                    config.prayerTimezoneOffset,
                                    config.prayerIhtiyatMinutes
                            );
                    if (synced != null) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                todayPrayerTimes = synced;
                                renderPrayerTimesCard();
                                updateClockAndPrayerCountdown();
                                updateRunningTextBar();
                            }
                        });
                    }
                }
            });
        }
    }

    private void renderPrayerTimesCard() {
        if (todayPrayerTimes == null) return;
        String adzanTag = config.enableAdzanSound ? " (ADZAN ON)" : " (ADZAN OFF)";
        prayerHeaderCity.setText("SHOLAT CIREBON" + adzanTag);

        setPrayerBoxText(prayImsak, "Imsak", todayPrayerTimes.getImsak());
        setPrayerBoxText(praySubuh, "Subuh", todayPrayerTimes.getSubuh());
        setPrayerBoxText(prayDzuhur, "Dzuhur", todayPrayerTimes.getDzuhur());
        setPrayerBoxText(prayAshar, "Ashar", todayPrayerTimes.getAshar());
        setPrayerBoxText(prayMaghrib, "Maghrib", todayPrayerTimes.getMaghrib());
        setPrayerBoxText(prayIsya, "Isya", todayPrayerTimes.getIsya());
    }

    private void setPrayerBoxText(TextView view, String label, String timeStr) {
        view.setText(label + "\n" + timeStr);
    }

    private void highlightActivePrayerColumn(String nextPrayerName) {
        TextView[] all = new TextView[]{prayImsak, praySubuh, prayDzuhur, prayAshar, prayMaghrib, prayIsya};
        String[] names = new String[]{"Imsak", "Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya"};
        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < all.length; i++) {
            TextView tv = all[i];
            boolean isNext = names[i].equalsIgnoreCase(nextPrayerName);
            if (isNext) {
                GradientDrawable hl = new GradientDrawable();
                hl.setCornerRadius(6f * density);
                hl.setColor(0x3610B981);
                hl.setStroke((int) (1f * density), 0xFF34D399);
                tv.setBackground(hl);
                tv.setTextColor(0xFFA7F3D0);
                tv.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                tv.setBackground(null);
                tv.setTextColor(0xDDFFFFFF);
                tv.setTypeface(Typeface.DEFAULT);
            }
        }
    }

    private void updateClockAndPrayerCountdown() {
        TimeZone wib = TimeZone.getTimeZone("GMT+7");
        Calendar now = Calendar.getInstance(wib);

        int dayOfYear = now.get(Calendar.DAY_OF_YEAR);
        if (dayOfYear != lastCalculatedDayOfYear || todayPrayerTimes == null) {
            recalculateCirebonPrayerTimes(true);
        }

        String timePattern = "12".equals(config.clockFormat) ? "hh:mm" : "HH:mm";
        SimpleDateFormat clockFmt = new SimpleDateFormat(timePattern, Locale.getDefault());
        clockFmt.setTimeZone(wib);
        clockText.setText(clockFmt.format(now.getTime()));

        SimpleDateFormat dateFmt = new SimpleDateFormat("EEE, d MMM yyyy", new Locale("id", "ID"));
        dateFmt.setTimeZone(wib);
        String masehiStr = dateFmt.format(now.getTime());
        String hijriStr = PrayerTimesCalculator.getHijriDateString(
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH) + 1,
                now.get(Calendar.DAY_OF_MONTH)
        );
        dateText.setText(masehiStr + " | " + hijriStr);

        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);
        int second = now.get(Calendar.SECOND);

        String greetingPrefix;
        if (hour >= 4 && hour < 11) {
            greetingPrefix = "SELAMAT PAGI";
        } else if (hour >= 11 && hour < 15) {
            greetingPrefix = "SELAMAT SIANG";
        } else if (hour >= 15 && hour < 18) {
            greetingPrefix = "SELAMAT SORE";
        } else {
            greetingPrefix = "SELAMAT MALAM";
        }
        String suffix = (config.greetingName != null && !config.greetingName.trim().isEmpty())
                ? " | " + config.greetingName.toUpperCase(Locale.ROOT)
                : "";
        greetingText.setText(greetingPrefix + suffix);

        if (todayPrayerTimes != null) {
            PrayerTimesCalculator.NextPrayerInfo nextInfo =
                    PrayerTimesCalculator.getNextPrayer(todayPrayerTimes, hour, minute, second);
            prayerCountdownBadge.setText(nextInfo.formatCountdown());
            prayerNextPill.setText(nextInfo.name + " " + nextInfo.timeFormatted);
            highlightActivePrayerColumn(nextInfo.name);

            if (isScreensaverActive && screensaverOverlay != null) {
                SimpleDateFormat ssClockFmt = new SimpleDateFormat("HH:mm:ss", Locale.US);
                ssClockFmt.setTimeZone(wib);
                screensaverClock.setText(ssClockFmt.format(now.getTime()));
                screensaverDate.setText(masehiStr + "  |  " + hijriStr);
                String wText = (weatherManager != null && weatherManager.getCurrentWeather() != null)
                        ? weatherManager.getCurrentWeather().toCompactSummary()
                        : "Kota Cirebon";
                screensaverInfo.setText(nextInfo.formatCountdown() + " (" + nextInfo.timeFormatted + " WIB)   |   " + wText);

                if (second % 10 == 0 && screensaverFloatingBox != null) {
                    screensaverDriftStep = (screensaverDriftStep + 1) % 5;
                    float dx = (screensaverDriftStep - 2) * 18f;
                    float dy = ((screensaverDriftStep % 3) - 1) * 14f;
                    screensaverFloatingBox.animate().translationX(dx).translationY(dy).setDuration(900L).start();
                }
            }

            checkAndTriggerAutomaticAdzan(now, todayPrayerTimes);
        }
    }

    private void checkAndTriggerAutomaticAdzan(Calendar nowWib, PrayerTimesCalculator.DailyPrayerSchedule pt) {
        if (!config.enableAdzanSound || pt == null) return;
        int sec = nowWib.get(Calendar.SECOND);
        if (sec > 25) return;

        String hhmm = String.format(Locale.US, "%02d:%02d",
                nowWib.get(Calendar.HOUR_OF_DAY), nowWib.get(Calendar.MINUTE));
        String ymd = String.format(Locale.US, "%04d%02d%02d",
                nowWib.get(Calendar.YEAR), nowWib.get(Calendar.MONTH) + 1, nowWib.get(Calendar.DAY_OF_MONTH));

        String matchedPrayer = null;
        if (hhmm.equals(pt.getSubuh())) matchedPrayer = "Subuh";
        else if (hhmm.equals(pt.getDzuhur())) matchedPrayer = "Dzuhur";
        else if (hhmm.equals(pt.getAshar())) matchedPrayer = "Ashar";
        else if (hhmm.equals(pt.getMaghrib())) matchedPrayer = "Maghrib";
        else if (hhmm.equals(pt.getIsya())) matchedPrayer = "Isya";

        if (matchedPrayer != null) {
            String triggerKey = ymd + "_" + matchedPrayer + "_" + hhmm;
            if (!triggerKey.equals(lastTriggeredAdzanKey)) {
                lastTriggeredAdzanKey = triggerKey;
                triggerAdzanAlertAndSound(matchedPrayer, hhmm);
            }
        }
    }

    private void triggerAdzanAlertAndSound(final String prayerName, final String prayerTimeStr) {
        if (activeAdzanDialog != null && activeAdzanDialog.isShowing()) {
            try {
                activeAdzanDialog.dismiss();
            } catch (Exception ignored) {
            }
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(48, 36, 48, 36);
        container.setBackgroundColor(Color.TRANSPARENT);
        container.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView badge = new TextView(this);
        badge.setText("WAKTU SHOLAT KOTA CIREBON TELAH TIBA");
        badge.setTextColor(0xFFFDE047);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        container.addView(badge);

        TextView title = new TextView(this);
        title.setText("ADZAN " + prayerName.toUpperCase(Locale.US) + " - " + prayerTimeStr + " WIB");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 8, 0, 6);
        container.addView(title);

        TextView subLoc = new TextView(this);
        subLoc.setText("Suara: " + AdzanAudioPlayer.getMuadzinLabel(config.adzanMuadzinIndex));
        subLoc.setTextColor(0xFFA7F3D0);
        subLoc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        subLoc.setGravity(Gravity.CENTER);
        container.addView(subLoc);

        final TextView lafadzBox = new TextView(this);
        lafadzBox.setText("Allahu Akbar, Allahu Akbar\n(Allah Maha Besar, Allah Maha Besar)");
        lafadzBox.setTextColor(0xFFFFFFFF);
        lafadzBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        lafadzBox.setGravity(Gravity.CENTER);
        lafadzBox.setPadding(24, 20, 24, 20);
        container.addView(lafadzBox);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setView(container)
                .setPositiveButton("Matikan Suara Adzan", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        adzanPlayer.stop();
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        adzanPlayer.stop();
                    }
                });
        activeAdzanDialog = showTranslucentDialog(builder);

        adzanPlayer.playAdzan(
                this,
                prayerName,
                config.customAdzanPath,
                config.adzanMuadzinIndex,
                new AdzanAudioPlayer.OnAdzanListener() {
                    @Override
                    public void onLafadzChanged(String arabicTranslit, String indonesianMeaning) {
                        lafadzBox.setText(arabicTranslit + "\n(" + indonesianMeaning + ")");
                    }

                    @Override
                    public void onAdzanFinished() {
                        lafadzBox.setText("Alhamdulillah, panggilan Adzan " + prayerName + " selesai.\nSelamat menunaikan ibadah sholat " + prayerName + ".");
                    }
                }
        );
    }

    // =========================================================================
    // 2. LOGIKA CUACA WILAYAH KARYA MULYA, KOTA CIREBON
    // =========================================================================

    private void syncWeatherKaryaMulyaAsync(final boolean showToast) {
        bgExecutor.execute(new Runnable() {
            @Override
            public void run() {
                weatherManager.fetchKaryaMulyaWeatherSync();
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateWeatherKaryaMulyaDisplay();
                        updateRunningTextBar();
                        if (showToast) {
                            Toast.makeText(
                                    MainActivity.this,
                                    "Cuaca Karya Mulya Kota Cirebon diperbarui",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }
                });
            }
        });
    }

    private void updateWeatherKaryaMulyaDisplay() {
        if (weatherKaryaMulyaText == null) return;
        WeatherManager.WeatherInfo info = weatherManager.getCurrentWeather();
        weatherKaryaMulyaText.setText(info.toCompactSummary());
        weatherKaryaMulyaText.setVisibility(config.showWeatherKaryaMulya ? View.VISIBLE : View.GONE);
    }

    private void showKaryaMulyaWeatherDialog() {
        WeatherManager.WeatherInfo info = weatherManager.getCurrentWeather();

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(44, 30, 44, 20);
        container.setBackgroundColor(Color.TRANSPARENT);

        TextView header = new TextView(this);
        header.setText("CUACA REAL-TIME KARYA MULYA, KOTA CIREBON");
        header.setTextColor(0xFF38BDF8);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        container.addView(header);

        TextView body = new TextView(this);
        body.setText(info.toDetailedReport());
        body.setTextColor(0xFFFFFFFF);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        body.setTypeface(Typeface.MONOSPACE);
        body.setPadding(0, 14, 0, 10);
        container.addView(body);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setView(container)
                .setPositiveButton("Refresh Cuaca Sekarang", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        syncWeatherKaryaMulyaAsync(true);
                    }
                })
                .setNegativeButton("Tutup", null);
        showTranslucentDialog(builder);
    }

    // =========================================================================
    // 3. LOGIKA JADWAL SEPAK BOLA REAL DARI LIVESCORE.MOBI
    // =========================================================================

    private void syncFootballMatchesAsync(final boolean showFeedbackToast) {
        bgExecutor.execute(new Runnable() {
            @Override
            public void run() {
                footballManager.refreshOnlineSchedulesSync(config.footballLeagues);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        rotateFootballMatchCard();
                        if (showFeedbackToast) {
                            int liveCount = footballManager.countByCategory(FootballScheduleManager.FILTER_LIVE);
                            int ftCount = footballManager.countByCategory(FootballScheduleManager.FILTER_RESULT);
                            int upCount = footballManager.countByCategory(FootballScheduleManager.FILTER_UPCOMING);
                            Toast.makeText(
                                    MainActivity.this,
                                    "LiveScore.mobi diperbarui (Live: " + liveCount + ", Hasil FT: " + ftCount + ", Jadwal: " + upCount + ")",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }
                });
            }
        });
    }

    private void rotateFootballMatchCard() {
        if (footballManager == null) return;
        FootballScheduleManager.FootballMatch match = footballManager.getNextRotatedMatch();
        if (match == null) return;

        int idx = footballManager.getCurrentDisplayIndex();
        int total = footballManager.getActiveMatchCount();
        footballLeagueTitle.setText("LIVESCORE.MOBI | " + match.leagueName.toUpperCase(Locale.ROOT)
                + " (" + idx + "/" + total + ")");
        footballMatchTeams.setText(match.getDisplayHeadline());

        FootballScheduleManager.FootballMatch nextUp = footballManager.peekNextMatch();
        String nextInfo = (nextUp != null && nextUp != match)
                ? "  |  Berikutnya: " + nextUp.getDisplayHeadline()
                : "";
        footballMatchTime.setText(match.kickoffWibFormatted + nextInfo);
        footballStatusBadge.setText(match.statusBadge);

        float density = getResources().getDisplayMetrics().density;
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(6f * density);
        if ("LIVE".equalsIgnoreCase(match.matchCategory)) {
            badgeBg.setColor(0xAAEF4444);
        } else if ("RESULT".equalsIgnoreCase(match.matchCategory)) {
            badgeBg.setColor(0xAA059669);
        } else {
            badgeBg.setColor(0xAA0284C7);
        }
        footballStatusBadge.setBackground(badgeBg);
    }

    // =========================================================================
    // 4. LOGIKA LANGIT DINAMIS & NETWORK STATUS
    // =========================================================================

    private void renderSkyBackground() {
        Bitmap skyBmp = Sky.loadOrRenderWallpaper(
                this,
                config.wallpaperPreset,
                config.skyIntensity,
                config.customWallpaperPath
        );
        if (skyBmp != null) {
            skyView.setImageBitmap(skyBmp);
        }
    }

    private void updateAmbientGlow(int color) {
        if (!config.ambientGlow) {
            ambientView.setAlpha(0f);
            return;
        }
        if (color == currentAmbientColor && ambientView.getDrawable() != null) {
            return;
        }
        currentAmbientColor = color;
        int w = 320;
        int h = 180;
        Bitmap glowBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(glowBmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int glowColor = Color.argb(110, Color.red(color), Color.green(color), Color.blue(color));
        p.setShader(new RadialGradient(
                w * 0.18f, h * 0.5f, w * 0.55f,
                glowColor, 0x00000000, Shader.TileMode.CLAMP
        ));
        c.drawRect(0, 0, w, h, p);
        ambientView.setImageBitmap(glowBmp);
        ambientView.setAlpha(0.65f);
    }

    private void updateNetworkStatusPill() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo active = cm != null ? cm.getActiveNetworkInfo() : null;
            if (active != null && active.isConnected()) {
                if (active.getType() == ConnectivityManager.TYPE_ETHERNET) {
                    networkPill.setText("LAN");
                } else if (active.getType() == ConnectivityManager.TYPE_WIFI) {
                    WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    WifiInfo wi = wm != null ? wm.getConnectionInfo() : null;
                    String ssid = (wi != null && wi.getSSID() != null) ? wi.getSSID().replace("\"", "") : "Wi-Fi";
                    if ("<unknown ssid>".equalsIgnoreCase(ssid) || ssid.isEmpty()) {
                        ssid = "Wi-Fi";
                    }
                    networkPill.setText(ssid);
                } else {
                    networkPill.setText("Online");
                }
            } else {
                networkPill.setText("Offline");
            }
        } catch (Exception e) {
            networkPill.setText("Wi-Fi");
        }
    }

    private void updateSystemStats() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            long availMemMb = 0;
            if (am != null) {
                am.getMemoryInfo(mi);
                availMemMb = mi.availMem / (1024 * 1024);
            }
            StatFs statFs = new StatFs(Environment.getDataDirectory().getPath());
            long freeBytes = statFs.getAvailableBytes();
            long freeStorageMb = freeBytes / (1024 * 1024);
            statsText.setText("RAM Bebas: " + availMemMb + " MB  |  Storage Bebas: " + freeStorageMb + " MB");
        } catch (Exception ignored) {
        }
    }

    // =========================================================================
    // 5. INTERAKSI GRID APP FAVORIT & DIALOG KELOLA FAVORIT (TRANSPARAN)
    // =========================================================================

    @Override
    public void onAppFocused(AppAdapter.AppEntry entry, View itemView, int position) {
        dockFocusTitle.setText(entry.label);
        if (isReorderMode) {
            dockFocusHint.setText("MODE PINDAH FAVORIT: Arah Panah Geser Urutan, OK/BACK Selesai");
        } else if (entry.isBuiltinAction) {
            if ("builtin_manage_favorites".equals(entry.builtinId)) {
                dockFocusHint.setText("OK: Pilih / Tambah / Hapus Aplikasi Favorit di Menu Kiri");
            } else {
                dockFocusHint.setText("OK: Buka Daftar Semua Aplikasi yang Terinstal di TV");
            }
        } else {
            dockFocusHint.setText("OK: Buka | Tahan OK: Atur Favorit | Atas: Ke Jadwal Bola/Sholat");
        }

        if (config.appGlow) {
            updateAmbientGlow(entry.vividColor);
        }
        if (dockGlassView != null) {
            dockGlassView.setAccentColor(entry.vividColor);
        }
    }

    @Override
    public void onAppClicked(AppAdapter.AppEntry entry, int position) {
        if (isReorderMode) {
            isReorderMode = false;
            dockFocusHint.setText("Urutan Favorit disimpan | OK: Buka | Tahan OK: Atur Favorit");
            Toast.makeText(this, "Urutan App Favorit disimpan", Toast.LENGTH_SHORT).show();
            return;
        }

        if (entry.isBuiltinAction) {
            if ("builtin_manage_favorites".equals(entry.builtinId)) {
                showManageFavoritesDialog();
            } else if ("builtin_app_library".equals(entry.builtinId)) {
                showAllAppsDrawerDialog();
            }
            return;
        }

        launchPackage(entry.packageName, entry.activityName);
    }

    @Override
    public void onAppLongClicked(final AppAdapter.AppEntry entry, final int position) {
        if (entry.isBuiltinAction) {
            showManageFavoritesDialog();
            return;
        }

        final boolean isFav = config.isFavorite(entry.packageName);
        String[] actions = new String[]{
                "Buka Aplikasi (" + entry.label + ")",
                isFav ? "Hapus dari Menu Favorit Kiri" : "Tambahkan ke Menu Favorit Kiri",
                "Pilih / Kelola Daftar App Favorit",
                "Pindahkan Urutan Ikon di Menu Favorit (Mode Geser)",
                "Info Aplikasi Sistem (App Info)"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(entry.label)
                .setItems(actions, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            launchPackage(entry.packageName, entry.activityName);
                        } else if (which == 1) {
                            boolean added = config.toggleFavorite(MainActivity.this, entry.packageName);
                            appAdapter.reloadApps();
                            clampGridCursor();
                            renderCursorHighlights();
                            Toast.makeText(
                                    MainActivity.this,
                                    added ? (entry.label + " ditambahkan ke Favorit") : (entry.label + " dihapus dari Favorit"),
                                    Toast.LENGTH_SHORT
                            ).show();
                        } else if (which == 2) {
                            showManageFavoritesDialog();
                        } else if (which == 3) {
                            isReorderMode = true;
                            dockFocusHint.setText("MODE PINDAH FAVORIT: Gunakan Panah Atas/Bawah untuk Geser, OK Selesai");
                            Toast.makeText(
                                    MainActivity.this,
                                    "Gunakan tombol arah panah remote untuk menggeser posisi ikon favorit",
                                    Toast.LENGTH_LONG
                            ).show();
                        } else if (which == 4) {
                            openAppDetails(entry.packageName);
                        }
                    }
                });
        showTranslucentDialog(builder);
    }

    private void showManageFavoritesDialog() {
        final List<AppAdapter.AppEntry> allApps = appAdapter.getAllInstalledApps();
        if (allApps.isEmpty()) {
            Toast.makeText(this, "Belum ada daftar aplikasi terdeteksi", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[allApps.size()];
        final boolean[] checked = new boolean[allApps.size()];
        for (int i = 0; i < allApps.size(); i++) {
            AppAdapter.AppEntry app = allApps.get(i);
            labels[i] = app.label;
            checked[i] = config.isFavorite(app.packageName);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Pilih Aplikasi Favorit (Menu Grid Kiri)")
                .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                })
                .setPositiveButton("Simpan Favorit", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        List<String> newFavs = new ArrayList<>();
                        for (String existingPkg : config.favoriteApps) {
                            for (int i = 0; i < allApps.size(); i++) {
                                if (checked[i] && allApps.get(i).packageName.equals(existingPkg)) {
                                    if (!newFavs.contains(existingPkg)) {
                                        newFavs.add(existingPkg);
                                    }
                                }
                            }
                        }
                        for (int i = 0; i < allApps.size(); i++) {
                            if (checked[i]) {
                                String pkg = allApps.get(i).packageName;
                                if (!newFavs.contains(pkg)) {
                                    newFavs.add(pkg);
                                }
                            }
                        }
                        config.favoriteApps.clear();
                        config.favoriteApps.addAll(newFavs);
                        config.saveFavoriteApps(MainActivity.this);

                        appAdapter.reloadApps();
                        clampGridCursor();
                        renderCursorHighlights();
                        Toast.makeText(
                                MainActivity.this,
                                "Menu Favorit diperbarui (" + config.favoriteApps.size() + " aplikasi)",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                })
                .setNeutralButton("Buka Semua App", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showAllAppsDrawerDialog();
                    }
                })
                .setNegativeButton("Batal", null);
        showTranslucentDialog(builder);
    }

    private void showAllAppsDrawerDialog() {
        final List<AppAdapter.AppEntry> allApps = appAdapter.getAllInstalledApps();
        String[] labels = new String[allApps.size()];
        for (int i = 0; i < allApps.size(); i++) {
            AppAdapter.AppEntry e = allApps.get(i);
            boolean fav = config.isFavorite(e.packageName);
            labels[i] = (fav ? "[FAVORIT] " : "") + e.label;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Semua Aplikasi Terinstal (" + allApps.size() + ")")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which >= 0 && which < allApps.size()) {
                            AppAdapter.AppEntry e = allApps.get(which);
                            launchPackage(e.packageName, e.activityName);
                        }
                    }
                })
                .setNeutralButton("Atur App Favorit", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showManageFavoritesDialog();
                    }
                })
                .setNegativeButton("Tutup", null);
        showTranslucentDialog(builder);
    }

    private void launchPackage(String pkg, String activityName) {
        try {
            if (activityName != null && !activityName.isEmpty()) {
                Intent explicit = new Intent(Intent.ACTION_MAIN);
                explicit.setComponent(new ComponentName(pkg, activityName));
                explicit.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(explicit);
                return;
            }
            Intent leanback = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (leanback != null) {
                leanback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(leanback);
                return;
            }
            Intent standard = getPackageManager().getLaunchIntentForPackage(pkg);
            if (standard != null) {
                standard.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(standard);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Gagal membuka: " + pkg, Toast.LENGTH_SHORT).show();
        }
    }

    private void openAppDetails(String pkg) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + pkg));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    // =========================================================================
    // 6. ROLLING BACKGROUND & DIALOG GANTI BACKGROUND (.JPG, .PNG, .GIF, .MP4)
    // =========================================================================

    private void rescheduleRollingBackgroundTimer() {
        mainHandler.removeCallbacks(rollingBgRunnable);
        if (config != null && config.rollingBackgroundEnabled) {
            long delayMs = Math.max(5, config.rollingBackgroundIntervalSec) * 1000L;
            mainHandler.postDelayed(rollingBgRunnable, delayMs);
        }
    }

    private List<File> getRollingBackgroundFiles() {
        List<File> list = new ArrayList<>();
        File internalBgDir = new File(getFilesDir(), "backgrounds");
        if (internalBgDir.exists() && internalBgDir.isDirectory()) {
            File[] files = internalBgDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.length() > 0 && isSupportedBackgroundFile(f.getName())) {
                        list.add(f);
                    }
                }
            }
        }
        if (config.rollingCustomFolder != null && !config.rollingCustomFolder.trim().isEmpty()) {
            File extDir = new File(config.rollingCustomFolder.trim());
            if (extDir.exists() && extDir.isDirectory()) {
                File[] files = extDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile() && f.length() > 0 && isSupportedBackgroundFile(f.getName())) {
                            list.add(f);
                        }
                    }
                }
            }
        }
        Collections.sort(list, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        return list;
    }

    private void advanceRollingBackground(boolean showToast) {
        List<File> mediaFiles = getRollingBackgroundFiles();
        int[] presetIds = new int[]{
                Sky.WALLPAPER_CIREBON_EMERALD,
                Sky.WALLPAPER_STADIUM_NIGHT,
                Sky.WALLPAPER_AURORA_BOREALIS,
                Sky.WALLPAPER_SUNSET_MOUNTAINS,
                Sky.WALLPAPER_TIME_OF_DAY
        };

        if (mediaFiles.size() >= 2 && !config.rollingIncludePresets) {
            rollingBgIndex = (rollingBgIndex + 1) % mediaFiles.size();
            File chosen = mediaFiles.get(rollingBgIndex);
            config.customWallpaperPath = chosen.getAbsolutePath();
            Config.saveUserStringPref(this, "pref_custom_wallpaper_path", config.customWallpaperPath);
            applyCustomBackgroundMedia();
            if (showToast) {
                Toast.makeText(this, "Rolling Background: " + chosen.getName(), Toast.LENGTH_SHORT).show();
            }
            return;
        }

        int totalSlots = mediaFiles.size() + (config.rollingIncludePresets || mediaFiles.size() < 2 ? presetIds.length : 0);
        if (totalSlots <= 0) totalSlots = presetIds.length;
        rollingBgIndex = (rollingBgIndex + 1) % totalSlots;

        if (rollingBgIndex < mediaFiles.size()) {
            File chosen = mediaFiles.get(rollingBgIndex);
            config.customWallpaperPath = chosen.getAbsolutePath();
            Config.saveUserStringPref(this, "pref_custom_wallpaper_path", config.customWallpaperPath);
            applyCustomBackgroundMedia();
            if (showToast) {
                Toast.makeText(this, "Rolling Background: " + chosen.getName(), Toast.LENGTH_SHORT).show();
            }
        } else {
            int pIdx = (rollingBgIndex - mediaFiles.size()) % presetIds.length;
            config.customWallpaperPath = "";
            Config.saveUserStringPref(this, "pref_custom_wallpaper_path", "");
            config.wallpaperPreset = presetIds[pIdx];
            Config.saveUserIntPref(this, "pref_wallpaper_preset", config.wallpaperPreset);
            applyCustomBackgroundMedia();
            renderSkyBackground();
            if (showToast) {
                Toast.makeText(this, "Rolling Background: " + Sky.WALLPAPER_NAMES[config.wallpaperPreset], Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showRollingBackgroundDialog() {
        List<File> files = getRollingBackgroundFiles();
        String statusStr = config.rollingBackgroundEnabled ? "AKTIF (ON)" : "MATI (OFF)";
        String modeStr = config.rollingIncludePresets
                ? "File Kustom + Preset Warna"
                : "Hanya Koleksi File Kustom (" + files.size() + " file)";

        final String[] items = new String[]{
                "1. Status Rolling Background: " + statusStr + " (Klik untuk ON/OFF)",
                "2. Interval Waktu Ganti Otomatis: Setiap " + config.rollingBackgroundIntervalSec + " Detik",
                "3. Mode Rolling: " + modeStr,
                "4. Ganti ke Background Berikutnya Sekarang (Tes Rolling)",
                "5. Tambah Banyak File Gambar/GIF/MP4 dari HP (Wi-Fi)",
                "6. Pilih Folder Koleksi Background di Memori Internal",
                "7. Hapus Semua File Koleksi di Memori App (" + files.size() + " file)"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Pengaturan Rolling Background (Slideshow Otomatis)")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            config.rollingBackgroundEnabled = !config.rollingBackgroundEnabled;
                            Config.saveUserPref(MainActivity.this, "pref_rolling_bg_enabled", config.rollingBackgroundEnabled);
                            rescheduleRollingBackgroundTimer();
                            Toast.makeText(
                                    MainActivity.this,
                                    "Rolling Background: " + (config.rollingBackgroundEnabled ? "AKTIF" : "NONAKTIF"),
                                    Toast.LENGTH_SHORT
                            ).show();
                            showRollingBackgroundDialog();
                        } else if (which == 1) {
                            showRollingIntervalPickerDialog();
                        } else if (which == 2) {
                            config.rollingIncludePresets = !config.rollingIncludePresets;
                            Config.saveUserPref(MainActivity.this, "pref_rolling_include_presets", config.rollingIncludePresets);
                            showRollingBackgroundDialog();
                        } else if (which == 3) {
                            advanceRollingBackground(true);
                        } else if (which == 4) {
                            showWifiTransferServerDialog();
                        } else if (which == 5) {
                            File startRoot = Environment.getExternalStorageDirectory();
                            if (startRoot == null || !startRoot.exists()) {
                                startRoot = getFilesDir();
                            }
                            showInternalStorageBrowserDialog(startRoot);
                        } else if (which == 6) {
                            File internalBgDir = new File(getFilesDir(), "backgrounds");
                            if (internalBgDir.exists() && internalBgDir.isDirectory()) {
                                File[] list = internalBgDir.listFiles();
                                if (list != null) {
                                    for (File f : list) {
                                        f.delete();
                                    }
                                }
                            }
                            config.rollingCustomFolder = "";
                            Config.saveUserStringPref(MainActivity.this, "pref_rolling_custom_folder", "");
                            Toast.makeText(MainActivity.this, "Koleksi Rolling Background dikosongkan", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Kembali", null);
        showTranslucentDialog(builder);
    }

    private void showRollingIntervalPickerDialog() {
        final String[] labels = new String[]{
                "Setiap 10 Detik",
                "Setiap 15 Detik",
                "Setiap 30 Detik (Rekomendasi)",
                "Setiap 1 Menit (60 Detik)",
                "Setiap 3 Menit (180 Detik)",
                "Setiap 5 Menit (300 Detik)",
                "Setiap 15 Menit (900 Detik)"
        };
        final int[] secs = new int[]{10, 15, 30, 60, 180, 300, 900};

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Pilih Interval Waktu Rolling Background")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        config.rollingBackgroundIntervalSec = secs[which];
                        Config.saveUserIntPref(MainActivity.this, "pref_rolling_bg_interval_sec", config.rollingBackgroundIntervalSec);
                        config.rollingBackgroundEnabled = true;
                        Config.saveUserPref(MainActivity.this, "pref_rolling_bg_enabled", true);
                        rescheduleRollingBackgroundTimer();
                        Toast.makeText(
                                MainActivity.this,
                                "Rolling Background diaktifkan: " + labels[which],
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                })
                .setNegativeButton("Batal", null);
        showTranslucentDialog(builder);
    }

    private void showBackgroundPickerDialog() {
        int presetIdx = Math.max(0, Math.min(Sky.WALLPAPER_NAMES.length - 1, config.wallpaperPreset));
        String currentBgInfo = (config.customWallpaperPath != null && !config.customWallpaperPath.isEmpty())
                ? "File Aktif: " + new File(config.customWallpaperPath).getName()
                : "Preset Aktif: " + Sky.WALLPAPER_NAMES[presetIdx];
        String rollingInfo = config.rollingBackgroundEnabled
                ? ("AKTIF - Tiap " + config.rollingBackgroundIntervalSec + " dtk")
                : "NONAKTIF";

        final String[] options = new String[]{
                "1. Rolling Background Otomatis (Slideshow): " + rollingInfo,
                "2. Transfer Gambar/GIF/MP4 dari Browser HP (Wi-Fi - Bisa Banyak File)",
                "3. Cari File / Folder (.jpg / .png / .gif / .mp4) di Memori Internal",
                "4. Buka File Picker Sistem Android",
                "5. Gunakan Tema Background Bawaan (6 Preset Warna)",
                "6. Atur Tingkat Kecerahan/Redup Background (Saat Ini: " + config.wallpaperDimPercent + "%)",
                "7. Reset ke Background Default Cirebon Emerald"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Ubah Background (" + currentBgInfo + ")")
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            showRollingBackgroundDialog();
                        } else if (which == 1) {
                            showWifiTransferServerDialog();
                        } else if (which == 2) {
                            File startRoot = Environment.getExternalStorageDirectory();
                            if (startRoot == null || !startRoot.exists()) {
                                startRoot = getFilesDir();
                            }
                            showInternalStorageBrowserDialog(startRoot);
                        } else if (which == 3) {
                            openSystemMediaPicker();
                        } else if (which == 4) {
                            showPresetThemeDialog();
                        } else if (which == 5) {
                            showWallpaperDimDialog();
                        } else if (which == 6) {
                            config.rollingBackgroundEnabled = false;
                            Config.saveUserPref(MainActivity.this, "pref_rolling_bg_enabled", false);
                            rescheduleRollingBackgroundTimer();
                            config.customWallpaperPath = "";
                            Config.saveUserStringPref(MainActivity.this, "pref_custom_wallpaper_path", "");
                            config.wallpaperPreset = Sky.WALLPAPER_CIREBON_EMERALD;
                            Config.saveUserIntPref(MainActivity.this, "pref_wallpaper_preset", config.wallpaperPreset);
                            applyCustomBackgroundMedia();
                            renderSkyBackground();
                            Toast.makeText(MainActivity.this, "Background direset ke Cirebon Emerald", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Tutup", null);
        showTranslucentDialog(builder);
    }

    private void showWifiTransferServerDialog() {
        if (transferServer == null) {
            transferServer = new WallpaperTransferServer(this, 8888, new WallpaperTransferServer.OnMediaReceivedListener() {
                @Override
                public void onMediaFileReceived(final File savedFile, final String ext) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            config.customWallpaperPath = savedFile.getAbsolutePath();
                            Config.saveUserStringPref(MainActivity.this, "pref_custom_wallpaper_path", config.customWallpaperPath);
                            applyCustomBackgroundMedia();
                            Toast.makeText(
                                    MainActivity.this,
                                    "Background baru diterapkan dari HP (" + savedFile.getName() + ")",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    });
                }
            });
        }
        transferServer.start();
        String url = "http://" + WallpaperTransferServer.getLocalIpAddress() + ":8888";

        String message = "Cara Paling Mudah Mengirim Gambar/Video dari HP ke TV:\n\n"
                + "1. Pastikan HP dan TV terhubung ke Wi-Fi / Hotspot yang sama.\n"
                + "2. Buka Chrome / Browser di HP Anda, lalu ketik alamat ini:\n\n"
                + "     " + url + "\n\n"
                + "3. Pilih 1 atau banyak file (.jpg, .png, .gif, atau .mp4) dari galeri HP lalu tekan tombol Kirim.\n"
                + "4. Background TV akan otomatis berubah seketika!";

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Transfer Background dari HP (Port 8888)")
                .setMessage(message)
                .setPositiveButton("Selesai / Tutup", null);
        showTranslucentDialog(builder);
    }

    private void showInternalStorageBrowserDialog(final File currentDir) {
        final List<File> entries = new ArrayList<>();
        final List<String> labels = new ArrayList<>();

        File parent = currentDir.getParentFile();
        if (parent != null && parent.canRead()) {
            entries.add(parent);
            labels.add("[..] Kembali ke Folder Induk (" + parent.getName() + ")");
        }

        File[] children = currentDir.listFiles();
        if (children != null) {
            for (File f : children) {
                if (f.getName().startsWith(".")) continue;
                if (f.isDirectory()) {
                    entries.add(f);
                    labels.add("[FOLDER] " + f.getName());
                }
            }
            for (File f : children) {
                if (f.getName().startsWith(".")) continue;
                if (f.isFile() && isSupportedBackgroundFile(f.getName())) {
                    entries.add(f);
                    long sizeKb = Math.max(1, f.length() / 1024);
                    labels.add("[MEDIA] " + f.getName() + " (" + sizeKb + " KB)");
                }
            }
        }

        if (entries.isEmpty()) {
            Toast.makeText(
                    this,
                    "Folder tidak dapat diakses atau kosong. Gunakan opsi Transfer Wi-Fi dari HP.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        String[] itemsArr = labels.toArray(new String[0]);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Memori: " + currentDir.getAbsolutePath())
                .setItems(itemsArr, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which >= 0 && which < entries.size()) {
                            File chosen = entries.get(which);
                            if (chosen.isDirectory()) {
                                showInternalStorageBrowserDialog(chosen);
                            } else {
                                copyAndApplySelectedMediaFile(chosen);
                            }
                        }
                    }
                })
                .setPositiveButton("Jadikan Folder Rolling Background", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        config.rollingCustomFolder = currentDir.getAbsolutePath();
                        Config.saveUserStringPref(MainActivity.this, "pref_rolling_custom_folder", config.rollingCustomFolder);
                        config.rollingBackgroundEnabled = true;
                        Config.saveUserPref(MainActivity.this, "pref_rolling_bg_enabled", true);
                        rescheduleRollingBackgroundTimer();
                        advanceRollingBackground(true);
                        Toast.makeText(
                                MainActivity.this,
                                "Rolling Background aktif dari folder: " + currentDir.getName(),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                })
                .setNeutralButton("Transfer dari HP (Wi-Fi)", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showWifiTransferServerDialog();
                    }
                })
                .setNegativeButton("Batal", null);
        showTranslucentDialog(builder);
    }

    private boolean isSupportedBackgroundFile(String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".webp")
                || lower.endsWith(".gif")
                || lower.endsWith(".mp4")
                || lower.endsWith(".webm")
                || lower.endsWith(".mkv");
    }

    private void copyAndApplySelectedMediaFile(final File sourceFile) {
        bgExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    File destDir = new File(getFilesDir(), "backgrounds");
                    if (!destDir.exists()) destDir.mkdirs();

                    String ext = ".jpg";
                    String nameLow = sourceFile.getName().toLowerCase(Locale.US);
                    int dot = nameLow.lastIndexOf('.');
                    if (dot >= 0) ext = nameLow.substring(dot);

                    final File outFile = new File(destDir, "bg_" + System.currentTimeMillis() + ext);
                    java.io.FileInputStream fis = new java.io.FileInputStream(sourceFile);
                    FileOutputStream fos = new FileOutputStream(outFile);
                    byte[] buf = new byte[16384];
                    int r;
                    while ((r = fis.read(buf)) != -1) {
                        fos.write(buf, 0, r);
                    }
                    fos.flush();
                    fos.close();
                    fis.close();

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            config.customWallpaperPath = outFile.getAbsolutePath();
                            Config.saveUserStringPref(MainActivity.this, "pref_custom_wallpaper_path", config.customWallpaperPath);
                            applyCustomBackgroundMedia();
                            Toast.makeText(
                                    MainActivity.this,
                                    "Background diterapkan: " + sourceFile.getName(),
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(
                                    MainActivity.this,
                                    "Gagal membaca file: " + e.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    });
                }
            }
        });
    }

    private void openSystemMediaPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/mp4", "video/*"});
            startActivityForResult(intent, REQUEST_PICK_BACKGROUND_MEDIA);
            return;
        } catch (Exception ignored) {
        }
        try {
            Intent getContent = new Intent(Intent.ACTION_GET_CONTENT);
            getContent.setType("*/*");
            startActivityForResult(Intent.createChooser(getContent, "Pilih Background"), REQUEST_PICK_BACKGROUND_MEDIA);
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "File Picker sistem tidak tersedia di TV ini. Gunakan Transfer Wi-Fi dari HP.",
                    Toast.LENGTH_LONG
            ).show();
            showWifiTransferServerDialog();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_BACKGROUND_MEDIA && resultCode == RESULT_OK && data != null) {
            final Uri uri = data.getData();
            if (uri != null) {
                copyUriToInternalBackground(uri);
            }
        } else if (requestCode == REQUEST_PICK_ADZAN_AUDIO && resultCode == RESULT_OK && data != null) {
            final Uri uri = data.getData();
            if (uri != null) {
                copyUriToInternalAdzan(uri);
            }
        }
    }

    private void copyUriToInternalBackground(final Uri uri) {
        bgExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String ext = ".jpg";
                    String mime = getContentResolver().getType(uri);
                    if (mime != null) {
                        if (mime.contains("gif")) ext = ".gif";
                        else if (mime.contains("mp4") || mime.contains("video")) ext = ".mp4";
                        else if (mime.contains("png")) ext = ".png";
                    }
                    Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                    if (cursor != null) {
                        int nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (cursor.moveToFirst() && nameIdx >= 0) {
                            String displayName = cursor.getString(nameIdx);
                            if (displayName != null && displayName.contains(".")) {
                                ext = displayName.substring(displayName.lastIndexOf('.')).toLowerCase(Locale.US);
                            }
                        }
                        cursor.close();
                    }

                    File destDir = new File(getFilesDir(), "backgrounds");
                    if (!destDir.exists()) destDir.mkdirs();
                    final File outFile = new File(destDir, "bg_" + System.currentTimeMillis() + ext);

                    InputStream is = getContentResolver().openInputStream(uri);
                    if (is == null) return;
                    FileOutputStream fos = new FileOutputStream(outFile);
                    byte[] buf = new byte[16384];
                    int r;
                    while ((r = is.read(buf)) != -1) {
                        fos.write(buf, 0, r);
                    }
                    fos.flush();
                    fos.close();
                    is.close();

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            config.customWallpaperPath = outFile.getAbsolutePath();
                            Config.saveUserStringPref(MainActivity.this, "pref_custom_wallpaper_path", config.customWallpaperPath);
                            applyCustomBackgroundMedia();
                            Toast.makeText(MainActivity.this, "Background berhasil diterapkan!", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void copyUriToInternalAdzan(final Uri uri) {
        bgExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    File destDir = new File(getFilesDir(), "adzan");
                    if (!destDir.exists()) destDir.mkdirs();
                    final File outFile = new File(destDir, "custom_adzan.mp3");

                    InputStream is = getContentResolver().openInputStream(uri);
                    if (is == null) return;
                    FileOutputStream fos = new FileOutputStream(outFile);
                    byte[] buf = new byte[16384];
                    int r;
                    while ((r = is.read(buf)) != -1) {
                        fos.write(buf, 0, r);
                    }
                    fos.flush();
                    fos.close();
                    is.close();

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            config.customAdzanPath = outFile.getAbsolutePath();
                            Config.saveUserStringPref(MainActivity.this, "pref_custom_adzan_path", config.customAdzanPath);
                            Toast.makeText(MainActivity.this, "File MP3 Suara Adzan berhasil disimpan!", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void showPresetThemeDialog() {
        String[] presetLabels = new String[Sky.WALLPAPER_NAMES.length];
        for (int i = 0; i < Sky.WALLPAPER_NAMES.length; i++) {
            presetLabels[i] = Sky.WALLPAPER_NAMES[i];
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Pilih Preset Tema Background")
                .setSingleChoiceItems(presetLabels, config.wallpaperPreset, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        config.customWallpaperPath = "";
                        Config.saveUserStringPref(MainActivity.this, "pref_custom_wallpaper_path", "");
                        config.wallpaperPreset = which;
                        Config.saveUserIntPref(MainActivity.this, "pref_wallpaper_preset", which);
                        applyCustomBackgroundMedia();
                        renderSkyBackground();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("Batal", null);
        showTranslucentDialog(builder);
    }

    private void showWallpaperDimDialog() {
        final String[] dimLabels = new String[]{
                "0% - Sangat Terang (Tanpa Lapisan Gelap)",
                "15% - Terang Jernih",
                "30% - Sedang",
                "45% - Redup Nyaman",
                "65% - Gelap Sinematik"
        };
        final int[] dimValues = new int[]{0, 15, 30, 45, 65};

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Tingkat Kecerahan Background")
                .setItems(dimLabels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        config.wallpaperDimPercent = dimValues[which];
                        Config.saveUserIntPref(MainActivity.this, "pref_wallpaper_dim", config.wallpaperDimPercent);
                        applyScrimDimming();
                    }
                })
                .setNegativeButton("Batal", null);
        showTranslucentDialog(builder);
    }

    // =========================================================================
    // 7. DIALOG DETAIL SHOLAT KOTA CIREBON & SUARA ADZAN MUADZIN ASLI (TRANSPARAN)
    // =========================================================================

    private void showCirebonPrayerDetailDialog() {
        if (todayPrayerTimes == null) {
            recalculateCirebonPrayerTimes(false);
        }

        TimeZone wib = TimeZone.getTimeZone("GMT+7");
        Calendar tomorrow = Calendar.getInstance(wib);
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        PrayerTimesCalculator.DailyPrayerSchedule tomorrowTimes = PrayerTimesCalculator.calculateKemenag(
                config.prayerCityName,
                tomorrow.get(Calendar.YEAR),
                tomorrow.get(Calendar.MONTH) + 1,
                tomorrow.get(Calendar.DAY_OF_MONTH),
                config.prayerLatitude,
                config.prayerLongitude,
                config.prayerTimezoneOffset,
                config.prayerIhtiyatMinutes
        );

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(44, 28, 44, 20);
        container.setBackgroundColor(Color.TRANSPARENT);

        TextView header = new TextView(this);
        header.setText("JADWAL SHOLAT & SUARA ADZAN KOTA CIREBON");
        header.setTextColor(0xFF34D399);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        container.addView(header);

        File cachedFile = adzanPlayer.getCachedMuadzinFile(this, config.adzanMuadzinIndex);
        String cacheStatus = (cachedFile.exists() && cachedFile.length() > 50000)
                ? "Tersimpan Offline (Siap Putar)"
                : "Streaming / Mengunduh Otomatis";
        String adzanSourceDesc = (config.customAdzanPath != null && !config.customAdzanPath.isEmpty())
                ? ("File MP3 Kustom (" + new File(config.customAdzanPath).getName() + ")")
                : (AdzanAudioPlayer.getMuadzinLabel(config.adzanMuadzinIndex) + " - " + cacheStatus);

        TextView subHeader = new TextView(this);
        subHeader.setText("Wilayah: " + config.prayerCityName + " (" + config.prayerLatitude + ", " + config.prayerLongitude + ")\n"
                + "Suara Adzan Otomatis: " + (config.enableAdzanSound ? "AKTIF (ON)" : "NONAKTIF (OFF)") + "\n"
                + "Muadzin: " + adzanSourceDesc);
        subHeader.setTextColor(0xFFFDE047);
        subHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        subHeader.setPadding(0, 4, 0, 12);
        container.addView(subHeader);

        String tableText = String.format(
                Locale.getDefault(),
                "JADWAL HARI INI (%02d/%02d/%04d)       JADWAL BESOK (%02d/%02d/%04d)\n"
                        + "  Imsak   : %s WIB              Imsak   : %s WIB\n"
                        + "  Subuh   : %s WIB [ADZAN]      Subuh   : %s WIB\n"
                        + "  Terbit  : %s WIB              Terbit  : %s WIB\n"
                        + "  Dhuha   : %s WIB              Dhuha   : %s WIB\n"
                        + "  Dzuhur  : %s WIB [ADZAN]      Dzuhur  : %s WIB\n"
                        + "  Ashar   : %s WIB [ADZAN]      Ashar   : %s WIB\n"
                        + "  Maghrib : %s WIB [ADZAN]      Maghrib : %s WIB\n"
                        + "  Isya    : %s WIB [ADZAN]      Isya    : %s WIB",
                todayPrayerTimes.day, todayPrayerTimes.month, todayPrayerTimes.year,
                tomorrowTimes.day, tomorrowTimes.month, tomorrowTimes.year,
                todayPrayerTimes.getImsak(), tomorrowTimes.getImsak(),
                todayPrayerTimes.getSubuh(), tomorrowTimes.getSubuh(),
                todayPrayerTimes.getTerbit(), tomorrowTimes.getTerbit(),
                todayPrayerTimes.getDhuha(), tomorrowTimes.getDhuha(),
                todayPrayerTimes.getDzuhur(), tomorrowTimes.getDzuhur(),
                todayPrayerTimes.getAshar(), tomorrowTimes.getAshar(),
                todayPrayerTimes.getMaghrib(), tomorrowTimes.getMaghrib(),
                todayPrayerTimes.getIsya(), tomorrowTimes.getIsya()
        );

        TextView scheduleBody = new TextView(this);
        scheduleBody.setText(tableText);
        scheduleBody.setTextColor(0xFFFFFFFF);
        scheduleBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        scheduleBody.setTypeface(Typeface.MONOSPACE);
        container.addView(scheduleBody);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setView(container)
                .setPositiveButton("Tes Suara Adzan Sekarang", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        triggerAdzanAlertAndSound("Dzuhur / Tes", "12:00");
                    }
                })
                .setNeutralButton("Pilih Muadzin & Lokasi", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showAdzanAndLocationSettingsDialog();
                    }
                })
                .setNegativeButton("Tutup", null);
        showTranslucentDialog(builder);
    }

    private void showAdzanAndLocationSettingsDialog() {
        String[] options = new String[]{
                "1. Suara Adzan Otomatis Saat Waktu Sholat Tiba: " + (config.enableAdzanSound ? "AKTIF (ON)" : "MATI (OFF)"),
                "2. Pilih Suara Muadzin Asli: " + AdzanAudioPlayer.getMuadzinLabel(config.adzanMuadzinIndex),
                "3. Tes Bunyikan Suara Adzan Sekarang",
                "4. Pilih File Audio Adzan MP3/WAV Kustom dari Penyimpanan",
                "5. Pilih Kecamatan / Wilayah di Cirebon (" + config.prayerCityName + ")",
                "6. Sinkronisasi Ulang Jadwal Kemenag RI Sekarang"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Pengaturan Suara Adzan & Wilayah Cirebon")
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            config.enableAdzanSound = !config.enableAdzanSound;
                            Config.saveUserPref(MainActivity.this, "pref_enable_adzan_sound", config.enableAdzanSound);
                            renderPrayerTimesCard();
                            renderCursorHighlights();
                            Toast.makeText(
                                    MainActivity.this,
                                    "Suara Adzan Otomatis: " + (config.enableAdzanSound ? "AKTIF" : "NONAKTIF"),
                                    Toast.LENGTH_SHORT
                            ).show();
                        } else if (which == 1) {
                            showMuadzinVoicePickerDialog();
                        } else if (which == 2) {
                            triggerAdzanAlertAndSound("Tes Adzan Cirebon", "12:00");
                        } else if (which == 3) {
                            try {
                                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                                intent.setType("audio/*");
                                startActivityForResult(Intent.createChooser(intent, "Pilih MP3 Adzan"), REQUEST_PICK_ADZAN_AUDIO);
                            } catch (Exception e) {
                                Toast.makeText(
                                        MainActivity.this,
                                        "Simpan file adzan.mp3 di folder Download TV untuk otomatis terdeteksi.",
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        } else if (which == 4) {
                            showCirebonSubdistrictPicker();
                        } else if (which == 5) {
                            recalculateCirebonPrayerTimes(true);
                            Toast.makeText(MainActivity.this, "Jadwal Sholat Cirebon diperbarui", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Kembali", null);
        showTranslucentDialog(builder);
    }

    private void showMuadzinVoicePickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Pilih Rekaman Suara Muadzin Asli")
                .setSingleChoiceItems(AdzanAudioPlayer.MUADZIN_NAMES, config.adzanMuadzinIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        config.adzanMuadzinIndex = which;
                        Config.saveUserIntPref(MainActivity.this, "pref_adzan_muadzin_idx", which);
                        config.customAdzanPath = "";
                        Config.saveUserStringPref(MainActivity.this, "pref_custom_adzan_path", "");
                        dialog.dismiss();
                        Toast.makeText(
                                MainActivity.this,
                                "Mengunduh & menyimpan suara: " + AdzanAudioPlayer.getMuadzinLabel(which),
                                Toast.LENGTH_SHORT
                        ).show();
                        adzanPlayer.prefetchMuadzinAudioAsync(MainActivity.this, which, new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Suara Muadzin siap digunakan!", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                })
                .setNegativeButton("Batal", null);
        showTranslucentDialog(builder);
    }

    private void showCirebonSubdistrictPicker() {
        final String[] names = new String[]{
                "Kelurahan Karya Mulya, Kec. Kesambi, Kota Cirebon",
                "Kota Cirebon (Pusat / Kejaksan / Alun-Alun)",
                "Kota Cirebon (Harjamukti / Kalijaga)",
                "Kota Cirebon (Pekalipan / Lemahwungkuk)",
                "Kabupaten Cirebon (Sumber / Kedawung / Plered)",
                "Kabupaten Indramayu",
                "Kabupaten Kuningan",
                "Kabupaten Majalengka"
        };
        final double[][] coords = new double[][]{
                {-6.7358, 108.5385},
                {-6.7063, 108.5570},
                {-6.7550, 108.5460},
                {-6.7220, 108.5680},
                {-6.7603, 108.4831},
                {-6.3276, 108.3249},
                {-6.9761, 108.4841},
                {-6.8364, 108.2278}
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Pilih Wilayah Jadwal Sholat")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        config.prayerCityName = names[which];
                        config.prayerLatitude = coords[which][0];
                        config.prayerLongitude = coords[which][1];
                        Config.savePrayerLocation(
                                MainActivity.this,
                                config.prayerCityName,
                                config.prayerLatitude,
                                config.prayerLongitude
                        );
                        recalculateCirebonPrayerTimes(true);
                        updateClockAndPrayerCountdown();
                    }
                });
        showTranslucentDialog(builder);
    }

    // =========================================================================
    // 8. DIALOG JADWAL SEPAK BOLA REAL DARI LIVESCORE.MOBI (TRANSPARAN)
    // =========================================================================

    private void showFootballScheduleDialog() {
        List<FootballScheduleManager.FootballMatch> list = footballManager.getMatches();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(40, 26, 40, 22);
        container.setBackgroundColor(Color.TRANSPARENT);

        TextView header = new TextView(this);
        header.setText("LIVESCORE.MOBI - JADWAL LIVE, HASIL (FT) & PERTANDINGAN");
        header.setTextColor(0xFFFBBF24);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        container.addView(header);

        int liveCount = footballManager.countByCategory(FootballScheduleManager.FILTER_LIVE);
        int ftCount = footballManager.countByCategory(FootballScheduleManager.FILTER_RESULT);
        int upCount = footballManager.countByCategory(FootballScheduleManager.FILTER_UPCOMING);

        TextView sub = new TextView(this);
        sub.setText("Sumber: https://www.livescore.mobi/ | Filter Aktif: " + footballManager.getFilterSlug()
                + " | Live: " + liveCount + " | Hasil FT: " + ftCount + " | Jadwal: " + upCount
                + " | Update: " + footballManager.getLastSyncTimeWib() + " WIB");
        sub.setTextColor(0xFF7DD3FC);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        sub.setPadding(0, 4, 0, 12);
        container.addView(sub);

        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < list.size(); i++) {
            FootballScheduleManager.FootballMatch m = list.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(20, 12, 20, 12);

            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setCornerRadius(10f * density);
            rowBg.setColor(i % 2 == 0 ? 0x441E293B : 0x360F172A);
            if ("LIVE".equalsIgnoreCase(m.matchCategory)) {
                rowBg.setStroke((int) (1.5f * density), 0xCCEF4444);
            } else if ("RESULT".equalsIgnoreCase(m.matchCategory)) {
                rowBg.setStroke((int) (1f * density), 0x9910B981);
            } else {
                rowBg.setStroke((int) (1f * density), 0x5560A5FA);
            }
            row.setBackground(rowBg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.bottomMargin = (int) (7 * density);
            row.setLayoutParams(lp);

            TextView leagueTv = new TextView(this);
            leagueTv.setText(m.leagueName.toUpperCase(Locale.ROOT) + "   [" + m.statusBadge + "]");
            if ("LIVE".equalsIgnoreCase(m.matchCategory)) {
                leagueTv.setTextColor(0xFFF87171);
            } else if ("RESULT".equalsIgnoreCase(m.matchCategory)) {
                leagueTv.setTextColor(0xFF34D399);
            } else {
                leagueTv.setTextColor(0xFF38BDF8);
            }
            leagueTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            leagueTv.setTypeface(Typeface.DEFAULT_BOLD);
            row.addView(leagueTv);

            TextView matchTv = new TextView(this);
            matchTv.setText(m.getDisplayHeadline());
            matchTv.setTextColor(0xFFFFFFFF);
            matchTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            matchTv.setTypeface(Typeface.DEFAULT_BOLD);
            row.addView(matchTv);

            TextView timeTv = new TextView(this);
            timeTv.setText(m.kickoffWibFormatted);
            timeTv.setTextColor(0xEEF1F5F9);
            timeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            row.addView(timeTv);

            container.addView(row);
        }

        scroll.addView(container);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setView(scroll)
                .setPositiveButton("Filter (Live / Hasil FT / Jadwal)", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showFootballCategoryFilterDialog();
                    }
                })
                .setNeutralButton("Refresh LiveScore.mobi", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        syncFootballMatchesAsync(true);
                    }
                })
                .setNegativeButton("Tutup", null);
        showTranslucentDialog(builder);
    }

    private void showFootballCategoryFilterDialog() {
        final String[] names = new String[]{
                "Semua Pertandingan (Live + Hasil FT + Jadwal)",
                "Hanya Pertandingan Sedang Berlangsung (LIVE)",
                "Hanya Hasil Pertandingan Selesai (FT)",
                "Hanya Jadwal Pertandingan Mendatang (Fixtures)"
        };
        final String[] slugs = new String[]{
                FootballScheduleManager.FILTER_ALL,
                FootballScheduleManager.FILTER_LIVE,
                FootballScheduleManager.FILTER_RESULT,
                FootballScheduleManager.FILTER_UPCOMING
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Filter Tampilan LiveScore.mobi")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        config.footballFilterSlug = slugs[which];
                        Config.saveUserStringPref(MainActivity.this, "pref_football_filter", config.footballFilterSlug);
                        footballManager.setFilterSlug(config.footballFilterSlug);
                        rotateFootballMatchCard();
                        showFootballScheduleDialog();
                    }
                });
        showTranslucentDialog(builder);
    }

    // =========================================================================
    // 9. PENGATURAN LAUNCHER LENGKAP (SETTINGS TRANSPARAN)
    // =========================================================================

    private void showLauncherSettingsDialog() {
        final String[] items = new String[]{
                "1. Edit Running Text / Teks Berjalan (" + (config.showRunningText ? "AKTIF" : "SEMBUNYI") + ")",
                "2. Pengaturan Screen Saver (" + (config.screensaverEnabled ? ("AKTIF - " + (config.screensaverTimeoutSec / 60) + " mnt") : "MATI") + ")",
                "3. Jalankan Crb Launcher Saat TV Power On: " + (config.autoStartOnPowerOn ? "AKTIF (ON)" : "NONAKTIF (OFF)"),
                "4. Pilih / Kelola Aplikasi Favorit di Menu Kiri (" + config.favoriteApps.size() + " App)",
                "5. Ubah Background & Rolling Background (" + (config.rollingBackgroundEnabled ? "Rolling ON" : "Rolling OFF") + ")",
                "6. Pengaturan Suara Adzan Muadzin (" + (config.enableAdzanSound ? "AKTIF" : "NONAKTIF") + ") & Tes Adzan",
                "7. Cuaca Kelurahan Karya Mulya, Kota Cirebon (" + (config.showWeatherKaryaMulya ? "TAMPIL" : "SEMBUNYI") + ")",
                "8. Jumlah Kolom Menu Favorit Kiri: " + config.columns + " Kolom (Klik untuk Ubah 1 / 2)",
                "9. Statistik RAM & Storage di Layar Utama: " + (config.showStats ? "AKTIF" : "NONAKTIF"),
                "10. Jadikan Crb Launcher sebagai Default Home TV / Pengaturan Android"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Pengaturan Crb Launcher (com.crb.tv)")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                showEditRunningTextDialog();
                                break;
                            case 1:
                                showScreensaverSettingsDialog();
                                break;
                            case 2:
                                config.autoStartOnPowerOn = !config.autoStartOnPowerOn;
                                Config.saveUserPref(MainActivity.this, "pref_auto_start_power_on", config.autoStartOnPowerOn);
                                Toast.makeText(
                                        MainActivity.this,
                                        "Auto-Start saat Power On: " + (config.autoStartOnPowerOn ? "AKTIF" : "NONAKTIF"),
                                        Toast.LENGTH_SHORT
                                ).show();
                                showLauncherSettingsDialog();
                                break;
                            case 3:
                                showManageFavoritesDialog();
                                break;
                            case 4:
                                showBackgroundPickerDialog();
                                break;
                            case 5:
                                showAdzanAndLocationSettingsDialog();
                                break;
                            case 6:
                                showKaryaMulyaWeatherDialog();
                                break;
                            case 7:
                                config.columns = (config.columns == 1) ? 2 : 1;
                                Config.saveUserIntPref(MainActivity.this, "pref_columns_left", config.columns);
                                gridLayoutManager.setSpanCount(config.columns);
                                setupSidebarLayoutAndStyle();
                                appAdapter.notifyDataSetChanged();
                                break;
                            case 8:
                                config.showStats = !config.showStats;
                                Config.saveUserPref(MainActivity.this, "pref_show_stats", config.showStats);
                                statsText.setVisibility(config.showStats ? View.VISIBLE : View.GONE);
                                if (config.showStats) updateSystemStats();
                                break;
                            case 9:
                                openHomeOrAndroidSettings();
                                break;
                        }
                    }
                })
                .setNegativeButton("Tutup", null);
        showTranslucentDialog(builder);
    }

    private void openHomeOrAndroidSettings() {
        try {
            Intent homeSettings = new Intent(Settings.ACTION_HOME_SETTINGS);
            homeSettings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeSettings);
        } catch (Exception e) {
            openAndroidSettings();
        }
    }

    private void openWifiSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            openAndroidSettings();
        }
    }

    private void openAndroidSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }
}
