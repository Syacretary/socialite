import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Slow in, slow out toggle with subtle thumb stretch in motion.
/// Follows:
/// - "Slow in, slow out: dynamic movement characteristics appear less robotic"
/// - "Timing: Keep UI animations under 300ms (all concept animations)"
/// - "Frequency of use: the more often something is used, the less animation you should put in it"
/// - "Secondary action: a small flourish that backs up the main action"
/// - "Peak-end rule: people remember the best part and the ending"
class SmoothToggle extends StatefulWidget {
  final bool value;
  final ValueChanged<bool>? onChanged;
  final Color? activeColor;
  final Color? inactiveColor;
  final Color? thumbColor;

  const SmoothToggle({
    super.key,
    required this.value,
    required this.onChanged,
    this.activeColor,
    this.inactiveColor,
    this.thumbColor,
  });

  @override
  State<SmoothToggle> createState() => _SmoothToggleState();
}

class _SmoothToggleState extends State<SmoothToggle>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _position;
  late final Animation<double> _stretch;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 210),
      value: widget.value ? 1.0 : 0.0,
    );

    // Slow in, slow out curve (easeInOutCubic)
    _position = CurvedAnimation(
      parent: _controller,
      curve: Curves.easeInOutCubic,
    );

    // Stretch mid-flight (squash and stretch during toggle movement)
    _stretch = TweenSequence<double>([
      TweenSequenceItem(
        tween: Tween<double>(begin: 0.0, end: 1.0)
            .chain(CurveTween(curve: Curves.easeInQuad)),
        weight: 50,
      ),
      TweenSequenceItem(
        tween: Tween<double>(begin: 1.0, end: 0.0)
            .chain(CurveTween(curve: Curves.easeOutQuad)),
        weight: 50,
      ),
    ]).animate(_controller);
  }

  @override
  void didUpdateWidget(covariant SmoothToggle oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.value != widget.value) {
      if (widget.value) {
        _controller.forward();
      } else {
        _controller.reverse();
      }
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _handleTap() {
    if (widget.onChanged == null) return;
    HapticFeedback.lightImpact();
    widget.onChanged!(!widget.value);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    final primaryColor = widget.activeColor ??
        (isDark ? const Color(0xFFFFFFFF) : const Color(0xFF000000));
    final inactiveBg = widget.inactiveColor ??
        (isDark ? const Color(0xFF242424) : const Color(0xFFD6D8DC));

    const trackWidth = 42.0;
    const trackHeight = 24.0;
    const thumbBaseSize = 18.0;
    const maxStretch = 3.5; // thumb stretches by 3.5px during travel

    return GestureDetector(
      onTap: _handleTap,
      behavior: HitTestBehavior.opaque,
      child: AnimatedBuilder(
        animation: _controller,
        builder: (context, _) {
          final t = _position.value;
          final currentBg = Color.lerp(inactiveBg, primaryColor, t)!;
          final thumbWidth = thumbBaseSize + (_stretch.value * maxStretch);
          final travelDistance = trackWidth - 6.0 - thumbWidth;
          final thumbLeft = 3.0 + (t * travelDistance);

          final thumbCol = widget.thumbColor ??
              (Color.lerp(
                isDark ? const Color(0xFF777777) : const Color(0xFFFFFFFF),
                isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF),
                t,
              )!);

          return Container(
            width: trackWidth,
            height: trackHeight,
            decoration: BoxDecoration(
              color: currentBg,
              borderRadius: BorderRadius.circular(14),
            ),
            child: Stack(
              alignment: Alignment.centerLeft,
              children: [
                Positioned(
                  left: thumbLeft,
                  child: Container(
                    width: thumbWidth,
                    height: thumbBaseSize,
                    decoration: BoxDecoration(
                      color: thumbCol,
                      borderRadius: BorderRadius.circular(thumbBaseSize / 2),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withOpacity(0.12),
                          blurRadius: 4,
                          offset: const Offset(0, 1.5),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}
