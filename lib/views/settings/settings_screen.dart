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
    final borderColor = isDark ? const Color(0xFF262626) : const Color(0xFFE5E5E5);

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
                  title: loc.get('settingsTitle'),
                  subtitle: loc.get('settingsSubtitle'),
                ),

                Expanded(
                  child: SingleChildScrollView(
                    physics: const BouncingScrollPhysics(),
                    padding: const EdgeInsets.fromLTRB(16, 8, 16, 120),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        // LANGUAGE SELECTOR (No emojis)
                        _buildLanguageSection(context, loc, isDark, settings, primaryColor, borderColor),

                        const SizedBox(height: 20),

                        // THEME SELECTOR (AMOLED Black vs Milky Light, No emojis)
                        _buildThemeSection(context, loc, isDark, settings, primaryColor, borderColor),

                        const SizedBox(height: 20),

                        // PERMISSIONS SECTION (Usage stats, notifications, overlay)
                        _buildPermissionsSection(context, loc, isDark, settings, screentime, primaryColor, borderColor),

                        const SizedBox(height: 20),

                        // ABOUT & VERSION
                        _buildAboutSection(context, loc, isDark, primaryColor, borderColor),
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

  Widget _buildLanguageSection(
    BuildContext context,
    AppLocalizations loc,
    bool isDark,
    SettingsProvider settings,
    Color primaryColor,
    Color borderColor,
  ) {
    final theme = Theme.of(context);

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.transparent, // INVISIBLE BACKGROUND
        borderRadius: BorderRadius.circular(28),
        border: Border.all(color: borderColor, width: 1.0),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(7),
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  border: Border.all(color: primaryColor, width: 1.0),
                ),
                child: Icon(
                  Icons.language_rounded,
                  size: 15,
                  color: primaryColor,
                ),
              ),
              const SizedBox(width: 12),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    loc.get('languageSection'),
                    style: theme.textTheme.labelLarge?.copyWith(
                      fontSize: 12,
                      fontWeight: FontWeight.w800,
                      letterSpacing: 1.2,
                    ),
                  ),
                  Text(
                    loc.get('languageDesc'),
                    style: theme.textTheme.bodyMedium?.copyWith(fontSize: 11),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 18),

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
                  borderColor: borderColor,
                  onTap: () {
                    HapticFeedback.lightImpact();
                    settings.setLanguage('id');
                  },
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _buildChoiceChip(
                  context: context,
                  label: 'English',
                  codeLabel: 'EN',
                  isSelected: settings.languageCode == 'en',
                  isDark: isDark,
                  primaryColor: primaryColor,
                  borderColor: borderColor,
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
    Color borderColor,
  ) {
    final theme = Theme.of(context);

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.transparent, // INVISIBLE BACKGROUND
        borderRadius: BorderRadius.circular(28),
        border: Border.all(color: borderColor, width: 1.0),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(7),
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  border: Border.all(color: primaryColor, width: 1.0),
                ),
                child: Icon(
                  Icons.contrast_rounded,
                  size: 15,
                  color: primaryColor,
                ),
              ),
              const SizedBox(width: 12),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    loc.get('themeSection'),
                    style: theme.textTheme.labelLarge?.copyWith(
                      fontSize: 12,
                      fontWeight: FontWeight.w800,
                      letterSpacing: 1.2,
                    ),
                  ),
                  Text(
                    loc.get('themeDesc'),
                    style: theme.textTheme.bodyMedium?.copyWith(fontSize: 11),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 18),

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
                  borderColor: borderColor,
                  onTap: () {
                    HapticFeedback.selectionClick();
                    settings.setThemeMode(ThemeMode.dark);
                  },
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _buildChoiceChip(
                  context: context,
                  label: loc.get('themeMilk'),
                  codeLabel: 'LIGHT',
                  isSelected: settings.themeMode == ThemeMode.light,
                  isDark: isDark,
                  primaryColor: primaryColor,
                  borderColor: borderColor,
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
    Color borderColor,
  ) {
    final theme = Theme.of(context);
    final isUsageActive = settings.usagePermissionGranted || screentime.hasUsagePermission;

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.transparent, // INVISIBLE BACKGROUND
        borderRadius: BorderRadius.circular(28),
        border: Border.all(color: borderColor, width: 1.0),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(7),
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  border: Border.all(color: primaryColor, width: 1.0),
                ),
                child: Icon(
                  Icons.shield_outlined,
                  size: 15,
                  color: primaryColor,
                ),
              ),
              const SizedBox(width: 12),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    loc.get('permissionsSection'),
                    style: theme.textTheme.labelLarge?.copyWith(
                      fontSize: 12,
                      fontWeight: FontWeight.w800,
                      letterSpacing: 1.2,
                    ),
                  ),
                  Text(
                    loc.get('permissionsDesc'),
                    style: theme.textTheme.bodyMedium?.copyWith(fontSize: 11),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 18),

          _buildPermissionTile(
            context: context,
            title: loc.get('permissionUsage'),
            subtitle: loc.get('permissionUsageDesc'),
            isActive: isUsageActive,
            isDark: isDark,
            primaryColor: primaryColor,
            borderColor: borderColor,
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
          Divider(height: 24, thickness: 0.6, color: borderColor),

          _buildPermissionTile(
            context: context,
            title: loc.get('permissionNotification'),
            subtitle: loc.get('permissionNotificationDesc'),
            isActive: settings.notificationPermissionGranted,
            isDark: isDark,
            primaryColor: primaryColor,
            borderColor: borderColor,
            onToggle: () {
              HapticFeedback.lightImpact();
              settings.toggleNotificationPermission();
            },
          ),
          Divider(height: 24, thickness: 0.6, color: borderColor),

          _buildPermissionTile(
            context: context,
            title: loc.get('permissionOverlay'),
            subtitle: loc.get('permissionOverlayDesc'),
            isActive: settings.overlayPermissionGranted,
            isDark: isDark,
            primaryColor: primaryColor,
            borderColor: borderColor,
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
    required Color borderColor,
    required VoidCallback onToggle,
  }) {
    final theme = Theme.of(context);

    return InkWell(
      onTap: onToggle,
      borderRadius: BorderRadius.circular(16),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: theme.textTheme.labelLarge?.copyWith(
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    subtitle,
                    style: theme.textTheme.bodyMedium?.copyWith(fontSize: 11),
                  ),
                ],
              ),
            ),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
              decoration: BoxDecoration(
                color: isActive ? primaryColor : Colors.transparent,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: primaryColor, width: 1.0),
              ),
              child: Text(
                isActive ? 'AKTIF' : 'NONAKTIF',
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 0.6,
                  color: isActive
                      ? (isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF))
                      : primaryColor,
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
    required Color borderColor,
    required VoidCallback onTap,
  }) {
    final theme = Theme.of(context);

    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 12),
        decoration: BoxDecoration(
          color: isSelected ? primaryColor : Colors.transparent, // INVISIBLE when unselected
          borderRadius: BorderRadius.circular(18),
          border: Border.all(
            color: isSelected ? primaryColor : borderColor,
            width: 1.0,
          ),
        ),
        child: Column(
          children: [
            Text(
              codeLabel,
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w900,
                letterSpacing: 0.8,
                color: isSelected
                    ? (isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF))
                    : primaryColor,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              label,
              style: TextStyle(
                fontSize: 11,
                fontWeight: isSelected ? FontWeight.w800 : FontWeight.w500,
                color: isSelected
                    ? (isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF))
                    : (isDark ? const Color(0xFF888888) : const Color(0xFF737373)),
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
    Color borderColor,
  ) {
    final theme = Theme.of(context);

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.transparent, // INVISIBLE BACKGROUND
        borderRadius: BorderRadius.circular(28),
        border: Border.all(color: borderColor, width: 1.0),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              border: Border.all(color: primaryColor, width: 1.0),
            ),
            child: Icon(
              Icons.blur_on_rounded,
              size: 20,
              color: primaryColor,
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Socialite',
                  style: theme.textTheme.titleLarge?.copyWith(
                    fontSize: 15,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                Text(
                  loc.get('version'),
                  style: theme.textTheme.bodyMedium?.copyWith(fontSize: 11),
                ),
                const SizedBox(height: 2),
                Text(
                  loc.get('aboutDesc'),
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
