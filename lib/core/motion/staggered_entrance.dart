import 'package:flutter/material.dart';

/// Staggers content entry one after another under 300ms total using Animation Interval.
/// Follows:
/// - "Timing: Keep UI animations under 300ms"
/// - "Staggering: bring things in one after another, not all in unison (all content in this app)"
/// - "Slow in, slow out: dynamic movement characteristics appear less robotic"
/// - Uses zero background Timers for 100% test purity and leak-free performance.
class StaggeredEntrance extends StatefulWidget {
  final int index;
  final Widget child;
  final Duration totalDuration;
  final Offset offset;

  const StaggeredEntrance({
    super.key,
    required this.index,
    required this.child,
    this.totalDuration = const Duration(milliseconds: 280),
    this.offset = const Offset(0, 14),
  });

  @override
  State<StaggeredEntrance> createState() => _StaggeredEntranceState();
}

class _StaggeredEntranceState extends State<StaggeredEntrance>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _opacity;
  late final Animation<Offset> _slide;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: widget.totalDuration);

    final start = (widget.index * 0.12).clamp(0.0, 0.55);
    final end = (start + 0.55).clamp(0.0, 1.0);

    final curved = CurvedAnimation(
      parent: _controller,
      curve: Interval(start, end, curve: Curves.easeOutCubic),
    );

    _opacity = Tween<double>(begin: 0.0, end: 1.0).animate(curved);
    _slide = Tween<Offset>(begin: widget.offset, end: Offset.zero).animate(curved);

    _controller.forward();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, child) {
        return Opacity(
          opacity: _opacity.value,
          child: Transform.translate(
            offset: _slide.value,
            child: child,
          ),
        );
      },
      child: widget.child,
    );
  }
}
