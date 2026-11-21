import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../../models/app_item.dart';
import '../../providers/app_management_provider.dart';

class AppManagementDrawer extends StatelessWidget {
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
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final primaryColor = isDark ? const Color(0xFFFFFFFF) : const Color(0xFF000000);
    final surfaceColor = isDark ? const Color(0xFF0D0D0D) : const Color(0xFFFFFFFF);
    final cardColor = isDark ? const Color(0xFF141414) : const Color(0xFFF3F4F6);
    final screenHeight = MediaQuery.of(context).size.height;

    return Consumer<AppManagementProvider>(
      builder: (context, provider, _) {
        final app = provider.getAppById(appId);
        if (app == null) return const SizedBox.shrink();

        // Langsung menutupi 3/4 layar atau lebih (~80%) dari dasar layar ke atas (Bukan floating window)
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
                    // Grabber indicator & Header (swipe down to dismiss)
                    GestureDetector(
                      behavior: HitTestBehavior.opaque,
                      onVerticalDragEnd: (details) {
                        if ((details.primaryVelocity ?? 0) > 180) {
                          Navigator.of(context).pop();
                        }
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
                            child: _buildAppHeader(context, app, provider, isDark, primaryColor),
                          ),
                        ],
                      ),
                    ),

                    // Scrollable configuration content
                    Expanded(
                      child: SingleChildScrollView(
                        physics: const BouncingScrollPhysics(),
                        padding: const EdgeInsets.fromLTRB(16, 2, 16, 20),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            // SECTION 1: BATAS DURASI HARIAN (Recommended & Custom)
                            _buildDurationSection(context, app, provider, isDark, primaryColor, cardColor),

                            const SizedBox(height: 12),

                            // SECTION 2: AKSI KETIKA BATAS TERCAPAI
                            _buildActionSection(context, app, provider, isDark, primaryColor, cardColor),

                            const SizedBox(height: 12),

                            // SECTION 3: BLOKIR FITUR SPESIFIK APLIKASI
                            _buildFeatureBlockSection(context, app, provider, isDark, primaryColor, cardColor),

                            const SizedBox(height: 16),

                            // AUTO-SAVED NOTIFICATION
                            Center(
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Icon(
                                    Icons.check_circle_outline_rounded,
                                    size: 13,
                                    color: isDark ? const Color(0xFF777777) : const Color(0xFF888888),
                                  ),
                                  const SizedBox(width: 6),
                                  Text(
                                    'Setelan otomatis tersimpan',
                                    style: theme.textTheme.bodyMedium?.copyWith(
                                      fontSize: 11,
                                      fontWeight: FontWeight.w500,
                                      color: isDark ? const Color(0xFF777777) : const Color(0xFF888888),
                                    ),
                                  ),
                                ],
                              ),
                            ),
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
      },
    );
  }

  Widget _buildAppHeader(
    BuildContext context,
    AppItem app,
    AppManagementProvider provider,
    bool isDark,
    Color primaryColor,
  ) {
    final theme = Theme.of(context);

    return Row(
      children: [
        // Enlarged unboxed authentic app logo
        SizedBox(
          width: 38,
          height: 38,
          child: Center(
            child: app.buildLogo(size: 36, isDark: isDark),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                app.name,
                style: theme.textTheme.titleLarge?.copyWith(
                  fontSize: 17,
                  fontWeight: FontWeight.w800,
                  letterSpacing: -0.3,
                ),
              ),
              const SizedBox(height: 2),
              // DYNAMIC STATUS BADGE (Active if in Restricted Zone, Dormant if in Available)
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
                        fontSize: 9,
                        fontWeight: FontWeight.w900,
                        letterSpacing: 0.6,
                        color: app.isRestricted
                            ? (isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF))
                            : (isDark ? const Color(0xFF888888) : const Color(0xFF666666)),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    '${app.formattedUsage} terpakai',
                    style: theme.textTheme.bodyMedium?.copyWith(
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                    ),
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

  // --- SECTION 1: BATAS DURASI (Recommended & Custom Slider) ---

  Widget _buildDurationSection(
    BuildContext context,
    AppItem app,
    AppManagementProvider provider,
    bool isDark,
    Color primaryColor,
    Color cardColor,
  ) {
    final theme = Theme.of(context);
    final recommendedDurations = [5, 15, 30, 60, 120];

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: cardColor,
        borderRadius: BorderRadius.circular(20),
        // NO OUTLINES
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'BATAS DURASI HARIAN',
                style: theme.textTheme.labelLarge?.copyWith(
                  fontSize: 10,
                  letterSpacing: 1.2,
                  fontWeight: FontWeight.w800,
                ),
              ),
              Text(
                app.formattedLimit,
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w900,
                  color: primaryColor,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),

          // Recommended Pills: 5m, 15m, 30m, 1j, 2j
          Row(
            children: recommendedDurations.map((minutes) {
              final isSelected = app.dailyLimitMinutes == minutes;
              String label;
              if (minutes == 60) {
                label = '1 Jam';
              } else if (minutes == 120) {
                label = '2 Jam';
              } else {
                label = '$minutes m';
              }

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

          // Custom Duration Slider: 1 min to 150 min (2.5 hours)
          Row(
            children: [
              Text(
                'Atur Sendiri (1m - 2,5j)',
                style: theme.textTheme.bodyMedium?.copyWith(
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const Spacer(),
              Text(
                '${app.dailyLimitMinutes} menit',
                style: theme.textTheme.bodyMedium?.copyWith(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  color: primaryColor,
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
              onChanged: (val) {
                provider.updateDailyLimit(app.id, val.round());
              },
            ),
          ),
        ],
      ),
    );
  }

  // --- SECTION 2: AKSI KETIKA BATAS TERCAPAI ---

  Widget _buildActionSection(
    BuildContext context,
    AppItem app,
    AppManagementProvider provider,
    bool isDark,
    Color primaryColor,
    Color cardColor,
  ) {
    final theme = Theme.of(context);

    final actions = [
      const _ActionOption(
        action: LimitAction.notifyOnly,
        title: 'Hanya Notifikasi',
        description: 'Kirim peringatan halus tanpa mengganggu sesi penggunaan.',
      ),
      const _ActionOption(
        action: LimitAction.popupReminder,
        title: 'Pop-up Window',
        description: 'Tampilkan jendela intervensi pengingat kesadaran digital.',
      ),
      const _ActionOption(
        action: LimitAction.blockAndClose,
        title: 'Langsung Keluar & Blokir',
        description: 'Tutup paksa aplikasi secara instan dan kunci akses hari ini.',
      ),
    ];

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: cardColor,
        borderRadius: BorderRadius.circular(20),
        // NO OUTLINES
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'AKSI KETIKA BATAS TERCAPAI',
            style: theme.textTheme.labelLarge?.copyWith(
              fontSize: 10,
              letterSpacing: 1.2,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 10),

          Column(
            children: actions.map((opt) {
              final isSelected = app.actionOnLimit == opt.action;

              return Padding(
                padding: const EdgeInsets.only(bottom: 6),
                child: InkWell(
                  onTap: () {
                    HapticFeedback.selectionClick();
                    provider.updateLimitAction(app.id, opt.action);
                  },
                  borderRadius: BorderRadius.circular(14),
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 150),
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                    decoration: BoxDecoration(
                      color: isSelected
                          ? (isDark ? const Color(0xFF1E1E1E) : const Color(0xFFFFFFFF))
                          : Colors.transparent,
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: Row(
                      children: [
                        Container(
                          width: 16,
                          height: 16,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: isSelected ? primaryColor : Colors.transparent,
                            border: isSelected
                                ? null
                                : Border.all(
                                    color: isDark ? const Color(0xFF444444) : const Color(0xFFCCCCCC),
                                    width: 1.5,
                                  ),
                          ),
                          child: isSelected
                              ? Icon(
                                  Icons.check_rounded,
                                  size: 11,
                                  color: isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF),
                                )
                              : null,
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                opt.title,
                                style: theme.textTheme.labelLarge?.copyWith(
                                  fontSize: 12,
                                  fontWeight: isSelected ? FontWeight.w800 : FontWeight.w600,
                                ),
                              ),
                              const SizedBox(height: 1),
                              Text(
                                opt.description,
                                style: theme.textTheme.bodyMedium?.copyWith(
                                  fontSize: 10,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
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

  // --- SECTION 3: BLOKIR FITUR SPESIFIK APLIKASI ---

  Widget _buildFeatureBlockSection(
    BuildContext context,
    AppItem app,
    AppManagementProvider provider,
    bool isDark,
    Color primaryColor,
    Color cardColor,
  ) {
    final theme = Theme.of(context);

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: cardColor,
        borderRadius: BorderRadius.circular(20),
        // NO OUTLINES
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'BLOKIR FITUR SPESIFIK',
                style: theme.textTheme.labelLarge?.copyWith(
                  fontSize: 10,
                  letterSpacing: 1.2,
                  fontWeight: FontWeight.w800,
                ),
              ),
              Text(
                'Komponen Adiktif',
                style: theme.textTheme.bodyMedium?.copyWith(
                  fontSize: 10,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),

          Column(
            children: app.featureBlocks.map((feat) {
              return Padding(
                padding: const EdgeInsets.symmetric(vertical: 4),
                child: InkWell(
                  onTap: () {
                    HapticFeedback.lightImpact();
                    provider.toggleFeatureBlock(app.id, feat.id);
                  },
                  borderRadius: BorderRadius.circular(12),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4, horizontal: 4),
                    child: Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                feat.title,
                                style: theme.textTheme.labelLarge?.copyWith(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                              Text(
                                feat.description,
                                style: theme.textTheme.bodyMedium?.copyWith(
                                  fontSize: 10,
                                ),
                              ),
                            ],
                          ),
                        ),
                        // Modern borderless toggle switch
                        AnimatedContainer(
                          duration: const Duration(milliseconds: 180),
                          width: 38,
                          height: 22,
                          padding: const EdgeInsets.all(2.5),
                          decoration: BoxDecoration(
                            color: feat.isBlocked
                                ? primaryColor
                                : (isDark ? const Color(0xFF262626) : const Color(0xFFD4D4D4)),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: AnimatedAlign(
                            duration: const Duration(milliseconds: 180),
                            curve: Curves.easeOutCubic,
                            alignment: feat.isBlocked ? Alignment.centerRight : Alignment.centerLeft,
                            child: Container(
                              width: 17,
                              height: 17,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: feat.isBlocked
                                    ? (isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF))
                                    : (isDark ? const Color(0xFF888888) : const Color(0xFFFFFFFF)),
                              ),
                            ),
                          ),
                        ),
                      ],
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

  const _ActionOption({
    required this.action,
    required this.title,
    required this.description,
  });
}
