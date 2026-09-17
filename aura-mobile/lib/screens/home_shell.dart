import 'package:flutter/material.dart';

import '../core/theme.dart';
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
    return Scaffold(
      body: IndexedStack(
        index: _index,
        children: _pages,
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
        onDestinationSelected: (value) => setState(() => _index = value),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.checkroom_outlined),
            selectedIcon: Icon(Icons.checkroom, color: AuraTheme.champagne),
            label: 'Dolap',
          ),
          NavigationDestination(
            icon: Icon(Icons.auto_awesome_outlined),
            selectedIcon: Icon(Icons.auto_awesome, color: AuraTheme.champagne),
            label: 'Oneri',
          ),
          NavigationDestination(
            icon: Icon(Icons.psychology_outlined),
            selectedIcon: Icon(Icons.psychology, color: AuraTheme.champagne),
            label: 'Aura AI',
          ),
          NavigationDestination(
            icon: Icon(Icons.favorite_border),
            selectedIcon: Icon(Icons.favorite, color: AuraTheme.champagne),
            label: 'Arşiv',
          ),
          NavigationDestination(
            icon: Icon(Icons.spa_outlined),
            selectedIcon: Icon(Icons.spa, color: AuraTheme.champagne),
            label: 'Raf',
          ),
        ],
      ),
    );
  }
}
