import 'package:flutter/material.dart';

/// A modular toggle button with slow-in/slow-out (ease-in/ease-out) animation.
///
/// When toggled, the button smoothly slides and changes color with an
/// [Curves.easeInOut] curve — never linear, never stiff.
/// Use [EaseToggleButton] for permission toggles, theme switches, or any
/// boolean setting throughout the app.
class EaseToggleButton extends StatefulWidget {
  final bool value;
  final ValueChanged<bool> onChanged;
  final String onLabel;
  final String offLabel;
  final Color activeColor;
  final Color inactiveColor;
  final Duration duration;
  final double width;
  final double height;

  const EaseToggleButton({
    super.key,
    required this.value,
    required this.onChanged,
    this.onLabel = 'Aktif',
    this.offLabel = 'Nonaktif',
    this.activeColor = const Color(0xFF00F2FE),
    this.inactiveColor = const Color(0xFF1A1A1A),
    this.duration = const Duration(milliseconds: 250),
    this.width = 72,
    this.height = 36,
  });

  @override
  State<EaseToggleButton> createState() => _EaseToggleButtonState();
}

class _EaseToggleButtonState extends State<EaseToggleButton>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: widget.duration);
    if (widget.value) _controller.value = 1.0;
  }

  @override
  void didUpdateWidget(EaseToggleButton oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.value != oldWidget.value) {
      _controller.animateTo(widget.value ? 1.0 : 0.0,
          curve: Curves.easeInOut, duration: widget.duration);
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _toggle() {
    final next = !widget.value;
    widget.onChanged(next);
    _controller.animateTo(next ? 1.0 : 0.0,
        curve: Curves.easeInOut, duration: widget.duration);
  }

  @override
  Widget build(BuildContext context) {
    final trackColor = ColorTween(
      begin: widget.inactiveColor,
      end: widget.activeColor,
    ).animate(CurvedAnimation(parent: _controller, curve: Curves.easeInOut));

    return GestureDetector(
      onTap: _toggle,
      child: AnimatedBuilder(
        animation: _controller,
        builder: (context, child) {
          final thumbX = _controller.value * (widget.width - widget.height);
          return Container(
            width: widget.width,
            height: widget.height,
            decoration: BoxDecoration(
              color: trackColor.value,
              borderRadius: BorderRadius.circular(widget.height / 2),
            ),
            child: Stack(
              children: [
                // Labels
                Positioned(
                  left: 4,
                  top: 0,
                  bottom: 0,
                  child: Center(
                    child: Text(
                      widget.offLabel,
                      style: TextStyle(
                        color: Colors.white54,
                        fontSize: 9,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                ),
                Positioned(
                  right: 4,
                  top: 0,
                  bottom: 0,
                  child: Center(
                    child: Text(
                      widget.onLabel,
                      style: TextStyle(
                        color: Colors.white70,
                        fontSize: 9,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                ),
                // Thumb
                Positioned(
                  left: thumbX,
                  top: 2,
                  bottom: 2,
                  child: Container(
                    width: widget.height - 4,
                    height: widget.height - 4,
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular((widget.height - 4) / 2),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withOpacity(0.25),
                          blurRadius: 4,
                          offset: const Offset(0, 2),
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