import 'package:flutter/material.dart';
import '../services/feature_block_service.dart';

class SettingsProvider extends ChangeNotifier {
  ThemeMode _themeMode = ThemeMode.dark; // Default to AMOLED Dark
  String _languageCode = 'id'; // Default to Bahasa Indonesia

  bool _usagePermissionGranted = false;
  bool _notificationPermissionGranted = false;
  bool _overlayPermissionGranted = false;
  bool _accessibilityPermissionGranted = false;
  bool _batteryOptimizationIgnored = false;

  ThemeMode get themeMode => _themeMode;
  String get languageCode => _languageCode;

  bool get usagePermissionGranted => _usagePermissionGranted;
  bool get notificationPermissionGranted => _notificationPermissionGranted;
  bool get overlayPermissionGranted => _overlayPermissionGranted;
  bool get accessibilityPermissionGranted => _accessibilityPermissionGranted;
  bool get batteryOptimizationIgnored => _batteryOptimizationIgnored;

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

  /// Request overlay permission from the system, then refresh live state.
  Future<void> toggleOverlayPermission() async {
    await requestOverlayPermission();
    await refreshPermissions();
  }

  /// Notification permission is declared in the manifest; no runtime toggle
  /// exists on Android 13+. Keep state synced with the system on refresh.
  Future<void> toggleNotificationPermission() async {
    await refreshPermissions();
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
    final battery = await FeatureBlockService.isBatteryOptimizationIgnored();

    _usagePermissionGranted = usage;
    _overlayPermissionGranted = overlay;
    _accessibilityPermissionGranted = accessibility;
    _batteryOptimizationIgnored = battery;
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

  Future<void> requestBatteryOptimization() async {
    await FeatureBlockService.requestIgnoreBatteryOptimization();
  }
}
