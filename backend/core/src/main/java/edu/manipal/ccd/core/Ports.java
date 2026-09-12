package edu.manipal.ccd.core;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Phase 1 contracts. No in-memory implementations stand in for durable services. */
public final class Ports {
    private Ports() {}

    public record Snapshot(JobKey key, JobStatus status, String revision, int attempts,
                           Instant expiresAt, Optional<String> outputKey) {}
    public record Lease(JobKey key, UUID token, Instant until, String revision) {}
    public record Dispatch(JobMessage message, String revision) {}
    public record Delivery(JobMessage message, String receipt) {}
    public record Page<T>(List<T> items, Optional<String> continuation) {}

    public interface Jobs {
        /** Atomically creates job metadata plus dispatch intent (one document/partition in Cosmos). */
        Snapshot createWithDispatch(JobKey key, String operation, String validatedParametersJson,
                                    List<String> inputKeys, Instant expiresAt, JobMessage message);
        Optional<Snapshot> findOwned(JobKey key);
        Page<Snapshot> listOwned(String ownerKey, int limit, Optional<String> continuation);
        Optional<Lease> claim(JobKey key, String expectedRevision, Instant now, Duration leaseLength);
        Optional<Lease> renew(Lease lease, Instant now, Duration leaseLength);
        /** Returns false if lease/revision is stale; attempt-specific output must then be cleaned. */
        boolean complete(Lease lease, String outputKey, Instant now);
        boolean failAttempt(Lease lease, String safeErrorCode, boolean retryable, Instant now);
        boolean cancel(JobKey key, String expectedRevision);
        List<Dispatch> pendingDispatches(Instant now, int limit);
        boolean markDispatched(Dispatch dispatch);
    }

    public interface Objects {
        void put(String key, InputStream bytes, long length, String contentType);
        InputStream open(String key);
        void deleteIfExists(String key);
    }

    public interface Queue {
        void publish(JobMessage message);
        Optional<Delivery> receive(Duration visibilityTimeout);
        Delivery renew(Delivery delivery, Duration visibilityTimeout);
        void acknowledge(Delivery delivery);
    }

    public interface Operation {
        String code();
        void validate(String parametersJson, List<String> inputKeys);
        /** Workspace paths and streams will be supplied by the worker, not browser filenames. */
        String execute(JobKey key, UUID attemptToken, String parametersJson, List<String> inputKeys);
    }
}
