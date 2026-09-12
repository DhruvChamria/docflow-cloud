package edu.manipal.ccd.core;

public enum JobStatus {
    CREATED, QUEUED, PROCESSING, RETRYING, COMPLETED, FAILED, CANCELLED, EXPIRED;

    public boolean canTransitionTo(JobStatus next) {
        if (next == null) return false;
        return switch (this) {
            case CREATED -> next == QUEUED || next == CANCELLED || next == FAILED || next == EXPIRED;
            case QUEUED -> next == PROCESSING || next == CANCELLED || next == FAILED || next == EXPIRED;
            case PROCESSING -> next == COMPLETED || next == RETRYING || next == FAILED || next == CANCELLED;
            case RETRYING -> next == QUEUED || next == CANCELLED || next == FAILED || next == EXPIRED;
            case COMPLETED, FAILED, CANCELLED -> next == EXPIRED;
            case EXPIRED -> false;
        };
    }

    public JobStatus transitionTo(JobStatus next) {
        if (!canTransitionTo(next)) throw new IllegalStateException("Invalid job transition: " + this + " -> " + next);
        return next;
    }
}
