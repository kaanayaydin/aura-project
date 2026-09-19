/// Backend `VtonJobResponse` eslemesi.
class VtonJob {
  const VtonJob({
    required this.jobId,
    required this.userId,
    required this.wardrobeItemId,
    required this.status,
    this.workerJobId,
    this.resultImageUri,
    this.resultImageUrl,
    this.resultImageBase64,
    this.errorMessage,
    this.lookbookSaved = false,
    this.createdAt,
  });

  final int jobId;
  final int userId;
  final int wardrobeItemId;
  final String status;
  final String? workerJobId;
  final String? resultImageUri;
  /// Java guvenli proxy yolu (ornek: /api/v1/aura/vton/results/12/image).
  final String? resultImageUrl;
  final String? resultImageBase64;
  final String? errorMessage;
  final bool lookbookSaved;
  final DateTime? createdAt;

  bool get isTerminal => status == 'COMPLETED' || status == 'FAILED';

  bool get isCompleted => status == 'COMPLETED';

  VtonJob copyWith({bool? lookbookSaved}) {
    return VtonJob(
      jobId: jobId,
      userId: userId,
      wardrobeItemId: wardrobeItemId,
      status: status,
      workerJobId: workerJobId,
      resultImageUri: resultImageUri,
      resultImageUrl: resultImageUrl,
      resultImageBase64: resultImageBase64,
      errorMessage: errorMessage,
      lookbookSaved: lookbookSaved ?? this.lookbookSaved,
      createdAt: createdAt,
    );
  }

  factory VtonJob.fromJson(Map<String, dynamic> json) {
    return VtonJob(
      jobId: (json['jobId'] as num).toInt(),
      userId: (json['userId'] as num).toInt(),
      wardrobeItemId: (json['wardrobeItemId'] as num).toInt(),
      status: json['status'] as String? ?? 'QUEUED',
      workerJobId: json['workerJobId'] as String?,
      resultImageUri: json['resultImageUri'] as String?,
      resultImageUrl: json['resultImageUrl'] as String?,
      resultImageBase64: json['resultImageBase64'] as String?,
      errorMessage: json['errorMessage'] as String?,
      lookbookSaved: json['lookbookSaved'] as bool? ?? false,
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString())
          : null,
    );
  }
}
