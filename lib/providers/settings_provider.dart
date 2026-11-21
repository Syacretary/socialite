import 'package:flutter/material.dart';
import '../services/feature_block_service.dart';

class SettingsProvider extends ChangeNotifier {
  ThemeMode _themeMode = ThemeMode.dark; // Default to AMOLED Dark
  String _languageCode = 'id'; // Default to Bahasa Indonesia

  bool _usagePermissionGranted = false;
  bool _notificationPermissionGranted = true;
  bool _overlayPermissionGranted = false;
  bool _accessibilityPermissionGranted = false;

  ThemeMode get themeMode => _themeMode;
  String get languageCode => _languageCode;

  bool get usagePermissionGranted => _usagePermissionGranted;
  bool get notificationPermissionGranted => _notificationPermissionGranted;
  bool get overlayPermissionGranted => _overlayPermissionGranted;
  bool get accessibilityPermissionGranted => _accessibilityPermissionGranted;

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

  void setAccessibilityPermission(bool granted) {
    _accessibilityPermissionGranted = granted;
    notifyListeners();
  }

  /// Live check permissions from Android OS
  Future<void> refreshPermissions() async {
    final usage = await FeatureBlockService.isUsageStatsGranted();
    final overlay = await FeatureBlockService.isOverlayGranted();
    final accessibility = await FeatureBlockService.isAccessibilityGranted();

    _usagePermissionGranted = usage;
    _overlayPermissionGranted = overlay;
    _accessibilityPermissionGranted = accessibility;
    notifyListeners();
  }

  Future<void> requestAccessibilityPermission() async {
    await FeatureBlockService.openAccessibilitySettings();
  }

  Future<void> requestOverlayPermission() async {
    await FeatureBlockService.openOverlaySettings();
  }

  Future<void> requestUsagePermission() async {
    await FeatureBlockService.openUsageStatsSettings();
  }
}
