import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../../core/motion/pressable_scale.dart';
import '../../core/motion/smooth_toggle.dart';
import '../../core/motion/staggered_entrance.dart';
import '../../models/app_item.dart';
import '../../providers/app_management_provider.dart';
import '../../providers/settings_provider.dart';

class AppManagementDrawer extends StatefulWidget {
  final String appId;

  const AppManagementDrawer({super.key, required this.appId});

  static Future<void> show(BuildContext context, String appId) {
    HapticFeedback.lightImpact();
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      enableDrag: true,
      backgroundColor: Colors.transparent,
      barrierColor: Colors.black.withValues(alpha: 0.6),
      builder: (_) => AppManagementDrawer(appId: appId),
    );
  }

  @override
  State<AppManagementDrawer> createState() => _AppManagementDrawerState();
}

class _AppManagementDrawerState extends State<AppManagementDrawer> {
  // 0 = Time Limit, 1 = Features Limit
  int _activeTab = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        Provider.of<SettingsProvider>(context, listen: false).refreshPermissions();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final primaryColor = isDark ? const Color(0xFFFFFFFF) : const Color(0xFF000000);
    final surfaceColor = isDark ? const Color(0xFF0D0D0D) : const Color(0xFFFFFFFF);
    final cardColor = isDark ? const Color(0xFF141414) : const Color(0xFFF3F4F6);
    final screenHeight = MediaQuery.of(context).size.height;

    return Consumer<AppManagementProvider>(
      builder: (context, provider, _) {
        final app = provider.getAppById(widget.appId);
        if (app == null) return const SizedBox.shrink();

        final showTabs = app.hasFeatureBlocks;

        return Align(
          alignment: Alignment.bottomCenter,
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 600),
            child: Container(
              height: screenHeight * 0.80,
              width: double.infinity,
              decoration: BoxDecoration(
                color: surfaceColor,
                borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
              ),
              child: SafeArea(
                top: false,
                child: Column(
                  children: [
                    // ── Grabber
                    GestureDetector(
                      behavior: HitTestBehavior.opaque,
                      onVerticalDragEnd: (d) {
                        if ((d.primaryVelocity ?? 0) > 180) Navigator.of(context).pop();
                      },
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Container(
                            margin: const EdgeInsets.only(top: 10, bottom: 6),
                            width: 36,
                            height: 4,
                            decoration: BoxDecoration(
                              color: isDark ? const Color(0xFF333333) : const Color(0xFFD4D4D4),
                              borderRadius: BorderRadius.circular(4),
                            ),
                          ),
                          Padding(
                            padding: const EdgeInsets.fromLTRB(18, 4, 18, 8),
                            child: _buildAppHeader(context, app, isDark, primaryColor),
                          ),
                        ],
                      ),
                    ),

                    // ── Tab Switcher (only if app has feature blocks)
                    if (showTabs) ...[
                      Padding(
                        padding: const EdgeInsets.fromLTRB(16, 0, 16, 10),
                        child: _buildTabSwitcher(isDark, primaryColor, surfaceColor, cardColor),
                      ),
                    ],

                    // ── Content with bottom feather
                    Expanded(
                      child: Stack(
                        children: [
                          SingleChildScrollView(
                            physics: const BouncingScrollPhysics(),
                            padding: const EdgeInsets.fromLTRB(16, 2, 16, 48),
                            child: AnimatedSwitcher(
                              duration: const Duration(milliseconds: 220),
                              switchInCurve: Curves.easeOutCubic,
                              switchOutCurve: Curves.easeInCubic,
                              transitionBuilder: (child, anim) => FadeTransition(
                                opacity: anim,
                                child: SlideTransition(
                                  position: Tween<Offset>(
                                    begin: const Offset(0, 0.03),
                                    end: Offset.zero,
                                  ).animate(anim),
                                  child: child,
                                ),
                              ),
                              child: showTabs && _activeTab == 1
                                  ? KeyedSubtree(
                                      key: const ValueKey('features'),
                                      child: _buildFeaturesTab(context, app, provider, isDark, primaryColor, cardColor),
                                    )
                                  : KeyedSubtree(
                                      key: const ValueKey('timelimit'),
                                      child: _buildTimeLimitTab(context, app, provider, isDark, primaryColor, cardColor),
                                    ),
                            ),
                          ),

                          // Bottom feather overlay
                          Positioned(
                            bottom: 0, left: 0, right: 0,
                            height: 40,
                            child: IgnorePointer(
                              child: Container(
                                decoration: BoxDecoration(
                                  gradient: LinearGradient(
                                    begin: Alignment.bottomCenter,
                                    end: Alignment.topCenter,
                                    colors: [
                                      surfaceColor,
                                      surfaceColor.withValues(alpha: 0.0),
                                    ],
                                  ),
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
            ),
          ),
        );
      },
    );
  }

  // ── Tab Switcher pill
  Widget _buildTabSwitcher(bool isDark, Color primaryColor, Color surfaceColor, Color cardColor) {
    final tabs = ['Time Limit', 'Features Limit'];
    return Container(
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(
        color: cardColor,
        borderRadius: BorderRadius.circular(14),
      ),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final tabW = (constraints.maxWidth - 6) / 2;
          return Stack(
            children: [
              // Sliding pill indicator
              AnimatedPositioned(
                duration: const Duration(milliseconds: 220),
                curve: Curves.easeOutCubic,
                left: _activeTab == 0 ? 0 : tabW,
                top: 0,
                bottom: 0,
                width: tabW,
                child: Container(
                  decoration: BoxDecoration(
                    color: primaryColor,
                    borderRadius: BorderRadius.circular(11),
                  ),
                ),
              ),
              Row(
                children: tabs.asMap().entries.map((e) {
                  final isSelected = _activeTab == e.key;
                  return Expanded(
                    child: GestureDetector(
                      onTap: () {
                        HapticFeedback.selectionClick();
                        setState(() => _activeTab = e.key);
                      },
                      behavior: HitTestBehavior.opaque,
                      child: SizedBox(
                        height: 36,
                        child: Center(
                          child: Text(
                            e.value,
                            style: TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.w800,
                              letterSpacing: -0.2,
                              color: isSelected
                                  ? (isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF))
                                  : (isDark ? const Color(0xFF777777) : const Color(0xFF888888)),
                            ),
                          ),
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ),
            ],
          );
        },
      ),
    );
  }

  // ── App header row
  Widget _buildAppHeader(BuildContext context, AppItem app, bool isDark, Color primaryColor) {
    final theme = Theme.of(context);
    return Row(
      children: [
        SizedBox(width: 38, height: 38, child: Center(child: app.buildLogo(size: 36, isDark: isDark))),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                app.name,
                style: theme.textTheme.titleLarge?.copyWith(
                  fontSize: 17, fontWeight: FontWeight.w800, letterSpacing: -0.3,
                ),
              ),
              const SizedBox(height: 2),
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                    decoration: BoxDecoration(
                      color: app.isRestricted
                          ? primaryColor
                          : (isDark ? const Color(0xFF1F1F1F) : const Color(0xFFE2E4E8)),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      app.isRestricted ? 'AKTIF' : 'NONAKTIF',
                      style: TextStyle(
                        fontSize: 9, fontWeight: FontWeight.w900, letterSpacing: 0.6,
                        color: app.isRestricted
                            ? (isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF))
                            : (isDark ? const Color(0xFF888888) : const Color(0xFF666666)),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    '${app.formattedUsage} terpakai',
                    style: theme.textTheme.bodyMedium?.copyWith(fontSize: 11, fontWeight: FontWeight.w600),
                  ),
                ],
              ),
            ],
          ),
        ),
        IconButton(
          onPressed: () => Navigator.of(context).pop(),
          icon: Icon(Icons.close_rounded, size: 20, color: primaryColor),
          padding: EdgeInsets.zero,
          constraints: const BoxConstraints(),
        ),
      ],
    );
  }

  // ══════════════════════════════════════════
  // TAB 1: TIME LIMIT
  // ══════════════════════════════════════════
  Widget _buildTimeLimitTab(
    BuildContext context, AppItem app, AppManagementProvider provider,
    bool isDark, Color primaryColor, Color cardColor,
  ) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        StaggeredEntrance(
          index: 0,
          child: _buildDurationSection(context, app, provider, isDark, primaryColor, cardColor),
        ),
        const SizedBox(height: 12),
        StaggeredEntrance(
          index: 1,
          child: _buildActionSection(context, app, provider, isDark, primaryColor, cardColor),
        ),
        const SizedBox(height: 20),
        StaggeredEntrance(
          index: 2,
          child: _buildAutoSavedNote(isDark),
        ),
      ],
    );
  }

  // ══════════════════════════════════════════
  // TAB 2: FEATURES LIMIT
  // ══════════════════════════════════════════
  Widget _buildFeaturesTab(
    BuildContext context, AppItem app, AppManagementProvider provider,
    bool isDark, Color primaryColor, Color cardColor,
  ) {
    final theme = Theme.of(context);
    final settings = Provider.of<SettingsProvider>(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (!settings.accessibilityPermissionGranted) ...[
          Container(
            padding: const EdgeInsets.all(12),
            margin: const EdgeInsets.only(bottom: 12),
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF191919) : const Color(0xFFE8E9EC),
              borderRadius: BorderRadius.circular(16),
            ),
            child: Row(
              children: [
                Icon(Icons.accessibility_new_rounded, size: 20, color: primaryColor),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Izin Aksesibilitas Dibutuhkan',
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w800,
                          color: primaryColor,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        'Aktifkan layanan Lifelit untuk deteksi & blokir fitur ${app.name} secara langsung.',
                        style: TextStyle(
                          fontSize: 9,
                          color: isDark ? const Color(0xFFAAAAAA) : const Color(0xFF555555),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                GestureDetector(
                  onTap: () {
                    HapticFeedback.mediumImpact();
                    settings.requestAccessibilityPermission();
                  },
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                    decoration: BoxDecoration(
                      color: primaryColor,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Text(
                      'Aktifkan',
                      style: TextStyle(
                        fontSize: 10,
                        fontWeight: FontWeight.w800,
                        color: isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(color: cardColor, borderRadius: BorderRadius.circular(20)),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'BLOKIR FITUR SPESIFIK',
                    style: theme.textTheme.labelLarge?.copyWith(
                      fontSize: 10, letterSpacing: 1.2, fontWeight: FontWeight.w800,
                    ),
                  ),
                  Text(
                    '${app.featureBlocks.where((f) => f.isBlocked).length} aktif',
                    style: TextStyle(fontSize: 10, fontWeight: FontWeight.w800, color: primaryColor),
                  ),
                ],
              ),
              const SizedBox(height: 4),
              Text(
                'Pembatasan langsung di dalam ${app.name} — tidak memerlukan batas waktu aktif.',
                style: theme.textTheme.bodyMedium?.copyWith(
                  fontSize: 10,
                  color: isDark ? const Color(0xFF777777) : const Color(0xFF888888),
                ),
              ),
              const SizedBox(height: 14),
              ...app.featureBlocks.map((feat) => _buildFeatureRow(
                context, feat, app, provider, isDark, primaryColor, cardColor,
              )),
            ],
          ),
        ),
        const SizedBox(height: 20),
        _buildAutoSavedNote(isDark),
      ],
    );
  }

  Widget _buildFeatureRow(
    BuildContext context, AppFeatureBlock feat, AppItem app,
    AppManagementProvider provider, bool isDark, Color primaryColor, Color cardColor,
  ) {
    final theme = Theme.of(context);
    final isChildOption = feat.id == 'disable_friend_reels';
    final isMasterMode = feat.id == 'dm_profile_only';

    return Padding(
      padding: EdgeInsets.only(
        top: 4,
        bottom: 4,
        left: isChildOption ? 16 : 0,
      ),
      child: PressableScale(
        onTap: () {
          provider.toggleFeatureBlock(app.id, feat.id);
        },
        pressedScale: 0.98,
        child: Container(
          padding: EdgeInsets.symmetric(
            vertical: isMasterMode ? 8 : 4,
            horizontal: isMasterMode ? 8 : 4,
          ),
          decoration: isMasterMode
              ? BoxDecoration(
                  color: isDark ? const Color(0xFF161616) : const Color(0xFFF3F3F3),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(
                    color: feat.isBlocked
                        ? primaryColor.withValues(alpha: 0.4)
                        : (isDark ? const Color(0xFF262626) : const Color(0xFFE5E5E5)),
                  ),
                )
              : null,
          child: Row(
            children: [
              if (isChildOption) ...[
                Text(
                  '└─ ',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: isDark ? const Color(0xFF666666) : const Color(0xFF999999),
                  ),
                ),
              ],
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Flexible(
                          child: Text(
                            feat.title,
                            style: theme.textTheme.labelLarge?.copyWith(
                              fontSize: isMasterMode ? 13 : 12,
                              fontWeight: isMasterMode ? FontWeight.w800 : FontWeight.w700,
                              color: isMasterMode && feat.isBlocked ? primaryColor : null,
                            ),
                          ),
                        ),
                        if (isMasterMode) ...[
                          const SizedBox(width: 6),
                          AnimatedScale(
                            duration: const Duration(milliseconds: 180),
                            curve: Curves.easeOutBack,
                            scale: feat.isBlocked ? 1.05 : 1.0,
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1.5),
                              decoration: BoxDecoration(
                                color: feat.isBlocked
                                    ? primaryColor.withValues(alpha: 0.15)
                                    : (isDark ? const Color(0xFF262626) : const Color(0xFFE0E0E0)),
                                borderRadius: BorderRadius.circular(4),
                              ),
                              child: Text(
                                'SEMUA FITUR',
                                style: TextStyle(
                                  fontSize: 8.5,
                                  fontWeight: FontWeight.w800,
                                  letterSpacing: 0.4,
                                  color: feat.isBlocked
                                      ? primaryColor
                                      : (isDark ? const Color(0xFF888888) : const Color(0xFF777777)),
                                ),
                              ),
                            ),
                          ),
                        ],
                      ],
                    ),
                    const SizedBox(height: 1),
                    Text(
                      feat.description,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        fontSize: 10,
                        color: isDark ? const Color(0xFF777777) : const Color(0xFF888888),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              // Slow in, slow out toggle with stretch
              SmoothToggle(
                value: feat.isBlocked,
                onChanged: (_) {
                  provider.toggleFeatureBlock(app.id, feat.id);
                },
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ── Auto-saved note
  Widget _buildAutoSavedNote(bool isDark) {
    return Center(
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.check_circle_outline_rounded, size: 13,
              color: isDark ? const Color(0xFF777777) : const Color(0xFF888888)),
          const SizedBox(width: 6),
          Text(
            'Setelan otomatis tersimpan',
            style: TextStyle(
              fontSize: 11, fontWeight: FontWeight.w500,
              color: isDark ? const Color(0xFF777777) : const Color(0xFF888888),
            ),
          ),
        ],
      ),
    );
  }

  // ══════════════════════════════════════════
  // DURATION SECTION
  // ══════════════════════════════════════════
  Widget _buildDurationSection(
    BuildContext context, AppItem app, AppManagementProvider provider,
    bool isDark, Color primaryColor, Color cardColor,
  ) {
    final theme = Theme.of(context);
    const recommendedDurations = [5, 15, 30, 60, 120];

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(color: cardColor, borderRadius: BorderRadius.circular(20)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'BATAS DURASI HARIAN',
                style: theme.textTheme.labelLarge?.copyWith(
                  fontSize: 10, letterSpacing: 1.2, fontWeight: FontWeight.w800,
                ),
              ),
              Text(
                app.formattedLimit,
                style: TextStyle(fontSize: 13, fontWeight: FontWeight.w900, color: primaryColor),
              ),
            ],
          ),
          const SizedBox(height: 10),

          // Recommended pills
          Row(
            children: recommendedDurations.map((minutes) {
              final isSelected = app.dailyLimitMinutes == minutes;
              final label = minutes == 60 ? '1 Jam' : minutes == 120 ? '2 Jam' : '$minutes m';
              return Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 2),
                  child: GestureDetector(
                    onTap: () {
                      HapticFeedback.selectionClick();
                      provider.updateDailyLimit(app.id, minutes);
                    },
                    child: AnimatedContainer(
                      duration: const Duration(milliseconds: 150),
                      padding: const EdgeInsets.symmetric(vertical: 7),
                      decoration: BoxDecoration(
                        color: isSelected
                            ? primaryColor
                            : (isDark ? const Color(0xFF1E1E1E) : const Color(0xFFFFFFFF)),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Center(
                        child: Text(
                          label,
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: isSelected ? FontWeight.w900 : FontWeight.w600,
                            color: isSelected
                                ? (isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF))
                                : primaryColor,
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              );
            }).toList(),
          ),

          const SizedBox(height: 14),

          // Custom slider
          Row(
            children: [
              Text(
                'Atur Sendiri (1m – 2,5j)',
                style: theme.textTheme.bodyMedium?.copyWith(fontSize: 11, fontWeight: FontWeight.w600),
              ),
              const Spacer(),
              Text(
                '${app.dailyLimitMinutes} menit',
                style: theme.textTheme.bodyMedium?.copyWith(
                  fontSize: 11, fontWeight: FontWeight.w700, color: primaryColor,
                ),
              ),
            ],
          ),
          SliderTheme(
            data: SliderThemeData(
              activeTrackColor: primaryColor,
              inactiveTrackColor: isDark ? const Color(0xFF262626) : const Color(0xFFD4D4D4),
              thumbColor: primaryColor,
              overlayColor: primaryColor.withValues(alpha: 0.1),
              trackHeight: 3.5,
              thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 7),
            ),
            child: Slider(
              value: app.dailyLimitMinutes.toDouble().clamp(1.0, 150.0),
              min: 1.0,
              max: 150.0,
              divisions: 149,
              onChanged: (val) => provider.updateDailyLimit(app.id, val.round()),
            ),
          ),
        ],
      ),
    );
  }

  // ══════════════════════════════════════════
  // ACTION SECTION (Bento grid)
  // ══════════════════════════════════════════
  Widget _buildActionSection(
    BuildContext context, AppItem app, AppManagementProvider provider,
    bool isDark, Color primaryColor, Color cardColor,
  ) {
    final theme = Theme.of(context);

    const actions = [
      _ActionOption(action: LimitAction.notifyOnly, title: 'Notifikasi', description: 'Peringatan halus', icon: Icons.notifications_none_rounded),
      _ActionOption(action: LimitAction.popupReminder, title: 'Pop-up', description: 'Jendela intervensi', icon: Icons.filter_none_rounded),
      _ActionOption(action: LimitAction.blockAndClose, title: 'Blokir', description: 'Keluar & kunci', icon: Icons.block_rounded),
    ];

    final activeLabel = {
      LimitAction.notifyOnly: 'Notifikasi Saja',
      LimitAction.popupReminder: 'Pop-up Window',
      LimitAction.blockAndClose: 'Keluar & Blokir',
    }[app.actionOnLimit]!;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(color: cardColor, borderRadius: BorderRadius.circular(20)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'AKSI KETIKA BATAS TERCAPAI',
                style: theme.textTheme.labelLarge?.copyWith(
                  fontSize: 10, letterSpacing: 1.2, fontWeight: FontWeight.w800,
                ),
              ),
              Text(
                activeLabel,
                style: TextStyle(fontSize: 10, fontWeight: FontWeight.w800, color: primaryColor),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Horizontal bento grid
          Row(
            children: actions.asMap().entries.map((entry) {
              final idx = entry.key;
              final opt = entry.value;
              final isSelected = app.actionOnLimit == opt.action;
              return Expanded(
                child: Padding(
                  padding: EdgeInsets.only(
                    left: idx == 0 ? 0 : 3.5,
                    right: idx == actions.length - 1 ? 0 : 3.5,
                  ),
                  child: InkWell(
                    onTap: () {
                      HapticFeedback.selectionClick();
                      provider.updateLimitAction(app.id, opt.action);
                    },
                    borderRadius: BorderRadius.circular(16),
                    child: AnimatedContainer(
                      duration: const Duration(milliseconds: 180),
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 12),
                      decoration: BoxDecoration(
                        color: isSelected
                            ? primaryColor
                            : (isDark ? const Color(0xFF1E1E1E) : const Color(0xFFFFFFFF)),
                        borderRadius: BorderRadius.circular(16),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Icon(
                                opt.icon, size: 18,
                                color: isSelected
                                    ? (isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF))
                                    : primaryColor,
                              ),
                              AnimatedContainer(
                                duration: const Duration(milliseconds: 180),
                                width: 6, height: 6,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: isSelected
                                      ? (isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF))
                                      : Colors.transparent,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 10),
                          Text(
                            opt.title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              fontSize: 11, fontWeight: FontWeight.w800, letterSpacing: -0.2,
                              color: isSelected
                                  ? (isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF))
                                  : primaryColor,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            opt.description,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              fontSize: 9, fontWeight: FontWeight.w500, height: 1.2,
                              color: isSelected
                                  ? (isDark ? const Color(0xFF333333) : const Color(0xFFE0E0E0))
                                  : (isDark ? const Color(0xFF777777) : const Color(0xFF888888)),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
        ],
      ),
    );
  }
}

class _ActionOption {
  final LimitAction action;
  final String title;
  final String description;
  final IconData icon;

  const _ActionOption({
    required this.action,
    required this.title,
    required this.description,
    required this.icon,
  });
}
