import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:socialite/main.dart';
import 'package:socialite/providers/app_management_provider.dart';
import 'package:socialite/providers/screentime_provider.dart';
import 'package:socialite/providers/settings_provider.dart';

void main() {
  testWidgets('App renders correctly', (WidgetTester tester) async {
    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => SettingsProvider()),
          ChangeNotifierProvider(create: (_) => AppManagementProvider()),
          ChangeNotifierProvider(create: (_) => ScreentimeProvider()),
        ],
        child: const SocialiteApp(),
      ),
    );
    expect(find.byType(MaterialApp), findsOneWidget);
  });
}
