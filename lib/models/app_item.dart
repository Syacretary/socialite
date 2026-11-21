import 'package:flutter/material.dart';

enum LimitAction {
  notifyOnly,
  popupReminder,
  blockAndClose,
}

/// A single toggleable feature restriction for an app.
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

/// Per-app feature block definitions (only for supported apps).
Map<String, List<AppFeatureBlock>> defaultFeatureBlocksFor(String appId) {
  switch (appId) {
    case 'instagram':
      return {
        appId: const [
          AppFeatureBlock(
            id: 'dm_profile_only',
            title: 'DM & Profile Only',
            description: 'Nonaktifkan semua konten kecuali DM dan halaman profil.',
          ),
          AppFeatureBlock(
            id: 'hide_stories',
            title: 'Sembunyikan Stories',
            description: 'Hapus baris Stories dari tampilan beranda.',
          ),
          AppFeatureBlock(
            id: 'disable_home',
            title: 'Matikan Beranda',
            description: 'Blokir tab beranda, otomatis diarahkan ke halaman yang tersedia.',
          ),
          AppFeatureBlock(
            id: 'disable_feeds',
            title: 'Matikan Feeds',
            description: 'Tutup scroll feeds dengan overlay menyatu, search/explore tetap terlihat.',
          ),
          AppFeatureBlock(
            id: 'disable_reels',
            title: 'Matikan Reels',
            description: 'Blokir tab Reels, arahkan ke konten yang diizinkan.',
          ),
          AppFeatureBlock(
            id: 'disable_friend_reels',
            title: 'Matikan Reels Teman',
            description: 'Sembunyikan Reels yang dikirim atau diteruskan oleh teman.',
          ),
        ],
      };
    case 'youtube':
      return {
        appId: const [
          AppFeatureBlock(
            id: 'disable_shorts',
            title: 'Matikan Shorts',
            description: 'Blokir tab dan rekomendasi video pendek YouTube Shorts.',
          ),
          AppFeatureBlock(
            id: 'disable_home',
            title: 'Beranda / Search Only',
            description: 'Tutup feed beranda dengan overlay, hanya bilah pencarian & navbar terlihat.',
          ),
        ],
      };
    case 'tiktok':
      return {
        appId: const [
          AppFeatureBlock(
            id: 'dm_profile_only',
            title: 'DM & Profile Only',
            description: 'Nonaktifkan semua konten kecuali DM dan halaman profil.',
          ),
          AppFeatureBlock(
            id: 'disable_short_feed',
            title: 'Matikan Video Pendek',
            description: 'Skip otomatis video pendek, hanya video panjang yang diputar.',
          ),
          AppFeatureBlock(
            id: 'disable_fyp',
            title: 'Matikan FYP',
            description: 'Sembunyikan For You Page, hanya tampilkan konten Following.',
          ),
          AppFeatureBlock(
            id: 'hide_stories',
            title: 'Sembunyikan Stories',
            description: 'Hapus baris Stories dari tampilan utama.',
          ),
          AppFeatureBlock(
            id: 'hide_explore_feeds',
            title: 'Sembunyikan Feeds Explore',
            description: 'Tutup feed di halaman eksplorasi dengan overlay.',
          ),
        ],
      };
    case 'x_twitter':
      return {
        appId: const [
          AppFeatureBlock(
            id: 'disable_for_you',
            title: 'Feed "For You"',
            description: 'Tutup tab linimasa yang dikurasi algoritma.',
          ),
          AppFeatureBlock(
            id: 'disable_endless_scroll',
            title: 'Endless Scroll Media',
            description: 'Putus swipe tanpa batas di penampil foto & video.',
          ),
          AppFeatureBlock(
            id: 'hide_trending',
            title: 'Trending Topics',
            description: 'Sembunyikan daftar topik tren di Explore.',
          ),
        ],
      };
    default:
      return {}; // No feature blocks for unsupported apps
  }
}

/// Returns true for apps that have configurable feature blocks.
bool appHasFeatureBlocks(String appId) {
  return ['instagram', 'youtube', 'tiktok', 'x_twitter'].contains(appId);
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
  int usageMinutesToday;
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
  }) : featureBlocks = featureBlocks ??
            (defaultFeatureBlocksFor(id)[id] ?? []);

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

  bool get hasFeatureBlocks => featureBlocks.isNotEmpty;

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
