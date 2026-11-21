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
                // Header with 3-second glanceability
                CustomHeader(
                  title: loc.get('manageTitle'),
                  subtitle: loc.get('manageSubtitle'),
                  trailing: Consumer<AppManagementProvider>(
                    builder: (context, provider, _) {
                      return Container(
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                        decoration: BoxDecoration(
                          color: Colors.transparent, // Invisible background
                          borderRadius: BorderRadius.circular(20),
                          border: Border.all(color: borderColor, width: 1.0),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              Icons.lock_outline_rounded,
                              size: 14,
                              color: primaryColor,
                            ),
                            const SizedBox(width: 6),
                            Text(
                              '${provider.restrictedApps.length} ${loc.get('appsCount')}',
                              style: theme.textTheme.labelLarge?.copyWith(
                                fontSize: 12,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ],
                        ),
                      );
                    },
                  ),
                ),

                Expanded(
                  child: SingleChildScrollView(
                    physics: const BouncingScrollPhysics(),
                    padding: const EdgeInsets.fromLTRB(16, 8, 16, 120),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        // ZONA DIBATASI (RESTRICTED ZONE DROP TARGET)
                        _buildRestrictedZone(context, loc, isDark, primaryColor, borderColor),

                        const SizedBox(height: 20),

                        // VISUAL DRAG CONNECTOR HINT
                        Center(
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                            decoration: BoxDecoration(
                              color: Colors.transparent,
                              borderRadius: BorderRadius.circular(16),
                              border: Border.all(
                                color: isDark ? const Color(0xFF1F1F1F) : const Color(0xFFEFEFEF),
                                width: 1.0,
                              ),
                            ),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  Icons.swap_vert_rounded,
                                  size: 15,
                                  color: isDark ? const Color(0xFF888888) : const Color(0xFF737373),
                                ),
                                const SizedBox(width: 6),
                                Text(
                                  loc.get('dragHint'),
                                  style: theme.textTheme.bodyMedium?.copyWith(
                                    fontSize: 11,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),

                        const SizedBox(height: 20),

                        // ZONA APLIKASI BEBAS (AVAILABLE APPS LIST & DROP TARGET)
                        _buildAvailableZone(context, loc, isDark, primaryColor, borderColor),
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

  Widget _buildRestrictedZone(
    BuildContext context,
    AppLocalizations loc,
    bool isDark,
    Color primaryColor,
    Color borderColor,
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
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOutCubic,
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            color: Colors.transparent, // INVISIBLE BACKGROUND
            borderRadius: BorderRadius.circular(28),
            border: Border.all(
              color: isHovered ? primaryColor : borderColor,
              width: isHovered ? 1.8 : 1.0,
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
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
                      const SizedBox(width: 10),
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            loc.get('restrictedZoneTitle'),
                            style: theme.textTheme.labelLarge?.copyWith(
                              fontSize: 12,
                              fontWeight: FontWeight.w800,
                              letterSpacing: 1.0,
                            ),
                          ),
                          Text(
                            loc.get('restrictedZoneSubtitle'),
                            style: theme.textTheme.bodyMedium?.copyWith(
                              fontSize: 11,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: primaryColor,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      '${provider.restrictedApps.length}',
                      style: TextStyle(
                        fontSize: 12,
                        color: isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF),
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 18),

              // Content of restricted zone
              if (provider.restrictedApps.isEmpty)
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(vertical: 36),
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: Colors.transparent,
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(
                      color: isDark ? const Color(0xFF1E1E1E) : const Color(0xFFEBEBEB),
                      width: 1.0,
                    ),
                  ),
                  child: Column(
                    children: [
                      Icon(
                        Icons.add_circle_outline_rounded,
                        size: 28,
                        color: isDark ? const Color(0xFF666666) : const Color(0xFF999999),
                      ),
                      const SizedBox(height: 10),
                      Text(
                        loc.get('restrictedZoneEmpty'),
                        textAlign: TextAlign.center,
                        style: theme.textTheme.bodyMedium?.copyWith(
                          fontSize: 12,
                          height: 1.5,
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
                        maxCrossAxisExtent: 120,
                        mainAxisSpacing: 12,
                        crossAxisSpacing: 12,
                        childAspectRatio: 0.90,
                      ),
                      itemBuilder: (context, index) {
                        final app = provider.restrictedApps[index];
                        return _buildDraggableAppCard(
                          context: context,
                          app: app,
                          isRestricted: true,
                          isDark: isDark,
                          primaryColor: primaryColor,
                          borderColor: borderColor,
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
    Color borderColor,
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
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOutCubic,
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            color: Colors.transparent, // INVISIBLE BACKGROUND
            borderRadius: BorderRadius.circular(28),
            border: Border.all(
              color: isHovered ? primaryColor : borderColor,
              width: isHovered ? 1.8 : 1.0,
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
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
                          Icons.grid_view_rounded,
                          size: 15,
                          color: primaryColor,
                        ),
                      ),
                      const SizedBox(width: 10),
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            loc.get('availableZoneTitle'),
                            style: theme.textTheme.labelLarge?.copyWith(
                              fontSize: 12,
                              fontWeight: FontWeight.w800,
                              letterSpacing: 1.0,
                            ),
                          ),
                          Text(
                            loc.get('availableZoneSubtitle'),
                            style: theme.textTheme.bodyMedium?.copyWith(
                              fontSize: 11,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: Colors.transparent,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: borderColor, width: 1.0),
                    ),
                    child: Text(
                      '${provider.availableApps.length}',
                      style: theme.textTheme.labelLarge?.copyWith(
                        fontSize: 12,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 18),

              if (provider.availableApps.isEmpty)
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(vertical: 30),
                  alignment: Alignment.center,
                  child: Text(
                    'Semua aplikasi telah masuk zona batas.',
                    style: theme.textTheme.bodyMedium?.copyWith(fontSize: 12),
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
                        maxCrossAxisExtent: 120,
                        mainAxisSpacing: 12,
                        crossAxisSpacing: 12,
                        childAspectRatio: 0.90,
                      ),
                      itemBuilder: (context, index) {
                        final app = provider.availableApps[index];
                        return _buildDraggableAppCard(
                          context: context,
                          app: app,
                          isRestricted: false,
                          isDark: isDark,
                          primaryColor: primaryColor,
                          borderColor: borderColor,
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
    required Color borderColor,
    required VoidCallback onTap,
    required VoidCallback onTapEject,
  }) {
    final theme = Theme.of(context);

    final cardContent = GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 8),
        decoration: BoxDecoration(
          color: Colors.transparent, // INVISIBLE BACKGROUND
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: isRestricted ? primaryColor : borderColor,
            width: 1.0,
          ),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Stack(
              clipBehavior: Clip.none,
              children: [
                Container(
                  width: 42,
                  height: 42,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: isDark ? const Color(0xFF333333) : const Color(0xFFCCCCCC),
                      width: 1.0,
                    ),
                  ),
                  child: Icon(
                    app.icon,
                    color: primaryColor,
                    size: 20,
                  ),
                ),
                if (isRestricted)
                  Positioned(
                    top: -4,
                    right: -4,
                    child: GestureDetector(
                      onTap: onTapEject,
                      child: Container(
                        padding: const EdgeInsets.all(3),
                        decoration: BoxDecoration(
                          color: primaryColor,
                          shape: BoxShape.circle,
                        ),
                        child: Icon(
                          Icons.close_rounded,
                          color: isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF),
                          size: 10,
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
                fontSize: 10,
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
        ),
      ),
    );

    // Draggable wrapper with mouse & touch responsiveness and smooth feedback
    return Draggable<String>(
      data: app.id,
      feedback: Material(
        color: Colors.transparent,
        child: Transform.scale(
          scale: 1.06,
          child: Container(
            width: 100,
            height: 95,
            padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 8),
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF000000) : const Color(0xFFF8F9FA),
              borderRadius: BorderRadius.circular(22),
              border: Border.all(color: primaryColor, width: 1.5),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(isDark ? 0.6 : 0.15),
                  blurRadius: 20,
                  offset: const Offset(0, 6),
                ),
              ],
            ),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(app.icon, color: primaryColor, size: 24),
                const SizedBox(height: 6),
                Text(
                  app.name,
                  maxLines: 1,
                  style: theme.textTheme.labelLarge?.copyWith(
                    fontSize: 11,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
      childWhenDragging: Opacity(
        opacity: 0.2,
        child: cardContent,
      ),
      child: cardContent,
    );
  }
}
