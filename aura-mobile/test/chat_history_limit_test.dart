import 'dart:convert';

import 'package:aura_mobile/models/auth_token.dart';
import 'package:aura_mobile/providers/providers.dart';
import 'package:aura_mobile/services/api_service.dart';
import 'package:aura_mobile/services/auth_secure_store.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  test('send keeps the screen transcript and posts only the last 12 turns', () async {
    final histories = <List<dynamic>>[];
    final client = MockClient((request) async {
      final body = jsonDecode(request.body) as Map<String, dynamic>;
      final history = body['history'] as List<dynamic>;
      histories.add(history);
      final index = histories.length - 1;
      return http.Response(
        jsonEncode({
          'reply': 'assistant-${index.toString().padLeft(3, '0')}',
          'model': 'llama3.2',
          'source': 'fallback',
          'wardrobeCount': 0,
          'perfumeCount': 0,
          'weatherSummary': '21 C, Acik',
        }),
        200,
      );
    });

    final container = ProviderContainer(
      overrides: [
        authSecureStoreProvider.overrideWithValue(MemoryAuthSecureStore()),
        authSessionProvider.overrideWith(_SeededAuth.new),
        apiServiceProvider.overrideWithValue(
          ApiService(
            client: client,
            backendBaseUrl: 'http://backend.test',
            visionBaseUrl: 'http://vision.test',
            accessTokenProvider: () => 'test-access',
          ),
        ),
      ],
    );
    addTearDown(container.dispose);

    const rounds = 32;
    for (var i = 0; i < rounds; i++) {
      await container
          .read(chatProvider.notifier)
          .send('user-${i.toString().padLeft(3, '0')}');
    }

    expect(container.read(chatProvider).messages, hasLength(rounds * 2));
    expect(container.read(chatProvider).error, isNull);

    await container.read(chatProvider.notifier).send('yeni-soru');

    final posted = histories.last;
    expect(posted, hasLength(kChatHistoryLimit));
    expect(
      posted.map((turn) => (turn as Map<String, dynamic>)['content']).toList(),
      [
        for (var i = rounds - (kChatHistoryLimit ~/ 2); i < rounds; i++) ...[
          'user-${i.toString().padLeft(3, '0')}',
          'assistant-${i.toString().padLeft(3, '0')}',
        ],
      ],
    );
    expect(
      posted.any(
        (turn) => (turn as Map<String, dynamic>)['content'] == 'yeni-soru',
      ),
      isFalse,
    );

    final transcript = container.read(chatProvider).messages;
    expect(transcript, hasLength(rounds * 2 + 2));
    expect(transcript.first.content, 'user-000');
    expect(transcript[transcript.length - 2].content, 'yeni-soru');
    expect(
      transcript.last.content,
      'assistant-${rounds.toString().padLeft(3, '0')}',
    );
    expect(transcript.map((m) => m.content), contains('assistant-000'));
  });
}

class _SeededAuth extends AuthSessionNotifier {
  @override
  AuthSession? build() => const AuthSession(
        accessToken: 'test-access',
        refreshToken: 'test-refresh',
        tokenType: 'Bearer',
        expiresInSeconds: 900,
        userId: 1,
        email: 'test@aura.app',
      );
}
