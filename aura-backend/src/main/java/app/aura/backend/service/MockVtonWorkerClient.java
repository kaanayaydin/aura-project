package app.aura.backend.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Yerel mock worker — yalnizca test profilinde (aura.vton.mock-worker-enabled=true).
 */
@Component
@ConditionalOnProperty(prefix = "aura.vton", name = "mock-worker-enabled", havingValue = "true")
public class MockVtonWorkerClient implements VtonWorkerClient {

    private static final Logger log = LoggerFactory.getLogger(MockVtonWorkerClient.class);

    @Override
    public String enqueue(EnqueuePayload payload) {
        String workerJobId = "mock-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        log.info(
                "Mock VTON worker kuyruga aldi: auraJobId={} workerJobId={} userId={} itemId={} clothType={} garmentUrl={}",
                payload.auraJobId(),
                workerJobId,
                payload.userId(),
                payload.wardrobeItemId(),
                payload.clothType(),
                payload.garmentImageUrl());
        return workerJobId;
    }

    @Override
    public WorkerStatusSnapshot status(String workerJobId) {
        return new WorkerStatusSnapshot("QUEUED", null, null, null);
    }
}
