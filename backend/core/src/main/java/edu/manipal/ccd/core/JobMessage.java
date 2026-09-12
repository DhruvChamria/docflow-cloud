package edu.manipal.ccd.core;

import java.util.Objects;
import java.util.UUID;

/** Delivery attempt count is authoritative in storage, not in an untrusted/redelivered message. */
public record JobMessage(int schemaVersion, JobKey key, WorkerType workerType, UUID dispatchId, UUID correlationId) {
    public enum WorkerType { PDF, OFFICE }
    public JobMessage {
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported queue schema");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(workerType, "workerType");
        Objects.requireNonNull(dispatchId, "dispatchId");
        Objects.requireNonNull(correlationId, "correlationId");
    }
}
