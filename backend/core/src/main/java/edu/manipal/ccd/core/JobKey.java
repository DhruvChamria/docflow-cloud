package edu.manipal.ccd.core;

import java.util.Objects;
import java.util.UUID;

/** Internal storage key. ownerKey is resolved server-side, never trusted from request JSON. */
public record JobKey(UUID jobId, String ownerKey) {
    public JobKey {
        Objects.requireNonNull(jobId, "jobId");
        if (ownerKey == null || ownerKey.isBlank()) throw new IllegalArgumentException("ownerKey is required");
    }
}
