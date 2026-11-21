import 'package:flutter/material.dart';

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
  });

  String get formattedUsage {
    final hours = usageMinutesToday ~/ 60;
    final mins = usageMinutesToday % 60;
    if (hours > 0) {
      return '${hours}j ${mins}m';
    }
    return '${mins}m';
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
    );
  }
}
