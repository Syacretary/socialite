import 'package:flutter/material.dart';

class SettingsProvider extends ChangeNotifier {
  ThemeMode _themeMode = ThemeMode.dark; // Default to AMOLED Dark
  String _languageCode = 'id'; // Default to Bahasa Indonesia

  bool _usagePermissionGranted = false;
  bool _notificationPermissionGranted = true;
  bool _overlayPermissionGranted = false;

  ThemeMode get themeMode => _themeMode;
  String get languageCode => _languageCode;

  bool get usagePermissionGranted => _usagePermissionGranted;
  bool get notificationPermissionGranted => _notificationPermissionGranted;
  bool get overlayPermissionGranted => _overlayPermissionGranted;

  void setThemeMode(ThemeMode mode) {
    _themeMode = mode;
    notifyListeners();
  }

  void setLanguage(String code) {
    if (code == 'id' || code == 'en') {
      _languageCode = code;
      notifyListeners();
    }
  }

  void setUsagePermission(bool granted) {
    _usagePermissionGranted = granted;
    notifyListeners();
  }

  void toggleNotificationPermission() {
    _notificationPermissionGranted = !_notificationPermissionGranted;
    notifyListeners();
  }

  void toggleOverlayPermission() {
    _overlayPermissionGranted = !_overlayPermissionGranted;
    notifyListeners();
  }
}
