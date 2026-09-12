package edu.manipal.ccd.core;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static edu.manipal.ccd.core.JobStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class JobContractsTest {
    @Test void successfulLifecycle() {
        assertEquals(EXPIRED, CREATED.transitionTo(QUEUED).transitionTo(PROCESSING)
                .transitionTo(COMPLETED).transitionTo(EXPIRED));
    }
    @Test void recoveryAndCancellation() {
        assertEquals(CANCELLED, PROCESSING.transitionTo(RETRYING).transitionTo(QUEUED).transitionTo(CANCELLED));
    }
    @Test void completedAndExpiredCannotBeReprocessed() {
        for (var status : new JobStatus[]{COMPLETED, FAILED, CANCELLED, EXPIRED}) {
            assertThrows(IllegalStateException.class, () -> status.transitionTo(PROCESSING));
            assertThrows(IllegalStateException.class, () -> status.transitionTo(QUEUED));
        }
    }
    @Test void activeLeaseCannotExpireButCanBeFencedByCancellation() {
        assertThrows(IllegalStateException.class, () -> PROCESSING.transitionTo(EXPIRED));
        assertEquals(CANCELLED, PROCESSING.transitionTo(CANCELLED));
    }
    @Test void failedCancelledAndAbandonedJobsCanExpire() {
        for (var status : new JobStatus[]{FAILED, CANCELLED, CREATED, QUEUED, RETRYING}) {
            assertEquals(EXPIRED, status.transitionTo(EXPIRED));
        }
    }
    @Test void nullAndSelfTransitionsAreRejected() {
        for (var status : values()) {
            assertThrows(IllegalStateException.class, () -> status.transitionTo(status));
            assertThrows(IllegalStateException.class, () -> status.transitionTo(null));
        }
    }
    @Test void messageRequiresRoutingAndSupportedSchema() {
        assertThrows(IllegalArgumentException.class, () -> new JobKey(UUID.randomUUID(), " "));
        var key = new JobKey(UUID.randomUUID(), "guest:hashed-id");
        assertThrows(IllegalArgumentException.class, () -> new JobMessage(2, key, JobMessage.WorkerType.PDF,
                UUID.randomUUID(), UUID.randomUUID()));
        assertNotNull(new JobMessage(1, key, JobMessage.WorkerType.PDF, UUID.randomUUID(), UUID.randomUUID()));
    }
}
