package dev.sentinel.domain.config;

/** An expected, user-facing failure (bad configuration, no project, ...). */
public class SentinelException extends RuntimeException {

    public SentinelException(String message) {
        super(message);
    }

    public SentinelException(String message, Throwable cause) {
        super(message, cause);
    }
}
