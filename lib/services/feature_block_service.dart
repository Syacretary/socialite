import 'package:flutter/services.dart';
import '../providers/app_management_provider.dart';

/// Bridge between Flutter UI and Android Native Accessibility Service
/// for real-time feature block detection and execution.
class FeatureBlockService {
  static const MethodChannel _channel = MethodChannel('com.syacretary.lifelit/features');

  static Future<bool> isAccessibilityGranted() async {
    try {
      final bool? granted = await _channel.invokeMethod<bool>('isAccessibilityGranted');
      return granted ?? false;
    } catch (_) {
      return false;
    }
  }

  static Future<void> openAccessibilitySettings() async {
    try {
      await _channel.invokeMethod('openAccessibilitySettings');
    } catch (_) {}
  }

  static Future<bool> isOverlayGranted() async {
    try {
      final bool? granted = await _channel.invokeMethod<bool>('isOverlayGranted');
      return granted ?? false;
    } catch (_) {
      return false;
    }
  }

  static Future<void> openOverlaySettings() async {
    try {
      await _channel.invokeMethod('openOverlaySettings');
    } catch (_) {}
  }

  static Future<bool> isUsageStatsGranted() async {
    try {
      final bool? granted = await _channel.invokeMethod<bool>('isUsageStatsGranted');
      return granted ?? false;
    } catch (_) {
      return false;
    }
  }

  static Future<void> openUsageStatsSettings() async {
    try {
      await _channel.invokeMethod('openUsageStatsSettings');
    } catch (_) {}
  }

  /// Syncs currently configured feature blocks from Flutter to Android AccessibilityService
  static Future<void> syncRules(AppManagementProvider provider) async {
    try {
      final Map<String, List<String>> rulesMap = {};

      for (final app in provider.allApps) {
        if (!app.hasFeatureBlocks) continue;

        final blockedFeatureIds = app.featureBlocks
            .where((feat) => feat.isBlocked)
            .map((feat) => feat.id)
            .toList();

        if (blockedFeatureIds.isNotEmpty) {
          rulesMap[app.id] = blockedFeatureIds;
        }
      }

      await _channel.invokeMethod('syncFeatureRules', rulesMap);
    } catch (_) {}
  }

  /// Returns only the package names from [candidatePackages] that are
  /// actually installed on the user's device.
  static Future<List<String>> getInstalledPackages(List<String> candidatePackages) async {
    try {
      final result = await _channel.invokeMethod<List<dynamic>>(
        'getInstalledPackages',
        {'packages': candidatePackages},
      );
      if (result != null) {
        return result.cast<String>();
      }
    } catch (_) {}
    return candidatePackages;
  }
}
