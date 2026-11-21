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
    final borderColor = isDark ? const Color(0xFF262626) : const Color(0xFFE5E5E5);

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
                CustomHeader(
                  title: loc.get('screentimeTitle'),
                  subtitle: loc.get('screentimeSubtitle'),
                  trailing: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                    decoration: BoxDecoration(
                      color: Colors.transparent, // Invisible
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: borderColor, width: 1.0),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Container(
                          width: 5,
                          height: 5,
                          decoration: BoxDecoration(
                            color: primaryColor,
                            shape: BoxShape.circle,
                          ),
                        ),
                        const SizedBox(width: 6),
                        Text(
                          'Live Tracker',
                          style: theme.textTheme.labelLarge?.copyWith(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),

                Expanded(
                  child: SingleChildScrollView(
                    physics: const BouncingScrollPhysics(),
                    padding: const EdgeInsets.fromLTRB(16, 8, 16, 120),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        // PERMISSION CARD
                        _buildPermissionBanner(context, loc, isDark, screentimeProvider, settings, primaryColor, borderColor),

                        const SizedBox(height: 16),

                        // HERO STAT CARD (Pure Monochrome & Invisible Background)
                        _buildHeroStatCard(context, loc, isDark, totalFormatted, totalMinutes, primaryColor, borderColor),

                        const SizedBox(height: 20),

                        // WEEKLY 7-DAY MINI BAR CHART (Pure Monochrome)
                        _buildWeeklyTrendCard(context, loc, isDark, screentimeProvider, primaryColor, borderColor),

                        const SizedBox(height: 20),

                        // BREAKDOWN APPS LIST
                        _buildAppBreakdownList(context, loc, isDark, appsProvider, totalMinutes, primaryColor, borderColor),
                      ],
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
    Color borderColor,
  ) {
    final theme = Theme.of(context);
    final hasPermission = screentimeProvider.hasUsagePermission || settings.usagePermissionGranted;

    if (hasPermission) {
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
        decoration: BoxDecoration(
          color: Colors.transparent, // Invisible
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: borderColor, width: 1.0),
        ),
        child: Row(
          children: [
            Icon(
              Icons.check_circle_outline_rounded,
              color: primaryColor,
              size: 16,
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                'Akses Screentime aktif',
                style: theme.textTheme.bodyMedium?.copyWith(
                  fontSize: 12,
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
                  fontSize: 11,
                  decoration: TextDecoration.underline,
                ),
              ),
            ),
          ],
        ),
      );
    }

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.transparent, // Invisible
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: primaryColor, width: 1.2),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              border: Border.all(color: primaryColor, width: 1.0),
            ),
            child: Icon(
              Icons.lock_outline_rounded,
              color: primaryColor,
              size: 18,
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  loc.get('permissionCardTitle'),
                  style: theme.textTheme.labelLarge?.copyWith(
                    fontSize: 13,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  loc.get('permissionCardSubtitle'),
                  style: theme.textTheme.bodyMedium?.copyWith(
                    fontSize: 11,
                    height: 1.4,
                  ),
                ),
                const SizedBox(height: 12),
                ElevatedButton(
                  onPressed: () {
                    HapticFeedback.mediumImpact();
                    screentimeProvider.grantUsagePermission();
                    settings.setUsagePermission(true);
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: primaryColor,
                    foregroundColor: isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF),
                    padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 8),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                    elevation: 0,
                  ),
                  child: Text(
                    loc.get('grantPermission'),
                    style: TextStyle(
                      fontSize: 12,
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
    Color borderColor,
  ) {
    final theme = Theme.of(context);
    final ratio = (totalMinutes / 360).clamp(0.0, 1.0); // 6h ceiling baseline

    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: Colors.transparent, // INVISIBLE BACKGROUND
        borderRadius: BorderRadius.circular(30),
        border: Border.all(color: borderColor, width: 1.0),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                loc.get('todayUsage'),
                style: theme.textTheme.labelLarge?.copyWith(
                  fontSize: 11,
                  letterSpacing: 1.5,
                  fontWeight: FontWeight.w800,
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: borderColor, width: 1.0),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      Icons.trending_down_rounded,
                      size: 13,
                      color: primaryColor,
                    ),
                    const SizedBox(width: 4),
                    Text(
                      loc.get('usageComparison'),
                      style: theme.textTheme.labelLarge?.copyWith(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),

          FittedBox(
            fit: BoxFit.scaleDown,
            alignment: Alignment.centerLeft,
            child: Text(
              totalFormatted,
              style: theme.textTheme.displayLarge?.copyWith(
                fontSize: 54,
                fontWeight: FontWeight.w900,
                letterSpacing: -2.0,
                height: 1.0,
              ),
            ),
          ),
          const SizedBox(height: 20),

          // PURE MONOCHROME PROGRESS TRACK
          ClipRRect(
            borderRadius: BorderRadius.circular(10),
            child: Container(
              height: 6,
              width: double.infinity,
              color: isDark ? const Color(0xFF262626) : const Color(0xFFE5E5E5),
              child: FractionallySizedBox(
                alignment: Alignment.centerLeft,
                widthFactor: ratio,
                child: Container(
                  color: primaryColor,
                ),
              ),
            ),
          ),
          const SizedBox(height: 12),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                loc.get('dailyGoal'),
                style: theme.textTheme.bodyMedium?.copyWith(
                  fontSize: 12,
                ),
              ),
              Text(
                '${(ratio * 100).toInt()}% terpakai',
                style: theme.textTheme.bodyMedium?.copyWith(
                  fontSize: 12,
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
    Color borderColor,
  ) {
    final theme = Theme.of(context);
    final days = provider.weeklyTrend;

    return Container(
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: Colors.transparent, // INVISIBLE BACKGROUND
        borderRadius: BorderRadius.circular(28),
        border: Border.all(color: borderColor, width: 1.0),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                loc.get('weeklyOverview'),
                style: theme.textTheme.labelLarge?.copyWith(
                  fontSize: 11,
                  letterSpacing: 1.4,
                  fontWeight: FontWeight.w800,
                ),
              ),
              Icon(
                Icons.bar_chart_rounded,
                size: 16,
                color: primaryColor,
              ),
            ],
          ),
          const SizedBox(height: 24),
          SizedBox(
            height: 110,
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
                        width: 22,
                        height: (75 * day.ratioOfMax).clamp(14.0, 75.0),
                        decoration: BoxDecoration(
                          color: isToday ? primaryColor : Colors.transparent,
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(
                            color: isToday
                                ? primaryColor
                                : (isDark ? const Color(0xFF333333) : const Color(0xFFD4D4D4)),
                            width: 1.0,
                          ),
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        day.dayName,
                        style: theme.textTheme.labelLarge?.copyWith(
                          fontSize: 11,
                          fontWeight: isToday ? FontWeight.w800 : FontWeight.w500,
                          color: isToday ? primaryColor : (isDark ? const Color(0xFF888888) : const Color(0xFF737373)),
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
    Color borderColor,
  ) {
    final theme = Theme.of(context);
    final sortedApps = List.of(provider.allApps)
      ..sort((a, b) => b.usageMinutesToday.compareTo(a.usageMinutesToday));

    final maxAppMinutes = sortedApps.first.usageMinutesToday;

    return Container(
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: Colors.transparent, // INVISIBLE BACKGROUND
        borderRadius: BorderRadius.circular(28),
        border: Border.all(color: borderColor, width: 1.0),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            loc.get('topUsedApps'),
            style: theme.textTheme.labelLarge?.copyWith(
              fontSize: 11,
              letterSpacing: 1.4,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 18),
          ListView.separated(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: sortedApps.length,
            separatorBuilder: (_, __) => const SizedBox(height: 16),
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
                borderRadius: BorderRadius.circular(16),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Row(
                    children: [
                      Container(
                        width: 38,
                        height: 38,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          border: Border.all(
                            color: isDark ? const Color(0xFF333333) : const Color(0xFFCCCCCC),
                            width: 1.0,
                          ),
                        ),
                        child: Icon(app.icon, color: primaryColor, size: 18),
                      ),
                      const SizedBox(width: 14),
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
                                        fontSize: 13,
                                        fontWeight: FontWeight.w700,
                                      ),
                                    ),
                                    if (app.isRestricted) ...[
                                      const SizedBox(width: 6),
                                      Container(
                                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                        decoration: BoxDecoration(
                                          border: Border.all(color: primaryColor, width: 0.8),
                                          borderRadius: BorderRadius.circular(8),
                                        ),
                                        child: Text(
                                          'DIBATASI',
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
                                    fontSize: 12,
                                    fontWeight: FontWeight.w800,
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 6),
                            ClipRRect(
                              borderRadius: BorderRadius.circular(4),
                              child: Container(
                                height: 4,
                                width: double.infinity,
                                color: isDark ? const Color(0xFF262626) : const Color(0xFFE5E5E5),
                                child: FractionallySizedBox(
                                  alignment: Alignment.centerLeft,
                                  widthFactor: ratio,
                                  child: Container(
                                    color: primaryColor,
                                  ),
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
