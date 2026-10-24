package org.openideastudio.common;

/** The client acted on a stale version of a resource (optimistic concurrency). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
