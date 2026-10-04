package dev.sentinel.application;

import dev.sentinel.domain.config.SentinelException;

public class ProjectNotFoundException extends SentinelException {

    public ProjectNotFoundException() {
        super("No supported project detected.");
    }
}
