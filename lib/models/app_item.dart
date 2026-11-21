import 'package:flutter/material.dart';

enum LimitAction {
  notifyOnly,
  popupReminder,
  blockAndClose,
}

class AppFeatureBlock {
  final String id;
  final String title;
  final String description;
  final bool isBlocked;

  const AppFeatureBlock({
    required this.id,
    required this.title,
    required this.description,
    this.isBlocked = false,
  });

  AppFeatureBlock copyWith({bool? isBlocked}) {
    return AppFeatureBlock(
      id: id,
      title: title,
      description: description,
      isBlocked: isBlocked ?? this.isBlocked,
    );
  }
}

class AppItem {
  final String id;
  final String name;
  final String packageName;
  final IconData icon;
  final String? iconAsset;
  final String? darkIconAsset;
  final bool isMonochrome;
  final Color brandColor;
  final String category;
  final int usageMinutesToday;
  bool isRestricted;

  // Management Drawer Configurations
  int dailyLimitMinutes;
  LimitAction actionOnLimit;
  List<AppFeatureBlock> featureBlocks;

  AppItem({
    required this.id,
    required this.name,
    required this.packageName,
    required this.icon,
    this.iconAsset,
    this.darkIconAsset,
    this.isMonochrome = false,
    required this.brandColor,
    required this.category,
    required this.usageMinutesToday,
    this.isRestricted = false,
    this.dailyLimitMinutes = 30,
    this.actionOnLimit = LimitAction.blockAndClose,
    List<AppFeatureBlock>? featureBlocks,
  }) : featureBlocks = featureBlocks ?? [
          const AppFeatureBlock(
            id: 'vertical_feed',
            title: 'Reels & Video Pendek',
            description: 'Blokir algoritma video vertikal adiktif',
            isBlocked: true,
          ),
          const AppFeatureBlock(
            id: 'explore_tab',
            title: 'Tab Eksplorasi & Pencarian',
            description: 'Cegah rekomendasi konten baru memicu distraksi',
            isBlocked: false,
          ),
          const AppFeatureBlock(
            id: 'comments',
            title: 'Kolom Komentar',
            description: 'Sembunyikan interaksi komentar publik',
            isBlocked: false,
          ),
          const AppFeatureBlock(
            id: 'direct_messages',
            title: 'Pesan Langsung (DM)',
            description: 'Batasi obrolan agar fokus tetap terjaga',
            isBlocked: false,
          ),
        ];

  String get formattedUsage {
    final hours = usageMinutesToday ~/ 60;
    final mins = usageMinutesToday % 60;
    if (hours > 0) {
      return '${hours}j ${mins}m';
    }
    return '${mins}m';
  }

  String get formattedLimit {
    final hours = dailyLimitMinutes ~/ 60;
    final mins = dailyLimitMinutes % 60;
    if (hours > 0 && mins > 0) {
      return '$hours Jam $mins Menit';
    } else if (hours > 0) {
      return '$hours Jam';
    }
    return '$mins Menit';
  }

  Widget buildLogo({double size = 26, bool isDark = false, Color? tintColor}) {
    final asset = (isDark && darkIconAsset != null) ? darkIconAsset! : iconAsset;

    if (asset != null && asset.isNotEmpty) {
      return Image.asset(
        asset,
        width: size,
        height: size,
        fit: BoxFit.contain,
        color: isMonochrome
            ? (tintColor ?? (isDark ? Colors.white : Colors.black))
            : null,
        colorBlendMode: isMonochrome ? BlendMode.srcIn : null,
        errorBuilder: (_, __, ___) => Icon(
          icon,
          size: size,
          color: tintColor ?? (isDark ? Colors.white : Colors.black),
        ),
      );
    }
    return Icon(
      icon,
      size: size,
      color: tintColor ?? (isDark ? Colors.white : Colors.black),
    );
  }

  AppItem copyWith({
    String? id,
    String? name,
    String? packageName,
    IconData? icon,
    String? iconAsset,
    String? darkIconAsset,
    bool? isMonochrome,
    Color? brandColor,
    String? category,
    int? usageMinutesToday,
    bool? isRestricted,
    int? dailyLimitMinutes,
    LimitAction? actionOnLimit,
    List<AppFeatureBlock>? featureBlocks,
  }) {
    return AppItem(
      id: id ?? this.id,
      name: name ?? this.name,
      packageName: packageName ?? this.packageName,
      icon: icon ?? this.icon,
      iconAsset: iconAsset ?? this.iconAsset,
      darkIconAsset: darkIconAsset ?? this.darkIconAsset,
      isMonochrome: isMonochrome ?? this.isMonochrome,
      brandColor: brandColor ?? this.brandColor,
      category: category ?? this.category,
      usageMinutesToday: usageMinutesToday ?? this.usageMinutesToday,
      isRestricted: isRestricted ?? this.isRestricted,
      dailyLimitMinutes: dailyLimitMinutes ?? this.dailyLimitMinutes,
      actionOnLimit: actionOnLimit ?? this.actionOnLimit,
      featureBlocks: featureBlocks ?? this.featureBlocks,
    );
  }
}
