import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../models/app_item.dart';

class SmoothAppSheet {
  static Future<void> show({
    required BuildContext context,
    required AppItem app,
    required bool isDark,
    required VoidCallback onToggleRestriction,
  }) {
    HapticFeedback.lightImpact();

    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      barrierColor: Colors.black.withOpacity(isDark ? 0.75 : 0.4),
      transitionAnimationController: AnimationController(
        duration: const Duration(milliseconds: 400),
        reverseDuration: const Duration(milliseconds: 300),
        vsync: Navigator.of(context),
      ),
      builder: (sheetContext) {
        return TweenAnimationBuilder<double>(
          duration: const Duration(milliseconds: 350),
          curve: Curves.easeOutCubic,
          tween: Tween(begin: 0.15, end: 0.0),
          builder: (context, translateY, child) {
            return Transform.translate(
              offset: Offset(0, translateY * 200),
              child: child,
            );
          },
          child: _AppSheetContent(
            app: app,
            isDark: isDark,
            onToggleRestriction: () {
              Navigator.of(sheetContext).pop();
              onToggleRestriction();
            },
          ),
        );
      },
    );
  }
}

class _AppSheetContent extends StatefulWidget {
  final AppItem app;
  final bool isDark;
  final VoidCallback onToggleRestriction;

  const _AppSheetContent({
    required this.app,
    required this.isDark,
    required this.onToggleRestriction,
  });

  @override
  State<_AppSheetContent> createState() => _AppSheetContentState();
}

class _AppSheetContentState extends State<_AppSheetContent> with SingleTickerProviderStateMixin {
  late AnimationController _springController;
  late Animation<double> _scaleAnimation;

  @override
  void initState() {
    super.initState();
    _springController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 450),
    );
    _scaleAnimation = CurvedAnimation(
      parent: _springController,
      curve: const SpringCurve(),
    );
    _springController.forward();
  }

  @override
  void dispose() {
    _springController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = widget.isDark;
    final app = widget.app;

    final primaryColor = isDark ? const Color(0xFFFFFFFF) : const Color(0xFF000000);
    final surfaceColor = isDark ? const Color(0xFF111111) : const Color(0xFFFFFFFF);

    return SafeArea(
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 540),
          child: Container(
            margin: const EdgeInsets.fromLTRB(16, 0, 16, 16),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(32),
              child: BackdropFilter(
                filter: ImageFilter.blur(sigmaX: 30, sigmaY: 30),
                child: Container(
                  padding: const EdgeInsets.fromLTRB(24, 16, 24, 28),
                  decoration: BoxDecoration(
                    color: surfaceColor.withOpacity(0.96),
                    borderRadius: BorderRadius.circular(32),
                    // NO OUTLINES
                  ),
                  child: ScaleTransition(
                    scale: _scaleAnimation,
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        // Subtle drag grabber
                        Container(
                          width: 36,
                          height: 4,
                          decoration: BoxDecoration(
                            color: isDark ? const Color(0xFF333333) : const Color(0xFFD4D4D4),
                            borderRadius: BorderRadius.circular(4),
                          ),
                        ),
                        const SizedBox(height: 20),

                        // Authentic App Logo
                        SizedBox(
                          width: 48,
                          height: 48,
                          child: Center(
                            child: app.buildLogo(size: 40),
                          ),
                        ),
                        const SizedBox(height: 12),

                        Text(
                          app.name,
                          style: theme.textTheme.displayMedium?.copyWith(
                            fontSize: 20,
                            fontWeight: FontWeight.w800,
                            letterSpacing: -0.5,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          app.category.toUpperCase(),
                          style: theme.textTheme.bodyMedium?.copyWith(
                            fontSize: 10,
                            letterSpacing: 1.5,
                            fontWeight: FontWeight.w600,
                          ),
                        ),

                        const SizedBox(height: 20),

                        // Quick Usage & Restriction Status Bar (No Outlines)
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
                          decoration: BoxDecoration(
                            color: isDark ? const Color(0xFF1A1A1A) : const Color(0xFFF3F4F6),
                            borderRadius: BorderRadius.circular(20),
                            // NO OUTLINES
                          ),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    'PENGGUNAAN HARI INI',
                                    style: theme.textTheme.bodyMedium?.copyWith(
                                      fontSize: 9,
                                      letterSpacing: 1.2,
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    app.formattedUsage,
                                    style: theme.textTheme.titleLarge?.copyWith(
                                      fontSize: 16,
                                      fontWeight: FontWeight.w800,
                                    ),
                                  ),
                                ],
                              ),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                                decoration: BoxDecoration(
                                  color: app.isRestricted ? primaryColor : (isDark ? const Color(0xFF262626) : const Color(0xFFE5E7EB)),
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                child: Text(
                                  app.isRestricted ? 'DIBATASI' : 'BEBAS',
                                  style: TextStyle(
                                    fontSize: 10,
                                    fontWeight: FontWeight.w800,
                                    letterSpacing: 0.8,
                                    color: app.isRestricted
                                        ? (isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF))
                                        : primaryColor,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),

                        const SizedBox(height: 20),

                        // Action Button (No Outlines)
                        SizedBox(
                          width: double.infinity,
                          child: TextButton(
                            onPressed: () {
                              HapticFeedback.mediumImpact();
                              widget.onToggleRestriction();
                            },
                            style: TextButton.styleFrom(
                              backgroundColor: app.isRestricted
                                  ? (isDark ? const Color(0xFF222222) : const Color(0xFFE5E7EB))
                                  : primaryColor,
                              foregroundColor: app.isRestricted
                                  ? primaryColor
                                  : (isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF)),
                              padding: const EdgeInsets.symmetric(vertical: 14),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(18),
                              ),
                            ),
                            child: Text(
                              app.isRestricted ? 'Lepaskan Batasan' : 'Batasi Aplikasi Ini',
                              style: const TextStyle(
                                fontSize: 13,
                                fontWeight: FontWeight.w800,
                                letterSpacing: 0.5,
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class SpringCurve extends Curve {
  const SpringCurve();

  @override
  double transformInternal(double t) {
    if (t == 0) return 0;
    if (t == 1) return 1;
    return (t * t * ((1.70158 + 1) * t - 1.70158)) + 1;
  }
}
