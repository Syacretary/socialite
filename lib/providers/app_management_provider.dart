import 'package:flutter/material.dart';
import '../models/app_item.dart';

class AppManagementProvider extends ChangeNotifier {
  final List<AppItem> _apps = [
    AppItem(
      id: 'instagram',
      name: 'Instagram',
      packageName: 'com.instagram.android',
      icon: Icons.camera_alt_rounded,
      brandColor: const Color(0xFFE1306C),
      category: 'Social Media',
      usageMinutesToday: 98,
      isRestricted: true,
    ),
    AppItem(
      id: 'tiktok',
      name: 'TikTok',
      packageName: 'com.zhiliaoapp.musically',
      icon: Icons.play_arrow_rounded,
      brandColor: const Color(0xFF00F2FE),
      category: 'Video Feed',
      usageMinutesToday: 124,
      isRestricted: true,
    ),
    AppItem(
      id: 'youtube',
      name: 'YouTube',
      packageName: 'com.google.android.youtube',
      icon: Icons.smart_display_rounded,
      brandColor: const Color(0xFFFF0000),
      category: 'Entertainment',
      usageMinutesToday: 72,
      isRestricted: false,
    ),
    AppItem(
      id: 'x_twitter',
      name: 'X (Twitter)',
      packageName: 'com.twitter.android',
      icon: Icons.alternate_email_rounded,
      brandColor: const Color(0xFF1DA1F2),
      category: 'Microblog',
      usageMinutesToday: 45,
      isRestricted: false,
    ),
    AppItem(
      id: 'reddit',
      name: 'Reddit',
      packageName: 'com.reddit.frontpage',
      icon: Icons.forum_rounded,
      brandColor: const Color(0xFFFF4500),
      category: 'Community',
      usageMinutesToday: 32,
      isRestricted: false,
    ),
    AppItem(
      id: 'facebook',
      name: 'Facebook',
      packageName: 'com.facebook.katana',
      icon: Icons.groups_rounded,
      brandColor: const Color(0xFF1877F2),
      category: 'Social Network',
      usageMinutesToday: 20,
      isRestricted: false,
    ),
    AppItem(
      id: 'threads',
      name: 'Threads',
      packageName: 'com.instagram.barcelona',
      icon: Icons.bubble_chart_rounded,
      brandColor: const Color(0xFF8B5CF6),
      category: 'Text Feed',
      usageMinutesToday: 18,
      isRestricted: false,
    ),
    AppItem(
      id: 'snapchat',
      name: 'Snapchat',
      packageName: 'com.snapchat.android',
      icon: Icons.chat_bubble_rounded,
      brandColor: const Color(0xFFFFFC00),
      category: 'Messaging',
      usageMinutesToday: 15,
      isRestricted: false,
    ),
  ];

  bool _isDraggingOverRestrictedZone = false;
  bool _isDraggingOverAvailableZone = false;

  bool get isDraggingOverRestrictedZone => _isDraggingOverRestrictedZone;
  bool get isDraggingOverAvailableZone => _isDraggingOverAvailableZone;

  List<AppItem> get allApps => List.unmodifiable(_apps);
  List<AppItem> get restrictedApps => _apps.where((app) => app.isRestricted).toList();
  List<AppItem> get availableApps => _apps.where((app) => !app.isRestricted).toList();

  void setDraggingOverRestricted(bool isOver) {
    if (_isDraggingOverRestrictedZone != isOver) {
      _isDraggingOverRestrictedZone = isOver;
      notifyListeners();
    }
  }

  void setDraggingOverAvailable(bool isOver) {
    if (_isDraggingOverAvailableZone != isOver) {
      _isDraggingOverAvailableZone = isOver;
      notifyListeners();
    }
  }

  void restrictApp(String appId) {
    final index = _apps.indexWhere((app) => app.id == appId);
    if (index != -1 && !_apps[index].isRestricted) {
      _apps[index].isRestricted = true;
      notifyListeners();
    }
  }

  void unrestrictApp(String appId) {
    final index = _apps.indexWhere((app) => app.id == appId);
    if (index != -1 && _apps[index].isRestricted) {
      _apps[index].isRestricted = false;
      notifyListeners();
    }
  }

  void toggleRestriction(String appId) {
    final index = _apps.indexWhere((app) => app.id == appId);
    if (index != -1) {
      _apps[index].isRestricted = !_apps[index].isRestricted;
      notifyListeners();
    }
  }
}
