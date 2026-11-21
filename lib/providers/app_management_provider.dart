import 'package:flutter/material.dart';
import '../models/app_item.dart';
import '../services/feature_block_service.dart';

class AppManagementProvider extends ChangeNotifier {
  final List<AppItem> _apps = [
    AppItem(
      id: 'instagram',
      name: 'Instagram',
      packageName: 'com.instagram.android',
      icon: Icons.camera_alt_rounded,
      iconAsset: 'assets/icons/instagram.png',
      brandColor: const Color(0xFFE1306C),
      category: 'Social Media',
      usageMinutesToday: 98,
      isRestricted: true,
      dailyLimitMinutes: 30,
      actionOnLimit: LimitAction.blockAndClose,
    ),
    AppItem(
      id: 'tiktok',
      name: 'TikTok',
      packageName: 'com.zhiliaoapp.musically',
      icon: Icons.play_arrow_rounded,
      iconAsset: 'assets/icons/tiktok.png',
      brandColor: const Color(0xFF00F2FE),
      category: 'Video Feed',
      usageMinutesToday: 124,
      isRestricted: true,
      dailyLimitMinutes: 15,
      actionOnLimit: LimitAction.blockAndClose,
    ),
    AppItem(
      id: 'youtube',
      name: 'YouTube',
      packageName: 'com.google.android.youtube',
      icon: Icons.smart_display_rounded,
      iconAsset: 'assets/icons/youtube.png',
      brandColor: const Color(0xFFFF0000),
      category: 'Entertainment',
      usageMinutesToday: 72,
      isRestricted: false,
      dailyLimitMinutes: 60,
      actionOnLimit: LimitAction.popupReminder,
    ),
    AppItem(
      id: 'x_twitter',
      name: 'X (Twitter)',
      packageName: 'com.twitter.android',
      icon: Icons.alternate_email_rounded,
      iconAsset: 'assets/icons/x_twitter.png',
      darkIconAsset: 'assets/icons/x_twitter_white.png',
      isMonochrome: true,
      brandColor: const Color(0xFF1DA1F2),
      category: 'Microblog',
      usageMinutesToday: 45,
      isRestricted: false,
      dailyLimitMinutes: 30,
      actionOnLimit: LimitAction.notifyOnly,
    ),
    AppItem(
      id: 'reddit',
      name: 'Reddit',
      packageName: 'com.reddit.frontpage',
      icon: Icons.forum_rounded,
      iconAsset: 'assets/icons/reddit.png',
      brandColor: const Color(0xFFFF4500),
      category: 'Community',
      usageMinutesToday: 32,
      isRestricted: false,
      dailyLimitMinutes: 45,
      actionOnLimit: LimitAction.popupReminder,
    ),
    AppItem(
      id: 'facebook',
      name: 'Facebook',
      packageName: 'com.facebook.katana',
      icon: Icons.groups_rounded,
      iconAsset: 'assets/icons/facebook.png',
      brandColor: const Color(0xFF1877F2),
      category: 'Social Network',
      usageMinutesToday: 20,
      isRestricted: false,
      dailyLimitMinutes: 30,
      actionOnLimit: LimitAction.blockAndClose,
    ),
    AppItem(
      id: 'threads',
      name: 'Threads',
      packageName: 'com.instagram.barcelona',
      icon: Icons.bubble_chart_rounded,
      iconAsset: 'assets/icons/threads.png',
      darkIconAsset: 'assets/icons/threads_white.png',
      isMonochrome: true,
      brandColor: const Color(0xFF8B5CF6),
      category: 'Text Feed',
      usageMinutesToday: 18,
      isRestricted: false,
      dailyLimitMinutes: 20,
      actionOnLimit: LimitAction.notifyOnly,
    ),
    AppItem(
      id: 'snapchat',
      name: 'Snapchat',
      packageName: 'com.snapchat.android',
      icon: Icons.chat_bubble_rounded,
      iconAsset: 'assets/icons/snapchat.png',
      brandColor: const Color(0xFFFFFC00),
      category: 'Messaging',
      usageMinutesToday: 15,
      isRestricted: false,
      dailyLimitMinutes: 15,
      actionOnLimit: LimitAction.blockAndClose,
    ),
  ];

  bool _isDraggingOverRestrictedZone = false;
  bool _isDraggingOverAvailableZone = false;

  Set<String> _installedPackages = {};
  bool _hasCheckedInstalledApps = false;

  bool get isDraggingOverRestrictedZone => _isDraggingOverRestrictedZone;
  bool get isDraggingOverAvailableZone => _isDraggingOverAvailableZone;
  bool get hasCheckedInstalledApps => _hasCheckedInstalledApps;

  AppManagementProvider() {
    checkInstalledApps();
  }

  Future<void> checkInstalledApps() async {
    final candidatePkgs = _apps.map((a) => a.packageName).toList();
    final installed = await FeatureBlockService.getInstalledPackages(candidatePkgs);
    _installedPackages = installed.toSet();
    _hasCheckedInstalledApps = true;
    notifyListeners();
  }

  List<AppItem> get allApps {
    if (!_hasCheckedInstalledApps) return List.unmodifiable(_apps);
    return List.unmodifiable(
      _apps.where((app) => _installedPackages.contains(app.packageName)),
    );
  }

  List<AppItem> get restrictedApps => allApps.where((app) => app.isRestricted).toList();
  List<AppItem> get availableApps => allApps.where((app) => !app.isRestricted).toList();

  AppItem? getAppById(String id) {
    try {
      return _apps.firstWhere((app) => app.id == id);
    } catch (_) {
      return null;
    }
  }

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

  // --- Real-time Auto-save Management Methods ---

  void updateDailyLimit(String appId, int minutes) {
    final index = _apps.indexWhere((app) => app.id == appId);
    if (index != -1) {
      final clamped = minutes.clamp(1, 150); // 1 min to 2.5 hours
      if (_apps[index].dailyLimitMinutes != clamped) {
        _apps[index].dailyLimitMinutes = clamped;
        notifyListeners();
      }
    }
  }

  void updateLimitAction(String appId, LimitAction action) {
    final index = _apps.indexWhere((app) => app.id == appId);
    if (index != -1) {
      if (_apps[index].actionOnLimit != action) {
        _apps[index].actionOnLimit = action;
        notifyListeners();
      }
    }
  }

  void toggleFeatureBlock(String appId, String featureId) {
    final appIndex = _apps.indexWhere((app) => app.id == appId);
    if (appIndex != -1) {
      final features = List<AppFeatureBlock>.from(_apps[appIndex].featureBlocks);
      final featIndex = features.indexWhere((f) => f.id == featureId);
      if (featIndex != -1) {
        features[featIndex] = features[featIndex].copyWith(
          isBlocked: !features[featIndex].isBlocked,
        );
        _apps[appIndex].featureBlocks = features;
        notifyListeners();
        FeatureBlockService.syncRules(this);
      }
    }
  }

  /// Simulate one minute of usage for a restricted app (called by LimitEnforcementService).
  void incrementUsage(String appId) {
    final index = _apps.indexWhere((app) => app.id == appId);
    if (index != -1) {
      // usageMinutesToday is final on the model; we replace the item
      final old = _apps[index];
      _apps[index] = old.copyWith(
        usageMinutesToday: old.usageMinutesToday + 1,
      );
      notifyListeners();
    }
  }
}
