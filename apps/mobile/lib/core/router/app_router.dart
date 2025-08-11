import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../features/home/presentation/screens/home_screen.dart';
import '../../features/player/presentation/screens/player_screen.dart';
import '../../features/library/presentation/screens/library_screen.dart';
import '../../features/settings/presentation/screens/settings_screen.dart';

final appRouterProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: '/',
    debugLogDiagnostics: true,
    routes: [
      GoRoute(
        path: '/',
        name: 'home',
        builder: (context, state) => const HomeScreen(),
        routes: [
          GoRoute(
            path: 'player',
            name: 'player',
            builder: (context, state) {
              final filePath = state.queryParameters['path'];
              final isNetwork = state.queryParameters['isNetwork'] == 'true';
              return PlayerScreen(
                filePath: filePath ?? '',
                isNetwork: isNetwork,
              );
            },
          ),
          GoRoute(
            path: 'library',
            name: 'library',
            builder: (context, state) => const LibraryScreen(),
          ),
          GoRoute(
            path: 'settings',
            name: 'settings',
            builder: (context, state) => const SettingsScreen(),
          ),
        ],
      ),
    ],
    errorBuilder: (context, state) => Scaffold(
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(
              Icons.error_outline,
              size: 64,
              color: Colors.red,
            ),
            const SizedBox(height: 16),
            Text(
              'Page not found',
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            const SizedBox(height: 8),
            Text(
              state.error?.toString() ?? 'Unknown error',
              style: Theme.of(context).textTheme.bodyMedium,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: () => context.go('/'),
              child: const Text('Go Home'),
            ),
          ],
        ),
      ),
    ),
  );
});

// Route names for type-safe navigation
class AppRoutes {
  static const String home = 'home';
  static const String player = 'player';
  static const String library = 'library';
  static const String settings = 'settings';
  static const String search = 'search';
  static const String downloads = 'downloads';
  static const String playlists = 'playlists';
  static const String favorites = 'favorites';
  static const String history = 'history';
}