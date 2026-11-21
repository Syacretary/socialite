# 📱 Socialite - Minimalist Screen Time & Boundary Manager

Aplikasi mobile berbasis **Flutter & Dart** dengan pendekatan **Ultra-Minimalist**, **3-Second Glance Comprehension**, dan estetika **AMOLED Black / Putih Susu (Milky Light)** yang ramah dan nyaman di mata pengguna.

---

## 🌟 Fitur Utama

### 1. 🎯 Manage App (Zona Batas dengan Drag & Drop Interaktif)
- **Interactive Drag & Drop**: Tarik ikon media sosial (Instagram, TikTok, YouTube, dsb.) langsung ke **Zona Dibatasi (Restricted Zone)** untuk membatasi aplikasi.
- **Eject / Drag Out**: Geser kembali ikon dari kotak batasan ke **Zona Aplikasi Bebas** (atau tekan tombol silang cepat) untuk menonaktifkan pembatasan.
- **Haptic & Visual Feedback**: Zona tujuan berpendar halus dengan garis batas rounded squircle responsif saat item diseret mendekat.
- *(Catatan: Pemblokiran tingkat OS / Accessibility Service belum diimplementasikan sesuai instruksi fase awal, berfokus penuh pada kestabilan interaksi UI & state).*

### 2. ⏳ Screentime (Pantau Durasi & Izin Akses)
- **Prinsip 3 Detik Pertama**: Angka total durasi harian berukuran besar dengan typografi geometris modern (*Space Grotesk*) dan badge perbandingan performa (*"-14% vs kemarin"*).
- **Alur Izin Screentime**: Kartu status izin akses penggunaan (*Package Usage Stats*) terintegrasi dengan tombol aksi langsung.
- **Visual Progress Track**: Indikator batas aman harian dengan gradien aksen.
- **Daftar Peringkat Aplikasi**: Bar proporsional warna brand tiap media sosial.
- **Tren 7 Hari**: Mini bar chart 7 hari terakhir yang bersih dan minimalis.

### 3. ⚙️ Settings (Konfigurasi & Preferensi)
- **Language Switcher**: Pilihan cepat dwibahasa antara 🇮🇩 **Bahasa Indonesia** dan 🇬🇧 **English**.
- **Theme Selector**:
  - 🌑 **AMOLED Dark Theme**: `#000000` True AMOLED Black dengan permukaan card `#0F1014`, aksen neon mint `#10E79D`, dan teks putih kontras tinggi.
  - 🥛 **Putih Susu (Milky Light)**: Permukaan lembut `#F7F8FA` dengan card putih murni dan typografi obsidian charcoal `#0F172A`.
- **System Permissions**: Manajemen status izin sistem (Akses Penggunaan Aplikasi, Notifikasi & Pengingat, dan Tampilan di Atas Aplikasi).
- **About**: Informasi versi dan filosofi produk.

---

## 🎨 Desain & Tipografi Anti-Mainstream

- **Font Kombinasi**:
  - `Space Grotesk`: Header tebal, punchy numerals, label metrik, dan navigasi.
  - `Plus Jakarta Sans`: Body copy yang rapi, ergonomis, dan ramah di mata.
- **Bentuk & Layout**:
  - Rounded rectangle / Squircle (`borderRadius: 20` - `32`) untuk kesan modern dan ramah.
  - Floating bottom navigation capsule dengan efek *frosted glass blur*.
  - Tanpa *wall of text*: setiap informasi disajikan dalam bentuk kapsul, meter, dan ikon yang mudah dipahami dalam 3 detik.

---

## 📁 Struktur Kode

```
Socialite/
├── android/
│   └── app/src/main/AndroidManifest.xml  # Izin PACKAGE_USAGE_STATS & Notifications
├── lib/
│   ├── core/
│   │   ├── localization/
│   │   │   └── app_localizations.dart    # Kamus terjemahan ID & EN
│   │   └── theme/
│   │       └── app_theme.dart            # Konfigurasi AMOLED Dark & Putih Susu
│   ├── models/
│   │   ├── app_item.dart                 # Model data aplikasi & durasi
│   │   └── screentime_data.dart          # Model statistik harian/mingguan
│   ├── providers/
│   │   ├── app_management_provider.dart  # State drag-and-drop & status batasan
│   │   ├── screentime_provider.dart      # Kalkulasi screentime & alur perizinan
│   │   └── settings_provider.dart        # State tema, bahasa, dan toggle izin
│   ├── views/
│   │   ├── manage_app/
│   │   │   └── manage_app_screen.dart    # UI drag and drop zona aplikasi
│   │   ├── screentime/
│   │   │   └── screentime_screen.dart    # Dashboard durasi & izin
│   │   ├── settings/
│   │   │   └── settings_screen.dart      # Pengaturan bahasa, tema, dan izin
│   │   ├── widgets/
│   │   │   └── custom_app_bar.dart       # Header minimalis modern
│   │   └── main_scaffold.dart            # Floating capsule bottom navigation
│   └── main.dart                         # Entry point & MultiProvider setup
├── analysis_options.yaml
└── pubspec.yaml
```

---

## 🚀 Cara Menjalankan Aplikasi

1. Pastikan Flutter SDK terpasang di sistem.
2. Unduh paket dependensi:
   ```bash
   flutter pub get
   ```
3. Jalankan aplikasi pada emulator atau perangkat fisik (Android / iOS / Web / Linux):
   ```bash
   flutter run
   ```
