import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../../core/localization/app_localizations.dart';
import '../../providers/app_management_provider.dart';
import '../../providers/screentime_provider.dart';
import '../../providers/settings_provider.dart';
import '../widgets/custom_app_bar.dart';
import '../widgets/smooth_sheet.dart';

class ScreentimeScreen extends StatelessWidget {
  const ScreentimeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final settings = Provider.of<SettingsProvider>(context);
    final loc = AppLocalizations(settings.languageCode);
    final appsProvider = Provider.of<AppManagementProvider>(context);
    final screentimeProvider = Provider.of<ScreentimeProvider>(context);
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final primaryColor = isDark ? const Color(0xFFFFFFFF) : const Color(0xFF000000);

    final totalMinutes = screentimeProvider.calculateTotalUsageMinutes(appsProvider);
    final totalFormatted = screentimeProvider.formatMinutes(
      totalMinutes,
      hourSuffix: loc.get('hourShort'),
      minSuffix: loc.get('minuteShort'),
    );

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: SafeArea(
        bottom: false,
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 680),
            child: Column(
              children: [
                // Clean Header without subtitle and without trailing chips
                CustomHeader(title: loc.get('screentimeTitle')),

                // Feather scroll wrapper ensures content gracefully fades away before header
                Expanded(
                  child: FeatherScrollWrapper(
                    child: SingleChildScrollView(
                      physics: const BouncingScrollPhysics(),
                      padding: const EdgeInsets.fromLTRB(16, 4, 16, 120),
                      child: Column(
                        mainAxisSize: MainAxisSize.min, // Hug content tightly
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          // PERMISSION CARD (If needed)
                          _buildPermissionBanner(context, loc, isDark, screentimeProvider, settings, primaryColor),

                          const SizedBox(height: 10),

                          // HERO STAT CARD (Compact, No Outlines)
                          _buildHeroStatCard(context, loc, isDark, totalFormatted, totalMinutes, primaryColor),

                          const SizedBox(height: 12),

                          // WEEKLY 7-DAY MINI BAR CHART (Compact, No Outlines)
                          _buildWeeklyTrendCard(context, loc, isDark, screentimeProvider, primaryColor),

                          const SizedBox(height: 12),

                          // BREAKDOWN APPS LIST (Authentic App Logos, No Outlines)
                          _buildAppBreakdownList(context, loc, isDark, appsProvider, totalMinutes, primaryColor),
                        ],
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

  Widget _buildPermissionBanner(
    BuildContext context,
    AppLocalizations loc,
    bool isDark,
    ScreentimeProvider screentimeProvider,
    SettingsProvider settings,
    Color primaryColor,
  ) {
    final theme = Theme.of(context);
    final hasPermission = screentimeProvider.hasUsagePermission || settings.usagePermissionGranted;

    if (hasPermission) {
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF0F0F0F) : const Color(0xFFF1F2F4),
          borderRadius: BorderRadius.circular(16),
          // NO OUTLINES
        ),
        child: Row(
          children: [
            Icon(Icons.check_circle_outline_rounded, color: primaryColor, size: 15),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                'Akses Screentime aktif',
                style: theme.textTheme.bodyMedium?.copyWith(
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                  color: primaryColor,
                ),
              ),
            ),
            GestureDetector(
              onTap: () {
                screentimeProvider.toggleUsagePermission();
                settings.setUsagePermission(!hasPermission);
              },
              child: Text(
                'Ubah',
                style: theme.textTheme.labelLarge?.copyWith(
                  fontSize: 10,
                  decoration: TextDecoration.underline,
                ),
              ),
            ),
          ],
        ),
      );
    }

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF141414) : const Color(0xFFEBEBEB),
        borderRadius: BorderRadius.circular(20),
        // NO OUTLINES
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.lock_outline_rounded, color: primaryColor, size: 16),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  loc.get('permissionCardTitle'),
                  style: theme.textTheme.labelLarge?.copyWith(
                    fontSize: 12,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  loc.get('permissionCardSubtitle'),
                  style: theme.textTheme.bodyMedium?.copyWith(fontSize: 10),
                ),
                const SizedBox(height: 8),
                ElevatedButton(
                  onPressed: () {
                    HapticFeedback.mediumImpact();
                    screentimeProvider.grantUsagePermission();
                    settings.setUsagePermission(true);
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: primaryColor,
                    foregroundColor: isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF),
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    elevation: 0,
                  ),
                  child: Text(
                    loc.get('grantPermission'),
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w800,
                      color: isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildHeroStatCard(
    BuildContext context,
    AppLocalizations loc,
    bool isDark,
    String totalFormatted,
    int totalMinutes,
    Color primaryColor,
  ) {
    final theme = Theme.of(context);
    final ratio = (totalMinutes / 360).clamp(0.0, 1.0);

    return Container(
      padding: const EdgeInsets.symmetric(vertical: 18, horizontal: 18),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF0A0A0A) : const Color(0xFFF3F4F6),
        borderRadius: BorderRadius.circular(24),
        // NO OUTLINES
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min, // Hug content
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                loc.get('todayUsage'),
                style: theme.textTheme.labelLarge?.copyWith(
                  fontSize: 10,
                  letterSpacing: 1.5,
                  fontWeight: FontWeight.w800,
                ),
              ),
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.trending_down_rounded, size: 12, color: primaryColor),
                  const SizedBox(width: 3),
                  Text(
                    loc.get('usageComparison'),
                    style: theme.textTheme.labelLarge?.copyWith(
                      fontSize: 10,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 8),

          FittedBox(
            fit: BoxFit.scaleDown,
            alignment: Alignment.centerLeft,
            child: Text(
              totalFormatted,
              style: theme.textTheme.displayLarge?.copyWith(
                fontSize: 48,
                fontWeight: FontWeight.w900,
                letterSpacing: -2.0,
                height: 1.0,
              ),
            ),
          ),
          const SizedBox(height: 14),

          // Track
          ClipRRect(
            borderRadius: BorderRadius.circular(10),
            child: Container(
              height: 5,
              width: double.infinity,
              color: isDark ? const Color(0xFF1E1E1E) : const Color(0xFFE2E4E8),
              child: FractionallySizedBox(
                alignment: Alignment.centerLeft,
                widthFactor: ratio,
                child: Container(color: primaryColor),
              ),
            ),
          ),
          const SizedBox(height: 8),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                loc.get('dailyGoal'),
                style: theme.textTheme.bodyMedium?.copyWith(fontSize: 11),
              ),
              Text(
                '${(ratio * 100).toInt()}% terpakai',
                style: theme.textTheme.bodyMedium?.copyWith(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  color: primaryColor,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildWeeklyTrendCard(
    BuildContext context,
    AppLocalizations loc,
    bool isDark,
    ScreentimeProvider provider,
    Color primaryColor,
  ) {
    final theme = Theme.of(context);
    final days = provider.weeklyTrend;

    return Container(
      padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 16),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF0A0A0A) : const Color(0xFFF3F4F6),
        borderRadius: BorderRadius.circular(22),
        // NO OUTLINES
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min, // Hug content
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                loc.get('weeklyOverview'),
                style: theme.textTheme.labelLarge?.copyWith(
                  fontSize: 10,
                  letterSpacing: 1.2,
                  fontWeight: FontWeight.w800,
                ),
              ),
              Icon(Icons.bar_chart_rounded, size: 14, color: primaryColor),
            ],
          ),
          const SizedBox(height: 14),
          SizedBox(
            height: 75, // Compact vertical size
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              crossAxisAlignment: CrossAxisAlignment.end,
              children: days.asMap().entries.map((entry) {
                final idx = entry.key;
                final day = entry.value;
                final isToday = idx == days.length - 1;

                return Expanded(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      Container(
                        width: 18,
                        height: (50 * day.ratioOfMax).clamp(10.0, 50.0),
                        decoration: BoxDecoration(
                          color: isToday
                              ? primaryColor
                              : (isDark ? const Color(0xFF222222) : const Color(0xFFDDDDDD)),
                          borderRadius: BorderRadius.circular(6),
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        day.dayName,
                        style: theme.textTheme.labelLarge?.copyWith(
                          fontSize: 10,
                          fontWeight: isToday ? FontWeight.w800 : FontWeight.w500,
                          color: isToday
                              ? primaryColor
                              : (isDark ? const Color(0xFF777777) : const Color(0xFF888888)),
                        ),
                      ),
                    ],
                  ),
                );
              }).toList(),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAppBreakdownList(
    BuildContext context,
    AppLocalizations loc,
    bool isDark,
    AppManagementProvider provider,
    int totalMinutes,
    Color primaryColor,
  ) {
    final theme = Theme.of(context);
    final sortedApps = List.of(provider.allApps)
      ..sort((a, b) => b.usageMinutesToday.compareTo(a.usageMinutesToday));

    final maxAppMinutes = sortedApps.first.usageMinutesToday;

    return Container(
      padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 16),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF0A0A0A) : const Color(0xFFF3F4F6),
        borderRadius: BorderRadius.circular(24),
        // NO OUTLINES
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min, // Hug content
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            loc.get('topUsedApps'),
            style: theme.textTheme.labelLarge?.copyWith(
              fontSize: 10,
              letterSpacing: 1.2,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 12),
          ListView.separated(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: sortedApps.length,
            separatorBuilder: (_, __) => const SizedBox(height: 10),
            itemBuilder: (context, index) {
              final app = sortedApps[index];
              final ratio = maxAppMinutes > 0 ? (app.usageMinutesToday / maxAppMinutes) : 0.0;

              return InkWell(
                onTap: () {
                  SmoothAppSheet.show(
                    context: context,
                    app: app,
                    isDark: isDark,
                    onToggleRestriction: () => provider.toggleRestriction(app.id),
                  );
                },
                borderRadius: BorderRadius.circular(14),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 3),
                  child: Row(
                    children: [
                      // Authentic Brand Logo
                      SizedBox(
                        width: 32,
                        height: 32,
                        child: Center(
                          child: app.buildLogo(size: 26),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                Row(
                                  children: [
                                    Text(
                                      app.name,
                                      style: theme.textTheme.labelLarge?.copyWith(
                                        fontSize: 12,
                                        fontWeight: FontWeight.w700,
                                      ),
                                    ),
                                    if (app.isRestricted) ...[
                                      const SizedBox(width: 6),
                                      Container(
                                        padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                                        decoration: BoxDecoration(
                                          color: isDark ? const Color(0xFF222222) : const Color(0xFFE2E4E8),
                                          borderRadius: BorderRadius.circular(6),
                                        ),
                                        child: Text(
                                          'BATAS',
                                          style: TextStyle(
                                            fontSize: 8,
                                            fontWeight: FontWeight.w800,
                                            letterSpacing: 0.5,
                                            color: primaryColor,
                                          ),
                                        ),
                                      ),
                                    ],
                                  ],
                                ),
                                Text(
                                  app.formattedUsage,
                                  style: theme.textTheme.labelLarge?.copyWith(
                                    fontSize: 11,
                                    fontWeight: FontWeight.w800,
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 5),
                            ClipRRect(
                              borderRadius: BorderRadius.circular(4),
                              child: Container(
                                height: 3,
                                width: double.infinity,
                                color: isDark ? const Color(0xFF1E1E1E) : const Color(0xFFE2E4E8),
                                child: FractionallySizedBox(
                                  alignment: Alignment.centerLeft,
                                  widthFactor: ratio,
                                  child: Container(color: primaryColor),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              );
            },
          ),
        ],
      ),
    );
  }
}
