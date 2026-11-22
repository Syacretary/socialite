import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
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

  /// Whether persistent config has been loaded from native SharedPreferences.
  bool _configLoaded = false;
  bool _installedChecked = false;

  AppManagementProvider() {
    // Load config first, then check installed apps.
    // syncRules is deferred until BOTH are ready AND after the first frame
    // so the Flutter MethodChannel is guaranteed to be initialized.
    _loadPersistentConfig().then((_) {
      checkInstalledApps();
    });
  }

  /// Called once installed packages are confirmed AND config is loaded.
  void _onReadySync() {
    if (!_configLoaded || !_installedChecked) return;
    // Defer to post-frame so MethodChannel is fully initialized.
    SchedulerBinding.instance.addPostFrameCallback((_) {
      FeatureBlockService.syncRules(this);
    });
  }

  Future<void> _loadPersistentConfig() async {
    try {
      final raw = await FeatureBlockService.loadAppConfig();
      if (raw != null && raw.isNotEmpty) {
        final Map<String, dynamic> configMap = jsonDecode(raw);

        for (var i = 0; i < _apps.length; i++) {
          final appId = _apps[i].id;
          if (!configMap.containsKey(appId)) continue;
          final appData = configMap[appId] as Map<String, dynamic>;

          final isRestricted = _safeBool(appData['isRestricted']) ?? _apps[i].isRestricted;
          final dailyLimitMinutes = _safeInt(appData['dailyLimitMinutes']) ?? _apps[i].dailyLimitMinutes;
          final actionIdx = _safeInt(appData['actionOnLimit']) ?? _apps[i].actionOnLimit.index;
          final actionOnLimit = LimitAction.values[actionIdx.clamp(0, LimitAction.values.length - 1)];

          var features = List<AppFeatureBlock>.from(_apps[i].featureBlocks);
          if (appData.containsKey('features')) {
            final featMap = appData['features'];
            if (featMap is Map<String, dynamic>) {
              for (var j = 0; j < features.length; j++) {
                if (featMap.containsKey(features[j].id)) {
                  features[j] = features[j].copyWith(
                    isBlocked: _safeBool(featMap[features[j].id]) ?? features[j].isBlocked,
                  );
                }
              }
            }
          }

          _apps[i] = _apps[i].copyWith(
            isRestricted: isRestricted,
            dailyLimitMinutes: dailyLimitMinutes,
            actionOnLimit: actionOnLimit,
            featureBlocks: features,
          );
        }
        notifyListeners();
      }
    } catch (_) {}
    _configLoaded = true;
    _onReadySync();
  }

  void _savePersistentConfig() {
    final Map<String, dynamic> configMap = {};
    for (final app in _apps) {
      final featMap = <String, bool>{};
      for (final f in app.featureBlocks) {
        featMap[f.id] = f.isBlocked;
      }
      configMap[app.id] = {
        'packageName': app.packageName,
        'isRestricted': app.isRestricted,
        'dailyLimitMinutes': app.dailyLimitMinutes,
        'actionOnLimit': app.actionOnLimit.index,
        'features': featMap,
      };
    }
    final jsonStr = jsonEncode(configMap);
    FeatureBlockService.saveAppConfig(jsonStr);
    FeatureBlockService.syncRules(this);
  }

  Future<void> checkInstalledApps() async {
    final candidatePkgs = _apps.map((a) => a.packageName).toList();
    final installed = await FeatureBlockService.getInstalledPackages(candidatePkgs);
    _installedPackages = installed.toSet();
    _hasCheckedInstalledApps = true;
    _installedChecked = true;
    notifyListeners();
    _onReadySync();
  }

  /// Returns all configured apps without filtering by installed status.
  /// Used by FeatureBlockService to ensure all active rules are synced to native accessibility service.
  List<AppItem> get rawApps => List.unmodifiable(_apps);

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
      _savePersistentConfig();
    }
  }

  void unrestrictApp(String appId) {
    final index = _apps.indexWhere((app) => app.id == appId);
    if (index != -1 && _apps[index].isRestricted) {
      _apps[index].isRestricted = false;
      notifyListeners();
      _savePersistentConfig();
    }
  }

  void toggleRestriction(String appId) {
    final index = _apps.indexWhere((app) => app.id == appId);
    if (index != -1) {
      _apps[index].isRestricted = !_apps[index].isRestricted;
      notifyListeners();
      _savePersistentConfig();
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
        _savePersistentConfig();
      }
    }
  }

  void updateLimitAction(String appId, LimitAction action) {
    final index = _apps.indexWhere((app) => app.id == appId);
    if (index != -1) {
      if (_apps[index].actionOnLimit != action) {
        _apps[index].actionOnLimit = action;
        notifyListeners();
        _savePersistentConfig();
      }
    }
  }

  void toggleFeatureBlock(String appId, String featureId) {
    final appIndex = _apps.indexWhere((app) => app.id == appId);
    if (appIndex != -1) {
      final features = List<AppFeatureBlock>.from(_apps[appIndex].featureBlocks);
      final featIndex = features.indexWhere((f) => f.id == featureId);
      if (featIndex != -1) {
        final currentBlocked = features[featIndex].isBlocked;
        final targetBlocked = !currentBlocked;
        features[featIndex] = features[featIndex].copyWith(isBlocked: targetBlocked);

        // ── Adaptive What-If-Else Logic ──────────────────────────────
        if (appId == 'instagram') {
          const constituentIds = [
            'hide_stories',
            'disable_home',
            'disable_feeds',
            'disable_reels',
            'disable_friend_reels',
          ];

          if (featureId == 'dm_profile_only') {
            // Master toggle: turning it ON enables all constituent features; OFF disables all
            for (var i = 0; i < features.length; i++) {
              if (constituentIds.contains(features[i].id)) {
                features[i] = features[i].copyWith(isBlocked: targetBlocked);
              }
            }
          } else {
            // Child toggle relationship: disable_friend_reels is a child of disable_reels
            if (featureId == 'disable_reels' && !targetBlocked) {
              final childIdx = features.indexWhere((f) => f.id == 'disable_friend_reels');
              if (childIdx != -1) {
                features[childIdx] = features[childIdx].copyWith(isBlocked: false);
              }
            } else if (featureId == 'disable_friend_reels' && targetBlocked) {
              final parentIdx = features.indexWhere((f) => f.id == 'disable_reels');
              if (parentIdx != -1) {
                features[parentIdx] = features[parentIdx].copyWith(isBlocked: true);
              }
            }

            // If all constituent features are ON, dm_profile_only automatically activates;
            // if any is turned OFF, dm_profile_only turns OFF.
            final allConstituentsBlocked = constituentIds.every((id) {
              final idx = features.indexWhere((f) => f.id == id);
              return idx != -1 && features[idx].isBlocked;
            });

            final masterIdx = features.indexWhere((f) => f.id == 'dm_profile_only');
            if (masterIdx != -1) {
              features[masterIdx] = features[masterIdx].copyWith(isBlocked: allConstituentsBlocked);
            }
          }
        } else if (appId == 'tiktok') {
          const constituentIds = [
            'disable_short_feed',
            'disable_fyp',
            'hide_stories',
            'hide_explore_feeds',
          ];

          if (featureId == 'dm_profile_only') {
            for (var i = 0; i < features.length; i++) {
              if (constituentIds.contains(features[i].id)) {
                features[i] = features[i].copyWith(isBlocked: targetBlocked);
              }
            }
          } else {
            final allConstituentsBlocked = constituentIds.every((id) {
              final idx = features.indexWhere((f) => f.id == id);
              return idx != -1 && features[idx].isBlocked;
            });

            final masterIdx = features.indexWhere((f) => f.id == 'dm_profile_only');
            if (masterIdx != -1) {
              features[masterIdx] = features[masterIdx].copyWith(isBlocked: allConstituentsBlocked);
            }
          }
        }

        _apps[appIndex].featureBlocks = features;
        notifyListeners();
        _savePersistentConfig();
      }
    }
  }

  /// No-op: the native AccessibilityService owns usage tracking and limit enforcement.
  void incrementUsage(String appId) {
    // No longer needed — the native service tracks real usage via UsageStatsManager.
  }

  /// Safely decode a bool from JSON, tolerating 0/1, "true"/"false", or bool.
  static bool? _safeBool(dynamic v) {
    if (v == null) return null;
    if (v is bool) return v;
    if (v is int) return v != 0;
    if (v is double) return v != 0.0;
    if (v is String) {
      final s = v.trim().toLowerCase();
      if (s == 'true' || s == '1') return true;
      if (s == 'false' || s == '0') return false;
    }
    return null;
  }

  /// Safely decode an int from JSON, tolerating doubles (30.0 -> 30).
  static int? _safeInt(dynamic v) {
    if (v == null) return null;
    if (v is int) return v;
    if (v is double) return v.toInt();
    if (v is String) return int.tryParse(v.trim());
    return null;
  }
}
