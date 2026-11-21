import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../../core/localization/app_localizations.dart';
import '../../models/app_item.dart';
import '../../providers/app_management_provider.dart';
import '../../providers/settings_provider.dart';
import '../widgets/custom_app_bar.dart';
import '../widgets/smooth_sheet.dart';

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
                // Clean Header without subtitle and without trailing chips
                CustomHeader(title: loc.get('manageTitle')),

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
                          // ZONA DIBATASI (RESTRICTED ZONE DROP TARGET)
                          _buildRestrictedZone(context, loc, isDark, primaryColor),

                          const SizedBox(height: 12),

                          // VISUAL DRAG CONNECTOR HINT
                          Center(
                            child: Padding(
                              padding: const EdgeInsets.symmetric(vertical: 4),
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Icon(
                                    Icons.swap_vert_rounded,
                                    size: 14,
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

                          const SizedBox(height: 12),

                          // ZONA APLIKASI BEBAS (AVAILABLE APPS LIST & DROP TARGET)
                          _buildAvailableZone(context, loc, isDark, primaryColor),
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
          duration: const Duration(milliseconds: 200),
          curve: Curves.easeOutCubic,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          decoration: BoxDecoration(
            color: isHovered
                ? (isDark ? const Color(0xFF141414) : const Color(0xFFEBEBEB))
                : (isDark ? const Color(0xFF0A0A0A) : const Color(0xFFF3F4F6)),
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
              const SizedBox(height: 12),

              // Content of restricted zone (Compact, no vertical oversize)
              if (provider.restrictedApps.isEmpty)
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  alignment: Alignment.center,
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(
                        Icons.add_circle_outline_rounded,
                        size: 16,
                        color: isDark ? const Color(0xFF555555) : const Color(0xFFAAAAAA),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        loc.get('restrictedZoneEmpty').replaceAll('\n', ' '),
                        style: theme.textTheme.bodyMedium?.copyWith(
                          fontSize: 11,
                          color: isDark ? const Color(0xFF555555) : const Color(0xFFAAAAAA),
                        ),
                      ),
                    ],
                  ),
                )
              else
                LayoutBuilder(
                  builder: (context, constraints) {
                    return GridView.builder(
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      itemCount: provider.restrictedApps.length,
                      gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                        maxCrossAxisExtent: 110,
                        mainAxisSpacing: 10,
                        crossAxisSpacing: 10,
                        childAspectRatio: 0.95,
                      ),
                      itemBuilder: (context, index) {
                        final app = provider.restrictedApps[index];
                        return _buildDraggableAppCard(
                          context: context,
                          app: app,
                          isRestricted: true,
                          isDark: isDark,
                          primaryColor: primaryColor,
                          onTap: () {
                            SmoothAppSheet.show(
                              context: context,
                              app: app,
                              isDark: isDark,
                              onToggleRestriction: () => provider.toggleRestriction(app.id),
                            );
                          },
                          onTapEject: () {
                            HapticFeedback.lightImpact();
                            provider.unrestrictApp(app.id);
                          },
                        );
                      },
                    );
                  },
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
          duration: const Duration(milliseconds: 200),
          curve: Curves.easeOutCubic,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          decoration: BoxDecoration(
            color: isHovered
                ? (isDark ? const Color(0xFF141414) : const Color(0xFFEBEBEB))
                : (isDark ? const Color(0xFF0A0A0A) : const Color(0xFFF3F4F6)),
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
              const SizedBox(height: 12),

              if (provider.availableApps.isEmpty)
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(vertical: 16),
                  alignment: Alignment.center,
                  child: Text(
                    'Semua aplikasi telah dibatasi.',
                    style: theme.textTheme.bodyMedium?.copyWith(fontSize: 11),
                  ),
                )
              else
                LayoutBuilder(
                  builder: (context, constraints) {
                    return GridView.builder(
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      itemCount: provider.availableApps.length,
                      gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                        maxCrossAxisExtent: 110,
                        mainAxisSpacing: 10,
                        crossAxisSpacing: 10,
                        childAspectRatio: 0.95,
                      ),
                      itemBuilder: (context, index) {
                        final app = provider.availableApps[index];
                        return _buildDraggableAppCard(
                          context: context,
                          app: app,
                          isRestricted: false,
                          isDark: isDark,
                          primaryColor: primaryColor,
                          onTap: () {
                            SmoothAppSheet.show(
                              context: context,
                              app: app,
                              isDark: isDark,
                              onToggleRestriction: () => provider.toggleRestriction(app.id),
                            );
                          },
                          onTapEject: () {},
                        );
                      },
                    );
                  },
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

    final cardContent = GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 6),
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF121212) : const Color(0xFFFFFFFF),
          borderRadius: BorderRadius.circular(18),
          // NO OUTLINES
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Stack(
              clipBehavior: Clip.none,
              children: [
                // Authentic Brand Logo
                SizedBox(
                  width: 32,
                  height: 32,
                  child: Center(
                    child: app.buildLogo(size: 28),
                  ),
                ),
                if (isRestricted)
                  Positioned(
                    top: -4,
                    right: -6,
                    child: GestureDetector(
                      onTap: onTapEject,
                      child: Container(
                        padding: const EdgeInsets.all(2),
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
            const SizedBox(height: 6),
            Text(
              app.name,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.center,
              style: theme.textTheme.labelLarge?.copyWith(
                fontSize: 11,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 2),
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

    // Draggable wrapper with mouse & touch responsiveness
    return Draggable<String>(
      data: app.id,
      feedback: Material(
        color: Colors.transparent,
        child: Transform.scale(
          scale: 1.08,
          child: Container(
            width: 90,
            height: 80,
            padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 6),
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF1F1F1F) : const Color(0xFFFFFFFF),
              borderRadius: BorderRadius.circular(18),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(isDark ? 0.6 : 0.15),
                  blurRadius: 18,
                  offset: const Offset(0, 6),
                ),
              ],
            ),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                app.buildLogo(size: 30),
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
