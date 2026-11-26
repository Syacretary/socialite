import 'dart:math' as math;
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../core/localization/app_localizations.dart';
import '../core/motion/pressable_scale.dart';
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

class _MainScaffoldState extends State<MainScaffold>
    with WidgetsBindingObserver, TickerProviderStateMixin {
  int _currentIndex = 0;

  late final AnimationController _navController;
  late final Animation<double> _navCurved;
  double _fromIndex = 0.0;
  double _toIndex = 0.0;

  final List<Widget> _screens = const [
    ManageAppScreen(),
    ScreentimeScreen(),
    SettingsScreen(),
  ];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);

    _navController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 250),
    );
    _navCurved = CurvedAnimation(
      parent: _navController,
      curve: Curves.easeInOutCubic,
    );

    // Start limit enforcement after first frame so context & providers are ready
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final provider = Provider.of<AppManagementProvider>(context, listen: false);
      final settings = Provider.of<SettingsProvider>(context, listen: false);
      LimitEnforcementService.instance.start(provider, context);
      FeatureBlockService.syncRules(provider);
      settings.refreshPermissions();
      provider.checkInstalledApps();
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && mounted) {
      final provider = Provider.of<AppManagementProvider>(context, listen: false);
      final settings = Provider.of<SettingsProvider>(context, listen: false);
      provider.checkInstalledApps();
      settings.refreshPermissions();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    LimitEnforcementService.instance.stop();
    _navController.dispose();
    super.dispose();
  }

  void _onTabSelected(int index) {
    if (_currentIndex == index) return;
    HapticFeedback.selectionClick();
    _fromIndex = _navController.isAnimating
        ? lerpDouble(_fromIndex, _toIndex, _navCurved.value)!
        : _currentIndex.toDouble();
    _toIndex = index.toDouble();
    setState(() {
      _currentIndex = index;
    });
    _navController.forward(from: 0.0);
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
        duration: const Duration(milliseconds: 240),
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
                    ),
                    child: LayoutBuilder(
                      builder: (context, constraints) {
                        final tabWidth = constraints.maxWidth / 3;

                        return AnimatedBuilder(
                          animation: _navCurved,
                          builder: (context, _) {
                            final progress = _navController.isAnimating ? _navCurved.value : 1.0;
                            final currentPos = lerpDouble(_fromIndex, _toIndex, progress)!;
                            final leftPos = currentPos * tabWidth;

                            // Squash and stretch physics:
                            // Stretches along motion path, squashes on impact
                            final double scaleX;
                            final double scaleY;
                            if (_navController.isAnimating) {
                              if (progress < 0.70) {
                                final t = (progress / 0.70);
                                final midPeak = math.sin(t * math.pi);
                                scaleX = 1.0 + (midPeak * 0.14);
                                scaleY = 1.0 - (midPeak * 0.08);
                              } else {
                                final t = (progress - 0.70) / 0.30;
                                final impactPeak = math.sin(t * math.pi);
                                scaleX = 1.0 - (impactPeak * 0.06);
                                scaleY = 1.0 + (impactPeak * 0.04);
                              }
                            } else {
                              scaleX = 1.0;
                              scaleY = 1.0;
                            }

                            return Stack(
                              children: [
                                // SQUASH & STRETCH ANIMATED PILL INDICATOR
                                Positioned(
                                  left: leftPos,
                                  top: 0,
                                  bottom: 0,
                                  width: tabWidth,
                                  child: Transform.scale(
                                    scaleX: scaleX,
                                    scaleY: scaleY,
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

    return PressableScale(
      onTap: () => _onTabSelected(index),
      pressedScale: 0.94,
      enableHaptic: false,
      child: Center(
        child: AnimatedScale(
          duration: const Duration(milliseconds: 200),
          curve: Curves.easeOutBack,
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
