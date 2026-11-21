import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../core/localization/app_localizations.dart';
import '../providers/app_management_provider.dart';
import '../providers/settings_provider.dart';
import '../services/feature_block_service.dart';
import '../services/limit_enforcement_service.dart';
import 'manage_app/manage_app_screen.dart';
import 'screentime/screentime_screen.dart';
import 'settings/settings_screen.dart';

class MainScaffold extends StatefulWidget {
  const MainScaffold({super.key});

  @override
  State<MainScaffold> createState() => _MainScaffoldState();
}

class _MainScaffoldState extends State<MainScaffold> {
  int _currentIndex = 0;

  final List<Widget> _screens = const [
    ManageAppScreen(),
    ScreentimeScreen(),
    SettingsScreen(),
  ];

  @override
  void initState() {
    super.initState();
    // Start limit enforcement after first frame so context & providers are ready
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final provider = Provider.of<AppManagementProvider>(context, listen: false);
      final settings = Provider.of<SettingsProvider>(context, listen: false);
      LimitEnforcementService.instance.start(provider, context);
      FeatureBlockService.syncRules(provider);
      settings.refreshPermissions();
    });
  }

  @override
  void dispose() {
    LimitEnforcementService.instance.stop();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final settings = Provider.of<SettingsProvider>(context);
    final loc = AppLocalizations(settings.languageCode);
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    final primaryColor = isDark ? const Color(0xFFFFFFFF) : const Color(0xFF000000);
    final navBgColor = isDark ? const Color(0xFF141414) : const Color(0xFFF1F2F4);

    return Scaffold(
      extendBody: true,
      body: AnimatedSwitcher(
        duration: const Duration(milliseconds: 280),
        switchInCurve: Curves.easeOutCubic,
        switchOutCurve: Curves.easeInCubic,
        transitionBuilder: (child, animation) {
          return FadeTransition(
            opacity: animation,
            child: SlideTransition(
              position: Tween<Offset>(
                begin: const Offset(0.0, 0.02),
                end: Offset.zero,
              ).animate(animation),
              child: child,
            ),
          );
        },
        child: KeyedSubtree(
          key: ValueKey<int>(_currentIndex),
          child: _screens[_currentIndex],
        ),
      ),
      bottomNavigationBar: SafeArea(
        child: Align(
          alignment: Alignment.bottomCenter,
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 0, 20, 14),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(30),
                child: BackdropFilter(
                  filter: ImageFilter.blur(sigmaX: 30, sigmaY: 30),
                  child: Container(
                    height: 60,
                    padding: const EdgeInsets.all(4),
                    decoration: BoxDecoration(
                      color: navBgColor.withOpacity(0.92),
                      borderRadius: BorderRadius.circular(30),
                      // NO OUTLINES
                    ),
                    child: LayoutBuilder(
                      builder: (context, constraints) {
                        final tabWidth = constraints.maxWidth / 3;

                        return Stack(
                          children: [
                            // SLIDING ANIMATED PILL INDICATOR
                            AnimatedPositioned(
                              duration: const Duration(milliseconds: 260),
                              curve: Curves.easeOutCubic,
                              left: _currentIndex * tabWidth,
                              top: 0,
                              bottom: 0,
                              width: tabWidth,
                              child: Container(
                                decoration: BoxDecoration(
                                  color: primaryColor,
                                  borderRadius: BorderRadius.circular(26),
                                  boxShadow: [
                                    BoxShadow(
                                      color: Colors.black.withOpacity(isDark ? 0.35 : 0.08),
                                      blurRadius: 12,
                                      offset: const Offset(0, 3),
                                    ),
                                  ],
                                ),
                              ),
                            ),

                            // TAB ITEMS
                            Row(
                              children: [
                                Expanded(
                                  child: _buildNavItem(
                                    index: 0,
                                    icon: Icons.tune_rounded,
                                    label: loc.get('navManage'),
                                    isDark: isDark,
                                    primaryColor: primaryColor,
                                  ),
                                ),
                                Expanded(
                                  child: _buildNavItem(
                                    index: 1,
                                    icon: Icons.hourglass_top_rounded,
                                    label: loc.get('navScreentime'),
                                    isDark: isDark,
                                    primaryColor: primaryColor,
                                  ),
                                ),
                                Expanded(
                                  child: _buildNavItem(
                                    index: 2,
                                    icon: Icons.settings_rounded,
                                    label: loc.get('navSettings'),
                                    isDark: isDark,
                                    primaryColor: primaryColor,
                                  ),
                                ),
                              ],
                            ),
                          ],
                        );
                      },
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

  Widget _buildNavItem({
    required int index,
    required IconData icon,
    required String label,
    required bool isDark,
    required Color primaryColor,
  }) {
    final isSelected = _currentIndex == index;

    return GestureDetector(
      onTap: () {
        if (_currentIndex != index) {
          HapticFeedback.selectionClick();
          setState(() {
            _currentIndex = index;
          });
        }
      },
      behavior: HitTestBehavior.opaque,
      child: Center(
        child: AnimatedScale(
          duration: const Duration(milliseconds: 200),
          scale: isSelected ? 1.05 : 1.0,
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                icon,
                size: 18,
                color: isSelected
                    ? (isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF))
                    : (isDark ? const Color(0xFF777777) : const Color(0xFF888888)),
              ),
              if (isSelected) ...[
                const SizedBox(width: 6),
                Text(
                  label,
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w800,
                    letterSpacing: -0.2,
                    color: isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
