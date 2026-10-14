package dev.sentinel.domain.provenance;

/** Finding produced when an archive still contains an unchecked task. */
public record TaskCompletenessFinding(String archivePath, String task) {}
