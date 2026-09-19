/// `POST /api/v1/aura/chat` cevabi.
class ChatResponse {
  const ChatResponse({
    required this.reply,
    required this.model,
    required this.source,
    required this.wardrobeCount,
    required this.perfumeCount,
    required this.weatherSummary,
  });

  final String reply;
  final String model;
  final String source;
  final int wardrobeCount;
  final int perfumeCount;
  final String weatherSummary;

  factory ChatResponse.fromJson(Map<String, dynamic> json) {
    return ChatResponse(
      reply: json['reply'] as String? ?? '',
      model: json['model'] as String? ?? '',
      source: json['source'] as String? ?? '',
      wardrobeCount: (json['wardrobeCount'] as num?)?.toInt() ?? 0,
      perfumeCount: (json['perfumeCount'] as num?)?.toInt() ?? 0,
      weatherSummary: json['weatherSummary'] as String? ?? '',
    );
  }

  bool get isFallback => source == 'fallback';
}

/// UI ve API gecmisi icin tek mesaj.
class ChatMessage {
  const ChatMessage({
    required this.role,
    required this.content,
    this.source,
    this.model,
  });

  final String role; // user | assistant
  final String content;
  final String? source;
  final String? model;

  bool get isUser => role == 'user';

  Map<String, String> toApiJson() => {
        'role': role,
        'content': content,
      };
}
