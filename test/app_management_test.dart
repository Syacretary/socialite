import 'package:flutter_test/flutter_test.dart';
import 'package:socialite/providers/app_management_provider.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('AppManagementProvider installed apps tests', () {
    test('AppManagementProvider initializes with candidate apps and filters correctly', () async {
      final provider = AppManagementProvider();
      // Initial state before platform channel check completes
      expect(provider.allApps, isNotEmpty);

      // Verify restricting and unrestricting
      final initialAvailableCount = provider.availableApps.length;
      if (initialAvailableCount > 0) {
        final firstApp = provider.availableApps.first;
        provider.restrictApp(firstApp.id);
        expect(provider.restrictedApps.any((a) => a.id == firstApp.id), isTrue);

        provider.unrestrictApp(firstApp.id);
        expect(provider.availableApps.any((a) => a.id == firstApp.id), isTrue);
      }
    });
  });
}
