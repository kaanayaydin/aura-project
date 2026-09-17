package app.aura.backend.service;

/**
 * Dis VTON worker (Python FastAPI + Celery) istemci sozlesmesi.
 */
public interface VtonWorkerClient {

    /**
     * Isi Python kuyruguna alir; worker tarafindaki is kimligini dondurur.
     */
    String enqueue(EnqueuePayload payload);

    /**
     * Worker / Redis uzerindeki guncel durumu okur.
     */
    WorkerStatusSnapshot status(String workerJobId);

    record EnqueuePayload(
            Long auraJobId,
            Long userId,
            Long wardrobeItemId,
            String personImageBase64,
            String garmentImageBase64,
            String personImageUrl,
            String garmentImageUrl,
            String clothType) {
    }

    record WorkerStatusSnapshot(
            String status,
            String resultImageUri,
            String resultImageBase64,
            String errorMessage) {
    }
}
