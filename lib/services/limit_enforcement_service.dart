import 'dart:async';
import 'package:flutter/material.dart';
import '../models/app_item.dart';
import '../providers/app_management_provider.dart';

/// Simulates real-time usage tracking and enforces daily time limits.
/// In production this would hook into platform UsageStatsManager / AAAS.
/// For the demo, we tick every minute, increment usageMinutesToday for
/// restricted apps, and fire the configured action when the limit is reached.
class LimitEnforcementService {
  LimitEnforcementService._();
  static final LimitEnforcementService instance = LimitEnforcementService._();

  Timer? _ticker;
  final Set<String> _triggeredToday = {};

  /// Start the per-minute simulation tick.
  void start(AppManagementProvider provider, BuildContext context) {
    _ticker?.cancel();
    _ticker = Timer.periodic(const Duration(minutes: 1), (_) {
      _tick(provider, context);
    });
  }

  void stop() {
    _ticker?.cancel();
    _ticker = null;
  }

  void resetDailyTriggered() => _triggeredToday.clear();

  void _tick(AppManagementProvider provider, BuildContext context) {
    for (final app in provider.restrictedApps) {
      // Increment simulated usage by 1 minute
      provider.incrementUsage(app.id);

      final updated = provider.getAppById(app.id);
      if (updated == null) continue;

      // Check if limit just reached and not already fired today
      if (updated.usageMinutesToday >= updated.dailyLimitMinutes &&
          !_triggeredToday.contains(app.id)) {
        _triggeredToday.add(app.id);
        _fireAction(updated, context);
      }
    }
  }

  void _fireAction(AppItem app, BuildContext context) {
    if (!context.mounted) return;

    switch (app.actionOnLimit) {
      case LimitAction.notifyOnly:
        _showSnackbar(context, app);
        break;
      case LimitAction.popupReminder:
        _showPopup(context, app);
        break;
      case LimitAction.blockAndClose:
        _showBlockedDialog(context, app);
        break;
    }
  }

  void _showSnackbar(BuildContext context, AppItem app) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        behavior: SnackBarBehavior.floating,
        margin: const EdgeInsets.fromLTRB(16, 0, 16, 90),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        backgroundColor: isDark ? const Color(0xFF1A1A1A) : const Color(0xFF1A1A1A),
        duration: const Duration(seconds: 5),
        content: Row(
          children: [
            SizedBox(width: 24, height: 24, child: app.buildLogo(size: 22, isDark: true)),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    '${app.name} — Batas Tercapai',
                    style: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w800,
                      color: Colors.white,
                    ),
                  ),
                  Text(
                    'Kamu telah menggunakan ${app.formattedLimit} hari ini.',
                    style: const TextStyle(
                      fontSize: 10,
                      color: Color(0xFFAAAAAA),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _showPopup(BuildContext context, AppItem app) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final primaryColor = isDark ? Colors.white : Colors.black;

    showDialog(
      context: context,
      barrierDismissible: true,
      barrierColor: Colors.black.withValues(alpha: 0.6),
      builder: (ctx) => Dialog(
        backgroundColor: Colors.transparent,
        insetPadding: const EdgeInsets.symmetric(horizontal: 28, vertical: 40),
        child: Container(
          padding: const EdgeInsets.all(24),
          decoration: BoxDecoration(
            color: isDark ? const Color(0xFF0D0D0D) : const Color(0xFFFFFFFF),
            borderRadius: BorderRadius.circular(28),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              SizedBox(width: 48, height: 48, child: app.buildLogo(size: 44, isDark: isDark)),
              const SizedBox(height: 16),
              Text(
                'Waktumu untuk ${app.name}\nsudah habis',
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w900,
                  letterSpacing: -0.5,
                  height: 1.2,
                  color: primaryColor,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'Kamu telah menggunakan ${app.formattedLimit} hari ini.\nIstirahatlah sejenak dan kembali besok.',
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w500,
                  height: 1.5,
                  color: isDark ? const Color(0xFF888888) : const Color(0xFF777777),
                ),
              ),
              const SizedBox(height: 20),
              Row(
                children: [
                  Expanded(
                    child: GestureDetector(
                      onTap: () => Navigator.of(ctx).pop(),
                      child: Container(
                        padding: const EdgeInsets.symmetric(vertical: 13),
                        decoration: BoxDecoration(
                          color: isDark ? const Color(0xFF1A1A1A) : const Color(0xFFF3F4F6),
                          borderRadius: BorderRadius.circular(16),
                        ),
                        child: Center(
                          child: Text(
                            'Tutup',
                            style: TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w700,
                              color: primaryColor,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: GestureDetector(
                      onTap: () => Navigator.of(ctx).pop(),
                      child: Container(
                        padding: const EdgeInsets.symmetric(vertical: 13),
                        decoration: BoxDecoration(
                          color: primaryColor,
                          borderRadius: BorderRadius.circular(16),
                        ),
                        child: Center(
                          child: Text(
                            'Paham',
                            style: TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w800,
                              color: isDark ? Colors.black : Colors.white,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showBlockedDialog(BuildContext context, AppItem app) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final primaryColor = isDark ? Colors.white : Colors.black;

    showDialog(
      context: context,
      barrierDismissible: false,
      barrierColor: Colors.black.withValues(alpha: 0.85),
      builder: (ctx) => PopScope(
        canPop: false,
        child: Dialog(
          backgroundColor: Colors.transparent,
          insetPadding: const EdgeInsets.symmetric(horizontal: 28, vertical: 40),
          child: Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF0D0D0D) : const Color(0xFFFFFFFF),
              borderRadius: BorderRadius.circular(28),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  Icons.block_rounded,
                  size: 36,
                  color: primaryColor,
                ),
                const SizedBox(height: 14),
                Text(
                  '${app.name} Diblokir',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w900,
                    letterSpacing: -0.5,
                    color: primaryColor,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  'Batas harian ${app.formattedLimit} telah tercapai.\nAkses dikunci hingga hari berikutnya.',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w500,
                    height: 1.5,
                    color: isDark ? const Color(0xFF888888) : const Color(0xFF777777),
                  ),
                ),
                const SizedBox(height: 20),
                GestureDetector(
                  onTap: () => Navigator.of(ctx).pop(),
                  child: Container(
                    width: double.infinity,
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    decoration: BoxDecoration(
                      color: isDark ? const Color(0xFF1A1A1A) : const Color(0xFFF3F4F6),
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Center(
                      child: Text(
                        'Kembali ke Lifelit',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w700,
                          color: primaryColor,
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
