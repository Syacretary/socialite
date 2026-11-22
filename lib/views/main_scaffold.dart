import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../core/localization/app_localizations.dart';
import '../providers/app_management_provider.dart';
import '../providers/settings_provider.dart';
import '../services/feature_block_service.dart';
import '../services/limit_enforcement_service.dart';
import '../core/animation/squash_stretch_navbar.dart';
import 'manage_app/manage_app_screen.dart';
import 'screentime/screentime_screen.dart';
import 'settings/settings_screen.dart';

class MainScaffold extends StatefulWidget {
  const MainScaffold({super.key});

  @override
  State<MainScaffold> createState() => _MainScaffoldState();
}

class _MainScaffoldState extends State<MainScaffold> with WidgetsBindingObserver {
  int _currentIndex = 0;

  final List<Widget> _screens = const [
    ManageAppScreen(),
    ScreentimeScreen(),
    SettingsScreen(),
  ];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
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
                    height: 64,
                    padding: const EdgeInsets.all(6),
                    decoration: BoxDecoration(
                      color: navBgColor.withOpacity(0.92),
                      borderRadius: BorderRadius.circular(32),
                    ),
                    child: SquashStretchNavbar(
                      currentIndex: _currentIndex,
                      onTap: (i) {
                        if (_currentIndex != i) {
                          HapticFeedback.selectionClick();
                          setState(() {
                            _currentIndex = i;
                          });
                        }
                      },
                      items: [
                        NavItem(icon: Icons.tune_rounded, label: loc.get('navManage')),
                        NavItem(icon: Icons.hourglass_top_rounded, label: loc.get('navScreentime')),
                        NavItem(icon: Icons.settings_rounded, label: loc.get('navSettings')),
                      ],
                      primaryColor: primaryColor,
                      isDark: isDark,
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
