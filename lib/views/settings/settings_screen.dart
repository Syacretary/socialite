import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../../core/localization/app_localizations.dart';
import '../../providers/screentime_provider.dart';
import '../../providers/settings_provider.dart';
import '../widgets/custom_app_bar.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final settings = Provider.of<SettingsProvider>(context);
    final screentime = Provider.of<ScreentimeProvider>(context);
    final loc = AppLocalizations(settings.languageCode);
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final primaryColor = isDark ? const Color(0xFFFFFFFF) : const Color(0xFF000000);

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: SafeArea(
        bottom: false,
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 680),
            child: Column(
              children: [
                // Clean Header
                CustomHeader(title: loc.get('settingsTitle')),

                // Feather scroll wrapper with stable native scroll
                Expanded(
                  child: FeatherScrollWrapper(
                    child: SingleChildScrollView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      padding: const EdgeInsets.fromLTRB(16, 2, 16, 80),
                      child: Column(
                        mainAxisSize: MainAxisSize.min, // Hug content
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          // LANGUAGE SELECTOR
                          _buildLanguageSection(context, loc, isDark, settings, primaryColor),

                          const SizedBox(height: 10),

                          // THEME SELECTOR
                          _buildThemeSection(context, loc, isDark, settings, primaryColor),

                          const SizedBox(height: 10),

                          // PERMISSIONS SECTION
                          _buildPermissionsSection(context, loc, isDark, settings, screentime, primaryColor),

                          const SizedBox(height: 10),

                          // ABOUT & VERSION
                          _buildAboutSection(context, loc, isDark, primaryColor),
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

  Widget _buildLanguageSection(
    BuildContext context,
    AppLocalizations loc,
    bool isDark,
    SettingsProvider settings,
    Color primaryColor,
  ) {
    final theme = Theme.of(context);

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF0A0A0A) : const Color(0xFFF3F4F6),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.language_rounded, size: 15, color: primaryColor),
              const SizedBox(width: 8),
              Text(
                loc.get('languageSection'),
                style: theme.textTheme.labelLarge?.copyWith(
                  fontSize: 11,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 1.0,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),

          Row(
            children: [
              Expanded(
                child: _buildChoiceChip(
                  context: context,
                  label: 'Bahasa Indonesia',
                  codeLabel: 'ID',
                  isSelected: settings.languageCode == 'id',
                  isDark: isDark,
                  primaryColor: primaryColor,
                  onTap: () {
                    HapticFeedback.lightImpact();
                    settings.setLanguage('id');
                  },
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _buildChoiceChip(
                  context: context,
                  label: 'English',
                  codeLabel: 'EN',
                  isSelected: settings.languageCode == 'en',
                  isDark: isDark,
                  primaryColor: primaryColor,
                  onTap: () {
                    HapticFeedback.lightImpact();
                    settings.setLanguage('en');
                  },
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildThemeSection(
    BuildContext context,
    AppLocalizations loc,
    bool isDark,
    SettingsProvider settings,
    Color primaryColor,
  ) {
    final theme = Theme.of(context);

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF0A0A0A) : const Color(0xFFF3F4F6),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.contrast_rounded, size: 15, color: primaryColor),
              const SizedBox(width: 8),
              Text(
                loc.get('themeSection'),
                style: theme.textTheme.labelLarge?.copyWith(
                  fontSize: 11,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 1.0,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),

          Row(
            children: [
              Expanded(
                child: _buildChoiceChip(
                  context: context,
                  label: loc.get('themeAmoled'),
                  codeLabel: 'DARK',
                  isSelected: settings.themeMode == ThemeMode.dark,
                  isDark: isDark,
                  primaryColor: primaryColor,
                  onTap: () {
                    HapticFeedback.selectionClick();
                    settings.setThemeMode(ThemeMode.dark);
                  },
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _buildChoiceChip(
                  context: context,
                  label: loc.get('themeMilk'),
                  codeLabel: 'LIGHT',
                  isSelected: settings.themeMode == ThemeMode.light,
                  isDark: isDark,
                  primaryColor: primaryColor,
                  onTap: () {
                    HapticFeedback.selectionClick();
                    settings.setThemeMode(ThemeMode.light);
                  },
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildPermissionsSection(
    BuildContext context,
    AppLocalizations loc,
    bool isDark,
    SettingsProvider settings,
    ScreentimeProvider screentime,
    Color primaryColor,
  ) {
    final theme = Theme.of(context);
    final isUsageActive = settings.usagePermissionGranted || screentime.hasUsagePermission;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF0A0A0A) : const Color(0xFFF3F4F6),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.shield_outlined, size: 15, color: primaryColor),
              const SizedBox(width: 8),
              Text(
                loc.get('permissionsSection'),
                style: theme.textTheme.labelLarge?.copyWith(
                  fontSize: 11,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 1.0,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),

          _buildPermissionTile(
            context: context,
            title: loc.get('permissionUsage'),
            subtitle: loc.get('permissionUsageDesc'),
            isActive: isUsageActive,
            isDark: isDark,
            primaryColor: primaryColor,
            onToggle: () {
              HapticFeedback.lightImpact();
              final next = !isUsageActive;
              settings.setUsagePermission(next);
              if (next) {
                screentime.grantUsagePermission();
              } else {
                screentime.toggleUsagePermission();
              }
            },
          ),
          const SizedBox(height: 8),

          _buildPermissionTile(
            context: context,
            title: loc.get('permissionNotification'),
            subtitle: loc.get('permissionNotificationDesc'),
            isActive: settings.notificationPermissionGranted,
            isDark: isDark,
            primaryColor: primaryColor,
            onToggle: () {
              HapticFeedback.lightImpact();
              settings.toggleNotificationPermission();
            },
          ),
          const SizedBox(height: 8),

          _buildPermissionTile(
            context: context,
            title: loc.get('permissionOverlay'),
            subtitle: loc.get('permissionOverlayDesc'),
            isActive: settings.overlayPermissionGranted,
            isDark: isDark,
            primaryColor: primaryColor,
            onToggle: () {
              HapticFeedback.lightImpact();
              settings.toggleOverlayPermission();
            },
          ),
        ],
      ),
    );
  }

  Widget _buildPermissionTile({
    required BuildContext context,
    required String title,
    required String subtitle,
    required bool isActive,
    required bool isDark,
    required Color primaryColor,
    required VoidCallback onToggle,
  }) {
    final theme = Theme.of(context);

    return InkWell(
      onTap: onToggle,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 3),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: theme.textTheme.labelLarge?.copyWith(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  Text(
                    subtitle,
                    style: theme.textTheme.bodyMedium?.copyWith(fontSize: 10),
                  ),
                ],
              ),
            ),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: isActive
                    ? primaryColor
                    : (isDark ? const Color(0xFF1E1E1E) : const Color(0xFFE2E4E8)),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                isActive ? 'AKTIF' : 'OFF',
                style: TextStyle(
                  fontSize: 9,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 0.5,
                  color: isActive
                      ? (isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF))
                      : (isDark ? const Color(0xFF888888) : const Color(0xFF666666)),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildChoiceChip({
    required BuildContext context,
    required String label,
    required String codeLabel,
    required bool isSelected,
    required bool isDark,
    required Color primaryColor,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 8),
        decoration: BoxDecoration(
          color: isSelected
              ? primaryColor
              : (isDark ? const Color(0xFF141414) : const Color(0xFFFFFFFF)),
          borderRadius: BorderRadius.circular(14),
        ),
        child: Column(
          children: [
            Text(
              codeLabel,
              style: TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.w900,
                letterSpacing: 0.8,
                color: isSelected
                    ? (isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF))
                    : primaryColor,
              ),
            ),
            const SizedBox(height: 1),
            Text(
              label,
              style: TextStyle(
                fontSize: 9,
                fontWeight: isSelected ? FontWeight.w800 : FontWeight.w500,
                color: isSelected
                    ? (isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF))
                    : (isDark ? const Color(0xFF777777) : const Color(0xFF888888)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAboutSection(
    BuildContext context,
    AppLocalizations loc,
    bool isDark,
    Color primaryColor,
  ) {
    final theme = Theme.of(context);

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF0A0A0A) : const Color(0xFFF3F4F6),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        children: [
          Icon(Icons.blur_on_rounded, size: 18, color: primaryColor),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Socialite',
                  style: theme.textTheme.titleLarge?.copyWith(
                    fontSize: 13,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                Text(
                  loc.get('version'),
                  style: theme.textTheme.bodyMedium?.copyWith(fontSize: 10),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
