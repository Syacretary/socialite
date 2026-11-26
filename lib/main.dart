import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'core/theme/app_theme.dart';
import 'providers/app_management_provider.dart';
import 'providers/screentime_provider.dart';
import 'providers/settings_provider.dart';
import 'views/main_scaffold.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);

  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => SettingsProvider()),
        ChangeNotifierProvider(create: (_) => AppManagementProvider()),
        ChangeNotifierProvider(create: (_) => ScreentimeProvider()),
      ],
      child: const LifelitApp(),
    ),
  );
}

class LifelitScrollBehavior extends MaterialScrollBehavior {
  const LifelitScrollBehavior();

  @override
  ScrollPhysics getScrollPhysics(BuildContext context) {
    // Rubber banding: pull past the end and it stretches and springs back upon release
    return const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics());
  }
}

class LifelitApp extends StatelessWidget {
  const LifelitApp({super.key});

  @override
  Widget build(BuildContext context) {
    final settings = Provider.of<SettingsProvider>(context);

    return MaterialApp(
      title: 'Lifelit - Stop Doom Scrolling',
      debugShowCheckedModeBanner: false,
      scrollBehavior: const LifelitScrollBehavior(),
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      themeMode: settings.themeMode,
      home: const MainScaffold(),
    );
  }
}
