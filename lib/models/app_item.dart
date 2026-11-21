import 'package:flutter/material.dart';

class AppItem {
  final String id;
  final String name;
  final String packageName;
  final IconData icon;
  final Color brandColor;
  final String category;
  final int usageMinutesToday;
  bool isRestricted;

  AppItem({
    required this.id,
    required this.name,
    required this.packageName,
    required this.icon,
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

  AppItem copyWith({
    String? id,
    String? name,
    String? packageName,
    IconData? icon,
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
      brandColor: brandColor ?? this.brandColor,
      category: category ?? this.category,
      usageMinutesToday: usageMinutesToday ?? this.usageMinutesToday,
      isRestricted: isRestricted ?? this.isRestricted,
    );
  }
}
