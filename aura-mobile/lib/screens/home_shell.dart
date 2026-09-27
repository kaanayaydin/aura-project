import 'package:flutter/material.dart';

import '../core/quiet_luxury/aura_colors.dart';
import '../core/quiet_luxury/aura_typography.dart';
import '../core/quiet_luxury/quiet_luxury_nav_icons.dart';
import 'aura_chat_screen.dart';
import 'favorites_screen.dart';
import 'perfume_shelf_screen.dart';
import 'suggest_screen.dart';
import 'wardrobe_screen.dart';

class HomeShell extends StatefulWidget {
  const HomeShell({super.key});

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int _index = 0;

  static const _pages = [
    WardrobeScreen(),
    SuggestScreen(),
    AuraChatScreen(),
    FavoritesScreen(),
    PerfumeShelfScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    final navTheme = NavigationBarThemeData(
      backgroundColor: AuraColors.surfaceElevated,
      indicatorColor: AuraColors.primaryAction.withValues(alpha: 0.16),
      labelTextStyle: WidgetStateProperty.resolveWith((states) {
        final selected = states.contains(WidgetState.selected);
        return AuraTypography.caption.copyWith(
          fontWeight: FontWeight.w600,
          color: selected ? AuraColors.primaryAction : AuraColors.textSecondary,
        );
      }),
      iconTheme: WidgetStateProperty.resolveWith((states) {
        final selected = states.contains(WidgetState.selected);
        return IconThemeData(
          color: selected ? AuraColors.primaryAction : AuraColors.textSecondary,
        );
      }),
    );

    return Scaffold(
      body: IndexedStack(
        index: _index,
        children: _pages,
      ),
      bottomNavigationBar: Theme(
        data: Theme.of(context).copyWith(navigationBarTheme: navTheme),
        child: NavigationBar(
          selectedIndex: _index,
          surfaceTintColor: Colors.transparent,
          labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
          onDestinationSelected: (value) => setState(() => _index = value),
          destinations: const [
            NavigationDestination(
              icon: Icon(QuietLuxuryNavIcons.dolap),
              selectedIcon: Icon(
                QuietLuxuryNavIcons.dolap,
                color: AuraColors.primaryAction,
              ),
              label: 'Dolap',
            ),
            NavigationDestination(
              icon: Icon(QuietLuxuryNavIcons.oneri),
              selectedIcon: Icon(
                QuietLuxuryNavIcons.oneri,
                color: AuraColors.primaryAction,
              ),
              label: 'Öneri',
            ),
            NavigationDestination(
              icon: Icon(QuietLuxuryNavIcons.auraAi),
              selectedIcon: Icon(
                QuietLuxuryNavIcons.auraAi,
                color: AuraColors.primaryAction,
              ),
              label: 'Aura AI',
            ),
            NavigationDestination(
              icon: Icon(QuietLuxuryNavIcons.arsiv),
              selectedIcon: Icon(
                QuietLuxuryNavIcons.arsiv,
                color: AuraColors.primaryAction,
              ),
              label: 'Arşiv',
            ),
            NavigationDestination(
              icon: Icon(QuietLuxuryNavIcons.raf),
              selectedIcon: Icon(
                QuietLuxuryNavIcons.raf,
                color: AuraColors.primaryAction,
              ),
              label: 'Raf',
            ),
          ],
        ),
      ),
    );
  }
}
