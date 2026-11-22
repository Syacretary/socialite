import 'package:flutter/material.dart';
import '../providers/app_management_provider.dart';

/// Real time-limit enforcement is handled natively by
/// `LifelitAccessibilityService` using UsageStatsManager + a live session clock.
/// This service exists only as a placeholder so the method-channel contract
/// stays compatible; it does not maintain its own usage clock or fire
/// duplicate in-app interventions.
class LimitEnforcementService {
  LimitEnforcementService._();
  static final LimitEnforcementService instance = LimitEnforcementService._();

  /// Start the per-minute simulation tick.
  void start(AppManagementProvider provider, BuildContext context) {
    // No-op: the native AccessibilityService owns enforcement.
  }

  void stop() {
    // No-op.
  }

  void resetDailyTriggered() {}

}
