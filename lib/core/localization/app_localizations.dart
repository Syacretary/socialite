import 'package:flutter/material.dart';

class AppLocalizations {
  final String languageCode;

  AppLocalizations(this.languageCode);

  static AppLocalizations of(BuildContext context) {
    return AppLocalizations('id');
  }

  static const Map<String, Map<String, String>> _localizedValues = {
    'id': {
      // General
      'appName': 'Socialite',
      'tagline': 'Batas Sehat Digital',

      // Navigation
      'navManage': 'Atur App',
      'navScreentime': 'Screentime',
      'navSettings': 'Setelan',

      // Manage App Screen
      'manageTitle': 'Zona Batas',
      'manageSubtitle': 'Tarik icon untuk batasi atau bebaskan',
      'restrictedZoneTitle': 'ZONA DIBATASI',
      'restrictedZoneSubtitle': 'Lepaskan app di sini untuk aktivasi batasan',
      'restrictedZoneEmpty': 'Belum ada app dibatasi.\nTarik icon sosial mediamu ke sini!',
      'availableZoneTitle': 'DAFTAR APLIKASI',
      'availableZoneSubtitle': 'Sentuh & geser ikon ke zona di atas',
      'appsCount': 'app',
      'dragHint': 'Geser kembali ke sini untuk membuka pembatasan',
      'activeRestrictionCount': 'Aplikasi Terkunci',
      'unrestrictedBadge': 'Bebas Akses',
      'tapToEject': 'Keluarkan',

      // Screentime Screen
      'screentimeTitle': 'Screentime',
      'screentimeSubtitle': 'Pantau durasi penggunaan harian',
      'todayUsage': 'TOTAL HARI INI',
      'dailyGoal': 'Batas harian: 5j',
      'usageComparison': '-14% vs kemarin',
      'permissionCardTitle': 'Izin Akses Screentime',
      'permissionCardSubtitle': 'Aktifkan akses penggunaan aplikasi agar statistik akurat real-time.',
      'grantPermission': 'Beri Izin',
      'permissionGranted': 'Izin Aktif',
      'topUsedApps': 'PENGGUNAAN TERTINGGI',
      'weeklyOverview': 'TREN 7 HARI',
      'hourShort': 'j',
      'minuteShort': 'm',

      // Settings Screen
      'settingsTitle': 'Setelan',
      'settingsSubtitle': 'Konfigurasi preferensi & sistem',
      'languageSection': 'BAHASA',
      'languageDesc': 'Pilih bahasa tampilan antarmuka',
      'themeSection': 'TEMA TAMPILAN',
      'themeDesc': 'Hitam pekat AMOLED & Putih Susu lembut',
      'themeAmoled': 'AMOLED Dark',
      'themeMilk': 'Putih Susu',
      'themeSystem': 'Sistem',
      'permissionsSection': 'IZIN SISTEM',
      'permissionsDesc': 'Kelola otorisasi akses perangkat',
      'permissionUsage': 'Akses Penggunaan Aplikasi',
      'permissionUsageDesc': 'Membaca riwayat durasi tiap app',
      'permissionNotification': 'Notifikasi & Pengingat',
      'permissionNotificationDesc': 'Alarm peringatan saat batas terlewati',
      'permissionOverlay': 'Tampilan di Atas Aplikasi',
      'permissionOverlayDesc': 'Untuk banner pengingat visual',
      'statusActive': 'Aktif',
      'statusInactive': 'Nonaktif',
      'aboutSection': 'TENTANG',
      'version': 'Versi 1.0.0 (Build Alpha)',
      'aboutDesc': 'Didesain minimalis untuk kesadaran digital tanpa kebisingan.',
    },
    'en': {
      // General
      'appName': 'Socialite',
      'tagline': 'Digital Wellness Minimalist',

      // Navigation
      'navManage': 'Manage',
      'navScreentime': 'Screentime',
      'navSettings': 'Settings',

      // Manage App Screen
      'manageTitle': 'Boundary Zone',
      'manageSubtitle': 'Drag icons to restrict or set free',
      'restrictedZoneTitle': 'RESTRICTED ZONE',
      'restrictedZoneSubtitle': 'Drop apps here to engage restriction',
      'restrictedZoneEmpty': 'No restricted apps yet.\nDrag your social icons here!',
      'availableZoneTitle': 'AVAILABLE APPS',
      'availableZoneSubtitle': 'Hold & drag icon up to restrict',
      'appsCount': 'apps',
      'dragHint': 'Drag back here to release restrictions',
      'activeRestrictionCount': 'Locked Apps',
      'unrestrictedBadge': 'Unrestricted',
      'tapToEject': 'Eject',

      // Screentime Screen
      'screentimeTitle': 'Screentime',
      'screentimeSubtitle': 'Monitor your daily digital footprint',
      'todayUsage': 'TODAY\'S USAGE',
      'dailyGoal': 'Daily ceiling: 5h',
      'usageComparison': '-14% vs yesterday',
      'permissionCardTitle': 'Screentime Access Needed',
      'permissionCardSubtitle': 'Enable Usage Access for exact real-time application insights.',
      'grantPermission': 'Grant Access',
      'permissionGranted': 'Active',
      'topUsedApps': 'TOP APPLICATION BREAKDOWN',
      'weeklyOverview': '7-DAY TREND',
      'hourShort': 'h',
      'minuteShort': 'm',

      // Settings Screen
      'settingsTitle': 'Settings',
      'settingsSubtitle': 'Configure preferences & telemetry',
      'languageSection': 'LANGUAGE',
      'languageDesc': 'Select your preferred interface language',
      'themeSection': 'VISUAL THEME',
      'themeDesc': 'True AMOLED Deep Black & Creamy Milky White',
      'themeAmoled': 'AMOLED Dark',
      'themeMilk': 'Milky Light',
      'themeSystem': 'System',
      'permissionsSection': 'SYSTEM PERMISSIONS',
      'permissionsDesc': 'Manage device hardware & OS authorizations',
      'permissionUsage': 'Usage Stats Access',
      'permissionUsageDesc': 'Tracks app duration and sessions',
      'permissionNotification': 'Notifications & Reminders',
      'permissionNotificationDesc': 'Alerts when time threshold approaches',
      'permissionOverlay': 'Display Over Other Apps',
      'permissionOverlayDesc': 'Used for gentle visual break nudges',
      'statusActive': 'Active',
      'statusInactive': 'Disabled',
      'aboutSection': 'ABOUT',
      'version': 'Version 1.0.0 (Alpha Build)',
      'aboutDesc': 'Crafted with zero bloat for quiet digital clarity.',
    },
  };

  String get(String key) {
    return _localizedValues[languageCode]?[key] ?? _localizedValues['id']?[key] ?? key;
  }
}
