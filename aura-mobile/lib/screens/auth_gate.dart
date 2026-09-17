import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/config.dart';
import '../core/theme.dart';
import '../providers/providers.dart';
import '../screens/home_shell.dart';
import '../screens/login_screen.dart';

/// Oturum hydrate + demo/login yonlendirmesi.
class AuthGate extends ConsumerStatefulWidget {
  const AuthGate({super.key});

  @override
  ConsumerState<AuthGate> createState() => _AuthGateState();
}

class _AuthGateState extends ConsumerState<AuthGate> {
  @override
  void initState() {
    super.initState();
    Future.microtask(() async {
      await ref.read(authSessionProvider.notifier).hydrate();
      if (!mounted) return;
      if (AuraConfig.useDemoAuth && ref.read(authSessionProvider) == null) {
        try {
          await ref.read(authSessionProvider.notifier).ensure();
        } catch (_) {}
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final hydrated = ref.watch(authHydratedProvider);
    final session = ref.watch(authSessionProvider);

    if (!hydrated) {
      return const Scaffold(
        backgroundColor: AuraTheme.carbon,
        body: Center(
          child: CircularProgressIndicator(color: AuraTheme.champagneGold),
        ),
      );
    }

    if (session == null && !AuraConfig.useDemoAuth) {
      return const LoginScreen();
    }

    return const HomeShell();
  }
}
