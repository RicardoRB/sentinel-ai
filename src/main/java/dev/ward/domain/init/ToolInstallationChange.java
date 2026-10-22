package dev.ward.domain.init;

import java.nio.file.Path;

/** A filesystem change owned by one initialization invocation. */
public record ToolInstallationChange(Path staging, Path published, boolean invocationOwned) {}
