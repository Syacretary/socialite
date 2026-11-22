import 'package:flutter/material.dart';
import 'package:flutter/physics.dart';

/// Scroll physics that adds a rubber-band effect when pulling past the end.
///
/// When the user pulls beyond the scroll boundaries, the content stretches
/// slightly and then springs back with a slow-in/slow-out ease.
class RubberBandScrollPhysics extends ScrollPhysics {
  const RubberBandScrollPhysics({ScrollPhysics? parent}) : super(parent: parent);

  @override
  RubberBandScrollPhysics applyTo(ScrollPhysics? ancestor) {
    return RubberBandScrollPhysics(parent: buildParent(ancestor));
  }

  @override
  Simulation? createBallisticSimulation(
    ScrollMetrics position,
    double velocity,
  ) {
    // If we're out of range, spring back with slow-in/slow-out
    if (position.pixels < position.minScrollExtent) {
      final overshoot = position.minScrollExtent - position.pixels;
      return SpringSimulation(
        SpringDescription(
          mass: 0.5,
          stiffness: 400.0,
          damping: 40.0,
        ),
        position.minScrollExtent,
        position.minScrollExtent,
        velocity - overshoot * 2.5,
      );
    }
    if (position.pixels > position.maxScrollExtent) {
      final overshoot = position.pixels - position.maxScrollExtent;
      return SpringSimulation(
        SpringDescription(
          mass: 0.5,
          stiffness: 400.0,
          damping: 40.0,
        ),
        position.maxScrollExtent,
        position.maxScrollExtent,
        velocity + overshoot * 2.5,
      );
    }
    return super.createBallisticSimulation(position, velocity);
  }

  @override
  bool get allowImplicitScrolling => false;
}

/// A scrollable that applies rubber-band physics.
/// Use [RubberBandScrollView] instead of [SingleChildScrollView] or [ListView]
/// to get pull-past-end stretch-and-spring-back behavior.
class RubberBandScrollView extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry padding;
  final bool reverse;
  final ScrollController? controller;

  const RubberBandScrollView({
    super.key,
    required this.child,
    this.padding = EdgeInsets.zero,
    this.reverse = false,
    this.controller,
  });

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      controller: controller,
      physics: const RubberBandScrollPhysics(),
      padding: padding,
      reverse: reverse,
      child: child,
    );
  }
}