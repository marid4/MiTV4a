# Crb Launcher (`com.crb.tv`)

**Crb Launcher** adalah Android TV & Google TV Launcher ringan, cepat, dan hemat RAM yang dimodifikasi dari basis proyek open-source [CleanYourTV oleh Gathin23](https://github.com/Gathin23/cleanyourtv/tree/main), dirancang khusus untuk wilayah **Kota Cirebon (Kelurahan Karya Mulya, Kecamatan Kesambi & sekitarnya)** dengan antarmuka kaca semi-transparan (*translucent glass UI*).

---

## Informasi Proyek & Identitas APK

| Atribut | Detail |
| :--- | :--- |
| **Nama Aplikasi** | `Crb Launcher` |
| **Nama Proyek AndroidIDE** | `CrbLauncher` |
| **Package Name / Application ID** | `com.crb.tv` |
| **Ikon APK & Banner TV** | Logo kustom `ClTv` (`drawable/ic_launcher.png`, `mipmap-*/ic_launcher.png`, `drawable/ic_banner.png`) |
| **Minimum SDK / Target SDK** | Android 8.0 (API 26) / Android 14 (API 34) |
| **Kompatibilitas Build** | AndroidIDE di HP (Gradle 7.4.2+ / AGP 7.3.1), Termux, & GitHub Actions |

---

## Fitur Utama Crb Launcher

### 1. Jadwal Sholat Kota Cirebon & Suara Adzan Muadzin Otomatis
* **Standar Kemenag RI (MABIMS)**: Menghitung waktu **Imsak, Subuh, Terbit, Dhuha, Dzuhur, Ashar, Maghrib, dan Isya** secara akurat untuk koordinat **Kelurahan Karya Mulya, Kec. Kesambi, Kota Cirebon (`-6.7358, 108.5385`)** maupun wilayah Cirebon lainnya (Kejaksan, Harjamukti, Pekalipan, Lemahwungkuk, Sumber, Indramayu, Kuningan, Majalengka) baik secara *offline* maupun tersinkronisasi dengan [AlAdhan Kemenag RI API (Method 20)](https://aladhan.com/prayer-times-api).
* **Suara Adzan Otomatis Saat Waktu Sholat Tiba**: Memantau waktu sholat setiap detik dan otomatis memutar rekaman **Suara Muadzin Asli berkualitas studio (`.mp3`)** dari [AlAdhan Audio CDN](https://community.islamic.network/d/326-permission-to-bundle-four-adhan-recordings-in-a-free-app-quran-azkar) yang diunduh & disimpan ke memori internal TV (*offline cache*):
  1. **Syaikh Mishary Rashid Alafasy**
  2. **Syaikh Ahmad Al-Nafees**
  3. **Adzan Masjidil Haram Makkah Al-Mukarramah**
  4. **Syaikh Mansour Al-Zahrani**
  5. **Syaikh Hafiz Mustafa Ozcan**
* Mendukung pemutaran file kustom `adzan.mp3` dari penyimpanan internal (`Download/adzan.mp3`) serta cadangan *offline* `res/raw/adzan_chime.mp3` + suara vokal `TextToSpeech` Android.

### 2. Cuaca Real-Time Wilayah Karya Mulya, Kota Cirebon
* Menampilkan suhu udara (`C`), kondisi langit dalam Bahasa Indonesia (*Cerah*, *Cerah Berawan*, *Berawan*, *Hujan Ringan*, *Hujan Petir*), kelembaban (`%`), dan kecepatan angin (`km/jam`) khusus wilayah **Kelurahan Karya Mulya, Kec. Kesambi, Kota Cirebon** melalui [Open-Meteo Weather API](https://open-meteo.com/) tepat di bawah jam utama.

### 3. Jadwal & Skor Sepak Bola Real dari LiveScore.mobi
* Mengambil data pertandingan sepak bola secara *real-time* dari [LiveScore.mobi](https://www.livescore.mobi/) dan [LiveScore Live Football](https://www.livescore.mobi/football/live).
* Menampilkan tiga kategori pertandingan lengkap dengan lencana warna:
  * **`LIVE`** (Pertandingan sedang berlangsung beserta menit berjalan / `HT` dan skor langsung)
  * **`HASIL FT`** (Hasil akhir pertandingan yang telah selesai)
  * **`JADWAL`** (Jadwal *kickoff* pertandingan mendatang dalam zona waktu `WIB`)

### 4. Tata Letak Kaca Transparan & Menu App Favorit di Kiri Bawah Jam
* **Card di Atas Layar**: Kartu Jadwal Sepak Bola (`livescore.mobi`) dan Kartu Jadwal Sholat Cirebon ditempatkan di **Dashboard Bar Atas Layar** dengan latar kaca semi-transparan (`~24% alpha`) sehingga gambar/video wallpaper utama terbuka penuh.
* **Menu Grid Kiri Khusus App Favorit**: Sidebar kiri berada tepat di bawah baris jam (tidak pernah menutupi jam) dan hanya menampilkan aplikasi favorit pilihan Anda ditambah tombol **`+ Favorit`** (untuk memilih/mencentang aplikasi favorit) dan **`Semua App`**.
* **Kotak Dialog Transparan dengan Teks Tajam**: Seluruh dialog (*Settings*, *Jadwal Sholat*, *Background*, *LiveScore*, *Cuaca*, *Edit Running Text*, *Screen Saver*) menggunakan jendela kaca semi-transparan (`showTranslucentDialog`) dengan bayangan teks kontras agar wallpaper tetap terlihat di belakang dialog.

### 5. Ganti Background & Rolling Background (`.jpg`, `.png`, `.gif`, `.mp4`)
* **4 Format Background**: Mendukung foto statis (`.jpg`, `.png`), animasi bergerak (`.gif` via `AnimatedGifView`), dan video berulang tanpa suara (`.mp4` via `VideoView`).
* **Transfer Langsung dari Browser HP (Wi-Fi Port `8888`)**: Buka `http://<IP_TV>:8888` dari browser HP untuk mengirim satu atau banyak file gambar/GIF/MP4 sekaligus ke TV tanpa kabel.
* **Rolling Background (Slideshow Otomatis)**: Mengganti background secara otomatis berdasarkan interval waktu pilihan (**10 Detik**, **15 Detik**, **30 Detik**, **1 Menit**, **3 Menit**, **5 Menit**, hingga **15 Menit**) dari koleksi file yang dikirim lewat HP atau folder memori internal.

### 6. Running Text (Teks Berjalan 60fps) & Screen Saver Anti Burn-In
* **Running Text yang Dapat Diedit di Setting (`RunningTextView.java`)**: Teks berjalan mulus 60fps di bagian bawah layar yang tidak pernah berhenti saat fokus remote berpindah. Isi pesan, kecepatan gerak (*Lambat*, *Normal*, *Cepat*), serta info tambahan waktu sholat/cuaca dapat diedit langsung melalui **Settings -> 1. Edit Running Text / Teks Berjalan** atau dengan mengeklik baris Running Text.
* **Screen Saver Otomatis & Manual**: Menampilkan jam digital besar, tanggal Masehi & Hijriyah, hitung mundur waktu sholat, dan cuaca Karya Mulya yang bergerak perlahan (anti *burn-in*) setelah TV tidak aktif selama waktu tunggu yang dipilih.

### 7. Auto-Start Saat TV Power On (`BootStartReceiver.java`)
* Menjalankan **Crb Launcher** secara otomatis ketika TV dinyalakan (*Cold Boot* / *QuickBoot* / *Power On*) dengan prioritas penerima `BOOT_COMPLETED` tertinggi (`999`) dan prioritas `HOME` (`1000`).

---

## Struktur Direktori Proyek (`CrbLauncher`)

```text
CrbLauncher/
|-- build.gradle                          # Konfigurasi plugin Gradle (AGP 7.3.1 untuk Gradle 7.4.2+)
|-- settings.gradle                       # rootProject.name = "CrbLauncher"
|-- gradle.properties                     # Konfigurasi JVM & AndroidX
|-- app/
    |-- build.gradle                      # namespace & applicationId "com.crb.tv" + auto-clean duplikat
    |-- proguard-rules.pro                # Aturan ProGuard untuk com.crb.tv
    |-- src/main/
        |-- AndroidManifest.xml           # Manifest utama Crb Launcher & BootStartReceiver
        |-- java/com/crb/tv/
        |   |-- MainActivity.java         # Activity utama, navigasi remote, & dialog transparan
        |   |-- Config.java               # Penyimpanan preferensi pengguna (SharedPreferences)
        |   |-- AppAdapter.java           # Adapter menu grid kiri khusus App Favorit
        |   |-- PrayerTimesCalculator.java# Rumus astronomi Kemenag RI + sinkronisasi AlAdhan
        |   |-- AdzanAudioPlayer.java     # Pemutar & pengunduh rekaman suara Muadzin asli (.mp3)
        |   |-- WeatherManager.java       # Pengelola cuaca real-time Karya Mulya, Kota Cirebon
        |   |-- FootballScheduleManager.java # Pengambil jadwal Live, Hasil FT & Fixtures livescore.mobi
        |   |-- RunningTextView.java      # Custom view teks berjalan (marquee) 60fps
        |   |-- BootStartReceiver.java    # Auto-start Crb Launcher saat TV Power On
        |   |-- WallpaperTransferServer.java # Server HTTP Wi-Fi port 8888 untuk transfer dari HP
        |   |-- AnimatedGifView.java      # Pemutar background animasi .gif hemat memori
        |   |-- Sky.java                  # Generator 6 tema background artistik bawaan
        |   |-- GlassView.java            # Efek kaca semi-transparan pada sidebar favorit
        |   |-- ClockDream.java           # Layanan Daydream Screen Saver sistem Android TV
        |   |-- HomeKeyService.java       # Layanan aksesibilitas tombol Home remote TV
        |   |-- InputKeyService.java      # Layanan aksesibilitas tombol Input remote TV
        |   |-- ThemeProvider.java        # ContentProvider konfigurasi tema
        |-- res/
            |-- drawable/                 # ic_launcher.png (ClTv), ic_banner.png, & background XML
            |-- mipmap-*/                 # Ikon APK ClTv (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)
            |-- raw/adzan_chime.mp3       # Audio cadangan offline 44.1kHz
            |-- layout/activity_main.xml  # Layout utama Crb Launcher
            |-- layout/item_app.xml       # Layout kartu ikon aplikasi favorit
            |-- values/strings.xml        # Nama aplikasi "Crb Launcher"
            |-- values/config.xml         # Nilai default konfigurasi
            |-- values/styles.xml         # Tema utama aplikasi
```

---

## Cara Build APK di HP (Menggunakan AndroidIDE)

1. Ekstrak **`CrbLauncher.zip`** ke dalam folder `/storage/emulated/0/AndroidIDEProjects/CrbLauncher`.
2. Buka aplikasi **AndroidIDE** di HP Anda, lalu pilih proyek **`CrbLauncher`**.
3. Jalankan task build **`:app:assembleDebug`**.
4. File APK siap diinstal dapat diambil di:
   ```text
   /storage/emulated/0/AndroidIDEProjects/CrbLauncher/app/build/outputs/apk/debug/app-debug.apk
   ```

---

## Tombol Pintas Remote TV

* **Arah Panah (Atas / Bawah / Kiri / Kanan)**: Navigasi antar-ikon di Menu Favorit Kiri, Baris Tombol Atas, Kartu LiveScore.mobi, dan Kartu Jadwal Sholat Cirebon.
* **OK / Enter**: Membuka aplikasi favorit atau membuka detail kartu/pengaturan.
* **Tahan OK (Long Press) pada Ikon Aplikasi**: Menambah/menghapus aplikasi dari Menu Favorit atau menggeser urutan ikon.
* **Tombol Menu / Settings pada Remote**: Membuka dialog **Pengaturan Crb Launcher**.
* **Tombol Warna Remote (Merah / Hijau / Kuning / Biru)**:
  * **Merah (`F1`)**: Pengaturan Background & Rolling Background
  * **Hijau (`F2`)**: Detail Jadwal Sholat Cirebon & Suara Adzan
  * **Kuning (`F3`)**: Detail Jadwal & Hasil Sepak Bola LiveScore.mobi
  * **Biru (`F4`)**: Edit Running Text (Teks Berjalan)
