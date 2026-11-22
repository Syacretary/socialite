import 'package:flutter/material.dart';

/// A modular squash-and-stretch navigation bar item.
///
/// When tapped, the item briefly squishes vertically then stretches back,
/// giving a soft, tactile feel instead of a stiff scale.
class SquashStretchNavItem extends StatefulWidget {
  final int index;
  final IconData icon;
  final String label;
  final bool isSelected;
  final Color primaryColor;
  final bool isDark;
  final VoidCallback onTap;

  const SquashStretchNavItem({
    super.key,
    required this.index,
    required this.icon,
    required this.label,
    required this.isSelected,
    required this.primaryColor,
    required this.isDark,
    required this.onTap,
  });

  @override
  State<SquashStretchNavItem> createState() => _SquashStretchNavItemState();
}

class _SquashStretchNavItemState extends State<SquashStretchNavItem>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _scaleY;
  late Animation<double> _scaleX;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 180),
    );
    _scaleY = Tween<double>(begin: 1.0, end: 0.85).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeIn),
    )..addStatusListener((status) {
        if (status == AnimationStatus.completed) {
          _controller.reverse();
        }
      });
    _scaleX = Tween<double>(begin: 1.0, end: 1.1).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeOut),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _onTap() {
    widget.onTap();
    _controller.forward(from: 0);
  }

  @override
  Widget build(BuildContext context) {
    final isSelected = widget.isSelected;
    return GestureDetector(
      onTap: _onTap,
      behavior: HitTestBehavior.opaque,
      child: Center(
        child: AnimatedBuilder(
          animation: _controller,
          builder: (context, child) {
            return Transform.scale(
              scaleY: _scaleY.value,
              scaleX: _scaleX.value,
              child: child,
            );
          },
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                widget.icon,
                size: 18,
                color: isSelected
                    ? (widget.isDark
                        ? const Color(0xFF000000)
                        : const Color(0xFFFFFFFF))
                    : (widget.isDark
                        ? const Color(0xFF777777)
                        : const Color(0xFF888888)),
              ),
              if (isSelected) ...[
                const SizedBox(width: 6),
                Text(
                  widget.label,
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w800,
                    letterSpacing: -0.2,
                    color: widget.isDark
                        ? const Color(0xFF000000)
                        : const Color(0xFFFFFFFF),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

/// A bottom navigation bar with squash-and-stretch animation on each tab.
/// Drop-in replacement for the standard BottomNavigationBar items.
class SquashStretchNavbar extends StatelessWidget {
  final int currentIndex;
  final ValueChanged<int> onTap;
  final List<NavItem> items;
  final Color primaryColor;
  final bool isDark;

  const SquashStretchNavbar({
    super.key,
    required this.currentIndex,
    required this.onTap,
    required this.items,
    required this.primaryColor,
    required this.isDark,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: List.generate(items.length, (i) {
        final item = items[i];
        final isSelected = currentIndex == i;
        return Expanded(
          child: SquashStretchNavItem(
            index: i,
            icon: item.icon,
            label: item.label,
            isSelected: isSelected,
            primaryColor: primaryColor,
            isDark: isDark,
            onTap: () => onTap(i),
          ),
        );
      }),
    );
  }
}

class NavItem {
  final IconData icon;
  final String label;
  NavItem({required this.icon, required this.label});
}