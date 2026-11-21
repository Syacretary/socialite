import 'package:flutter/material.dart';
import '../models/screentime_data.dart';
import 'app_management_provider.dart';

class ScreentimeProvider extends ChangeNotifier {
  bool _hasUsagePermission = false;
  bool get hasUsagePermission => _hasUsagePermission;

  void grantUsagePermission() {
    _hasUsagePermission = true;
    notifyListeners();
  }

  void toggleUsagePermission() {
    _hasUsagePermission = !_hasUsagePermission;
    notifyListeners();
  }

  int calculateTotalUsageMinutes(AppManagementProvider appsProvider) {
    int total = 0;
    for (var app in appsProvider.allApps) {
      total += app.usageMinutesToday;
    }
    return total;
  }

  String formatMinutes(int minutes, {String hourSuffix = 'j', String minSuffix = 'm'}) {
    final h = minutes ~/ 60;
    final m = minutes % 60;
    if (h > 0) {
      return '$h$hourSuffix $m$minSuffix';
    }
    return '$m$minSuffix';
  }

  List<DailyUsagePoint> get weeklyTrend {
    return const [
      DailyUsagePoint(dayName: 'Sen', totalMinutes: 280, ratioOfMax: 0.70),
      DailyUsagePoint(dayName: 'Sel', totalMinutes: 340, ratioOfMax: 0.85),
      DailyUsagePoint(dayName: 'Rab', totalMinutes: 400, ratioOfMax: 1.0),
      DailyUsagePoint(dayName: 'Kam', totalMinutes: 310, ratioOfMax: 0.77),
      DailyUsagePoint(dayName: 'Jum', totalMinutes: 260, ratioOfMax: 0.65),
      DailyUsagePoint(dayName: 'Sab', totalMinutes: 380, ratioOfMax: 0.95),
      DailyUsagePoint(dayName: 'Min', totalMinutes: 324, ratioOfMax: 0.81),
    ];
  }
}
