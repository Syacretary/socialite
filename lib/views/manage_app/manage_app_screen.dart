import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../../core/localization/app_localizations.dart';
import '../../core/motion/pressable_scale.dart';
import '../../core/motion/staggered_entrance.dart';
import '../../models/app_item.dart';
import '../../providers/app_management_provider.dart';
import '../../providers/settings_provider.dart';
import '../widgets/app_management_drawer.dart';
import '../widgets/custom_app_bar.dart';

class ManageAppScreen extends StatelessWidget {
  const ManageAppScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final settings = Provider.of<SettingsProvider>(context);
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
                CustomHeader(title: loc.get('manageTitle')),

                // Feather scroll wrapper with zero-glitter native scroll
                Expanded(
                  child: FeatherScrollWrapper(
                    child: SingleChildScrollView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      padding: const EdgeInsets.fromLTRB(16, 2, 16, 80),
                      child: Column(
                        mainAxisSize: MainAxisSize.min, // Hug content tightly
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          // ZONA DIBATASI (RESTRICTED ZONE DROP TARGET)
                          StaggeredEntrance(
                            index: 0,
                            child: _buildRestrictedZone(context, loc, isDark, primaryColor),
                          ),

                          const SizedBox(height: 8),

                          // VISUAL DRAG CONNECTOR HINT
                          StaggeredEntrance(
                            index: 1,
                            child: Center(
                              child: Padding(
                                padding: const EdgeInsets.symmetric(vertical: 2),
                                child: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Icon(
                                      Icons.swap_vert_rounded,
                                      size: 13,
                                      color: isDark ? const Color(0xFF666666) : const Color(0xFF999999),
                                    ),
                                    const SizedBox(width: 4),
                                    Text(
                                      loc.get('dragHint'),
                                      style: theme.textTheme.bodyMedium?.copyWith(
                                        fontSize: 10,
                                        fontWeight: FontWeight.w600,
                                        color: isDark ? const Color(0xFF666666) : const Color(0xFF999999),
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ),

                          const SizedBox(height: 8),

                          // ZONA APLIKASI BEBAS (AVAILABLE APPS LIST & DROP TARGET)
                          StaggeredEntrance(
                            index: 2,
                            child: _buildAvailableZone(context, loc, isDark, primaryColor),
                          ),
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

  Widget _buildRestrictedZone(
    BuildContext context,
    AppLocalizations loc,
    bool isDark,
    Color primaryColor,
  ) {
    final theme = Theme.of(context);
    final provider = Provider.of<AppManagementProvider>(context);

    return DragTarget<String>(
      onWillAcceptWithDetails: (details) {
        provider.setDraggingOverRestricted(true);
        HapticFeedback.selectionClick();
        return true;
      },
      onLeave: (data) {
        provider.setDraggingOverRestricted(false);
      },
      onAcceptWithDetails: (details) {
        provider.setDraggingOverRestricted(false);
        provider.restrictApp(details.data);
        HapticFeedback.mediumImpact();
      },
      builder: (context, candidateData, rejectedData) {
        final isHovered = candidateData.isNotEmpty || provider.isDraggingOverRestrictedZone;

        return AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          curve: Curves.easeOutCubic,
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          decoration: BoxDecoration(
            color: isHovered
                ? (isDark ? const Color(0xFF141414) : const Color(0xFFEBEBEB))
                : (isDark ? const Color(0xFF0A0A0A) : const Color(0xFFF3F4F6)),
            borderRadius: BorderRadius.circular(20),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min, // Strictly hug content
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    loc.get('restrictedZoneTitle'),
                    style: theme.textTheme.labelLarge?.copyWith(
                      fontSize: 11,
                      fontWeight: FontWeight.w800,
                      letterSpacing: 1.0,
                    ),
                  ),
                  Text(
                    '${provider.restrictedApps.length}',
                    style: TextStyle(
                      fontSize: 11,
                      color: primaryColor,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),

              // Content of restricted zone
              if (provider.allApps.isEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  child: Center(
                    child: Text(
                      loc.get('noInstalledApps'),
                      textAlign: TextAlign.center,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        fontSize: 11,
                        color: isDark ? const Color(0xFF666666) : const Color(0xFF999999),
                      ),
                    ),
                  ),
                )
              else if (provider.restrictedApps.isEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: Center(
                    child: Text(
                      loc.get('restrictedZoneEmpty').replaceAll('\n', ' '),
                      textAlign: TextAlign.center,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        fontSize: 11,
                        color: isDark ? const Color(0xFF555555) : const Color(0xFFAAAAAA),
                      ),
                    ),
                  ),
                )
              else
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: provider.restrictedApps.map((app) {
                    return _buildDraggableAppCard(
                      context: context,
                      app: app,
                      isRestricted: true,
                      isDark: isDark,
                      primaryColor: primaryColor,
                      onTap: () {
                        // Open new App Management Drawer
                        AppManagementDrawer.show(context, app.id);
                      },
                      onTapEject: () {
                        HapticFeedback.lightImpact();
                        provider.unrestrictApp(app.id);
                      },
                    );
                  }).toList(),
                ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildAvailableZone(
    BuildContext context,
    AppLocalizations loc,
    bool isDark,
    Color primaryColor,
  ) {
    final theme = Theme.of(context);
    final provider = Provider.of<AppManagementProvider>(context);

    return DragTarget<String>(
      onWillAcceptWithDetails: (details) {
        provider.setDraggingOverAvailable(true);
        HapticFeedback.selectionClick();
        return true;
      },
      onLeave: (data) {
        provider.setDraggingOverAvailable(false);
      },
      onAcceptWithDetails: (details) {
        provider.setDraggingOverAvailable(false);
        provider.unrestrictApp(details.data);
        HapticFeedback.mediumImpact();
      },
      builder: (context, candidateData, rejectedData) {
        final isHovered = candidateData.isNotEmpty || provider.isDraggingOverAvailableZone;

        return AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          curve: Curves.easeOutCubic,
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          decoration: BoxDecoration(
            color: isHovered
                ? (isDark ? const Color(0xFF141414) : const Color(0xFFEBEBEB))
                : (isDark ? const Color(0xFF0A0A0A) : const Color(0xFFF3F4F6)),
            borderRadius: BorderRadius.circular(20),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min, // Strictly hug content
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    loc.get('availableZoneTitle'),
                    style: theme.textTheme.labelLarge?.copyWith(
                      fontSize: 11,
                      fontWeight: FontWeight.w800,
                      letterSpacing: 1.0,
                    ),
                  ),
                  Text(
                    '${provider.availableApps.length}',
                    style: TextStyle(
                      fontSize: 11,
                      color: primaryColor,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),

              if (provider.allApps.isEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  child: Center(
                    child: Text(
                      loc.get('noInstalledApps'),
                      textAlign: TextAlign.center,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        fontSize: 11,
                        color: isDark ? const Color(0xFF666666) : const Color(0xFF999999),
                      ),
                    ),
                  ),
                )
              else if (provider.availableApps.isEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  child: Center(
                    child: Text(
                      loc.get('allAppsRestricted'),
                      textAlign: TextAlign.center,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        fontSize: 11,
                        color: isDark ? const Color(0xFF666666) : const Color(0xFF999999),
                      ),
                    ),
                  ),
                )
              else
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: provider.availableApps.map((app) {
                    return _buildDraggableAppCard(
                      context: context,
                      app: app,
                      isRestricted: false,
                      isDark: isDark,
                      primaryColor: primaryColor,
                      onTap: () {
                        // Open new App Management Drawer
                        AppManagementDrawer.show(context, app.id);
                      },
                      onTapEject: () {},
                    );
                  }).toList(),
                ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildDraggableAppCard({
    required BuildContext context,
    required AppItem app,
    required bool isRestricted,
    required bool isDark,
    required Color primaryColor,
    required VoidCallback onTap,
    required VoidCallback onTapEject,
  }) {
    final theme = Theme.of(context);

    final cardContent = PressableScale(
      onTap: onTap,
      pressedScale: 0.94,
      child: Container(
        width: 82, // Roomy compact width for enlarged icon
        padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 4),
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF121212) : const Color(0xFFFFFFFF),
          borderRadius: BorderRadius.circular(16),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min, // Hug content
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Stack(
              clipBehavior: Clip.none,
              children: [
                // Prominent unboxed authentic app icon
                SizedBox(
                  width: 40,
                  height: 40,
                  child: Center(
                    child: app.buildLogo(size: 38, isDark: isDark),
                  ),
                ),
                if (isRestricted)
                  Positioned(
                    top: -3,
                    right: -5,
                    child: GestureDetector(
                      onTap: onTapEject,
                      child: Container(
                        padding: const EdgeInsets.all(2.5),
                        decoration: BoxDecoration(
                          color: primaryColor,
                          shape: BoxShape.circle,
                        ),
                        child: Icon(
                          Icons.close_rounded,
                          color: isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF),
                          size: 9,
                        ),
                      ),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 5),
            Text(
              app.name,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.center,
              style: theme.textTheme.labelLarge?.copyWith(
                fontSize: 10,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 1),
            Text(
              app.formattedUsage,
              style: theme.textTheme.bodyMedium?.copyWith(
                fontSize: 9,
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
        ),
      ),
    );

    // Draggable wrapper with enlarged feedback logo
    return Draggable<String>(
      data: app.id,
      feedback: Material(
        color: Colors.transparent,
        child: Transform.scale(
          scale: 1.08,
          child: Container(
            width: 82,
            padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 4),
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF1F1F1F) : const Color(0xFFFFFFFF),
              borderRadius: BorderRadius.circular(16),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(isDark ? 0.6 : 0.15),
                  blurRadius: 16,
                  offset: const Offset(0, 5),
                ),
              ],
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                app.buildLogo(size: 40, isDark: isDark),
                const SizedBox(height: 4),
                Text(
                  app.name,
                  maxLines: 1,
                  style: theme.textTheme.labelLarge?.copyWith(
                    fontSize: 10,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
      childWhenDragging: Opacity(
        opacity: 0.15,
        child: cardContent,
      ),
      child: cardContent,
    );
  }
}
