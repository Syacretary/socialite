import 'package:flutter/material.dart';

/// A modular staggered animation container.
///
/// Children fade in one after another instead of all at once.
/// [staggerDelay] controls the gap between each child's start.
/// [duration] is the per-child animation duration.
/// Use [StaggeredContent] anywhere you have a list of widgets to reveal
/// gradually (lists, cards, permission tiles, stat cards, etc.).
class StaggeredContent extends StatefulWidget {
  final List<Widget> children;
  final Duration duration;
  final Duration staggerDelay;
  final Curve curves;

  const StaggeredContent({
    super.key,
    required this.children,
    this.duration = const Duration(milliseconds: 400),
    this.staggerDelay = const Duration(milliseconds: 80),
    this.curves = Curves.easeOut,
  });

  @override
  State<StaggeredContent> createState() => _StaggeredContentState();
}

class _StaggeredContentState extends State<StaggeredContent>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late List<Animation<double>> _fades;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: widget.duration);
    _fades = List.generate(
      widget.children.length,
      (i) => CurvedAnimation(
        parent: _controller,
        curve: Interval(
          i * widget.staggerDelay.inMilliseconds /
              widget.duration.inMilliseconds
              .clamp(0.0, 1.0),
          1.0,
          curve: widget.curves,
        ),
      ),
    );
    _controller.forward();
  }

  @override
  void didUpdateWidget(StaggeredContent oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.children.length != oldWidget.children.length) {
      _fades.clear();
      _fades = List.generate(
        widget.children.length,
        (i) => CurvedAnimation(
          parent: _controller,
          curve: Interval(
            i * widget.staggerDelay.inMilliseconds /
                widget.duration.inMilliseconds.clamp(0.0, 1.0),
            1.0,
            curve: widget.curves,
          ),
        ),
      );
      _controller.forward(from: 0);
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: List.generate(widget.children.length, (i) {
        return FadeTransition(
          opacity: _fades[i],
          child: widget.children[i],
        );
      }),
    );
  }
}