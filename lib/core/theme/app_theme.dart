import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class AppTheme {
  // Pure Monochrome Palette (Strict Black & White)
  static const Color amoledBlack = Color(0xFF000000);
  static const Color milkWhite = Color(0xFFF8F9FA);

  static const Color pureWhite = Color(0xFFFFFFFF);
  static const Color pureBlack = Color(0xFF000000);

  // Border & Muted Accents
  static const Color darkBorder = Color(0xFF262626);
  static const Color darkBorderHighlight = Color(0xFFFFFFFF);
  static const Color darkMuted = Color(0xFF888888);

  static const Color lightBorder = Color(0xFFE5E5E5);
  static const Color lightBorderHighlight = Color(0xFF000000);
  static const Color lightMuted = Color(0xFF737373);

  // Dark Theme (True AMOLED Black + Pure White Accent, Invisible Widget Backgrounds)
  static ThemeData get darkTheme {
    final textTheme = GoogleFonts.plusJakartaSansTextTheme(ThemeData.dark().textTheme);
    final headingFont = GoogleFonts.spaceGrotesk;

    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: amoledBlack,
      cardColor: Colors.transparent, // Invisible background
      colorScheme: const ColorScheme.dark(
        surface: amoledBlack,
        onSurface: pureWhite,
        primary: pureWhite, // ACCENT IS PURE WHITE
        onPrimary: amoledBlack,
        secondary: pureWhite,
        error: pureWhite,
        outline: darkBorder,
      ),
      dividerColor: darkBorder,
      textTheme: textTheme.copyWith(
        displayLarge: headingFont(
          fontSize: 38,
          fontWeight: FontWeight.w800,
          color: pureWhite,
          letterSpacing: -1.2,
        ),
        displayMedium: headingFont(
          fontSize: 28,
          fontWeight: FontWeight.w700,
          color: pureWhite,
          letterSpacing: -0.8,
        ),
        titleLarge: headingFont(
          fontSize: 20,
          fontWeight: FontWeight.w700,
          color: pureWhite,
          letterSpacing: -0.4,
        ),
        titleMedium: textTheme.titleMedium?.copyWith(
          fontSize: 16,
          fontWeight: FontWeight.w600,
          color: pureWhite,
        ),
        bodyLarge: textTheme.bodyLarge?.copyWith(
          fontSize: 15,
          color: pureWhite,
          height: 1.4,
        ),
        bodyMedium: textTheme.bodyMedium?.copyWith(
          fontSize: 13,
          color: darkMuted,
          height: 1.4,
        ),
        labelLarge: headingFont(
          fontSize: 14,
          fontWeight: FontWeight.w600,
          color: pureWhite,
          letterSpacing: 0.2,
        ),
      ),
      cardTheme: CardThemeData(
        color: Colors.transparent, // Invisible container
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(24),
          side: const BorderSide(color: darkBorder, width: 1.0),
        ),
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: amoledBlack,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        titleTextStyle: headingFont(
          fontSize: 24,
          fontWeight: FontWeight.w800,
          color: pureWhite,
          letterSpacing: -0.5,
        ),
        iconTheme: const IconThemeData(color: pureWhite),
      ),
    );
  }

  // Light Theme (Putih Susu + Pure Black Accent, Invisible Widget Backgrounds)
  static ThemeData get lightTheme {
    final textTheme = GoogleFonts.plusJakartaSansTextTheme(ThemeData.light().textTheme);
    final headingFont = GoogleFonts.spaceGrotesk;

    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.light,
      scaffoldBackgroundColor: milkWhite,
      cardColor: Colors.transparent, // Invisible background
      colorScheme: const ColorScheme.light(
        surface: milkWhite,
        onSurface: pureBlack,
        primary: pureBlack, // ACCENT IS PURE BLACK
        onPrimary: pureWhite,
        secondary: pureBlack,
        error: pureBlack,
        outline: lightBorder,
      ),
      dividerColor: lightBorder,
      textTheme: textTheme.copyWith(
        displayLarge: headingFont(
          fontSize: 38,
          fontWeight: FontWeight.w800,
          color: pureBlack,
          letterSpacing: -1.2,
        ),
        displayMedium: headingFont(
          fontSize: 28,
          fontWeight: FontWeight.w700,
          color: pureBlack,
          letterSpacing: -0.8,
        ),
        titleLarge: headingFont(
          fontSize: 20,
          fontWeight: FontWeight.w700,
          color: pureBlack,
          letterSpacing: -0.4,
        ),
        titleMedium: textTheme.titleMedium?.copyWith(
          fontSize: 16,
          fontWeight: FontWeight.w600,
          color: pureBlack,
        ),
        bodyLarge: textTheme.bodyLarge?.copyWith(
          fontSize: 15,
          color: pureBlack,
          height: 1.4,
        ),
        bodyMedium: textTheme.bodyMedium?.copyWith(
          fontSize: 13,
          color: lightMuted,
          height: 1.4,
        ),
        labelLarge: headingFont(
          fontSize: 14,
          fontWeight: FontWeight.w600,
          color: pureBlack,
          letterSpacing: 0.2,
        ),
      ),
      cardTheme: CardThemeData(
        color: Colors.transparent, // Invisible container
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(24),
          side: const BorderSide(color: lightBorder, width: 1.0),
        ),
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: milkWhite,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        titleTextStyle: headingFont(
          fontSize: 24,
          fontWeight: FontWeight.w800,
          color: pureBlack,
          letterSpacing: -0.5,
        ),
        iconTheme: const IconThemeData(color: pureBlack),
      ),
    );
  }
}
