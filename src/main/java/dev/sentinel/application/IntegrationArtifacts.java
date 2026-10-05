package dev.sentinel.application;

import dev.sentinel.domain.agent.IntegrationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** Common ownership-safe filesystem operations for generated agent integrations. */
final class IntegrationArtifacts {
    private IntegrationArtifacts() {
    }

    static boolean owned(Path target, String marker) throws IOException {
        return Files.exists(target) && Files.readString(target).contains(marker);
    }

    static IntegrationResult preflight(Path target, String marker) throws IOException {
        if (!Files.exists(target) || owned(target, marker)) {
            return null;
        }
        return new IntegrationResult(IntegrationResult.Status.CONFLICT, List.of(target.toString()),
                "Existing user-owned integration preserved; resolve the conflict explicitly.");
    }

    static IntegrationResult install(Path target, String marker, String content, String message) throws IOException {
        if (Files.exists(target)) {
            if (owned(target, marker)) {
                return new IntegrationResult(IntegrationResult.Status.ALREADY_PRESENT, List.of(),
                        "Sentinel integration is already present.");
            }
            return new IntegrationResult(IntegrationResult.Status.CONFLICT, List.of(target.toString()),
                    "Existing user-owned integration preserved; resolve the conflict explicitly.");
        }
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardOpenOption.CREATE_NEW);
        return new IntegrationResult(IntegrationResult.Status.CHANGED, List.of(target.toString()), message);
    }

    static IntegrationResult remove(Path target, String marker, String message) throws IOException {
        if (!Files.exists(target)) {
            return new IntegrationResult(IntegrationResult.Status.NOT_FOUND, List.of(), message);
        }
        if (!owned(target, marker)) {
            return new IntegrationResult(IntegrationResult.Status.CONFLICT, List.of(target.toString()),
                    "Refusing to remove an integration not owned by Sentinel.");
        }
        Files.delete(target);
        return new IntegrationResult(IntegrationResult.Status.REMOVED, List.of(target.toString()), message);
    }
}
