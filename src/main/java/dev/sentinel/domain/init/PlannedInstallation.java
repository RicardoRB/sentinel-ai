package dev.sentinel.domain.init;

import java.nio.file.Path;

/** A project-local tool installation that initialization would run after consent. */
public record PlannedInstallation(String tool, String packageName, Path destination) {}
