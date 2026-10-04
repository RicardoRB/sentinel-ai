package dev.sentinel.application;

import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.IntegrationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** Safe, ownership-marked integration for OpenCode command discovery. */
public final class OpenCodeIntegration implements AgentIntegration {
    public static final String MARKER = "<!-- sentinel-owned: opencode-quality-check v1 -->";
    private static final String FILE = ".opencode/commands/sentinel-check.md";

    @Override
    public String id() {
        return "opencode";
    }

    @Override
    public IntegrationResult integrate(Path projectRoot, boolean remove) {
        Path target = projectRoot.toAbsolutePath().normalize().resolve(FILE);
        try {
            if (remove) return remove(target);
            if (Files.exists(target)) {
                String existing = Files.readString(target);
                if (existing.contains(MARKER)) {
                    return new IntegrationResult(IntegrationResult.Status.ALREADY_PRESENT,
                            List.of(), "Sentinel OpenCode integration is already present.");
                }
                return new IntegrationResult(IntegrationResult.Status.CONFLICT,
                        List.of(target.toString()), "Existing OpenCode command preserved; resolve the conflict explicitly.");
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, MARKER + "\n\nRun `sentinel check --format json` and return the result.\n",
                    StandardOpenOption.CREATE_NEW);
            return new IntegrationResult(IntegrationResult.Status.CHANGED, List.of(target.toString()),
                    "Created Sentinel-owned OpenCode integration.");
        } catch (IOException e) {
            throw new RuntimeException("Could not update OpenCode integration: " + e.getMessage(), e);
        }
    }

    private IntegrationResult remove(Path target) throws IOException {
        if (!Files.exists(target)) {
            return new IntegrationResult(IntegrationResult.Status.NOT_FOUND, List.of(), "No Sentinel OpenCode integration found.");
        }
        String content = Files.readString(target);
        if (!content.contains(MARKER)) {
            return new IntegrationResult(IntegrationResult.Status.CONFLICT, List.of(target.toString()),
                    "Refusing to remove an OpenCode command not owned by Sentinel.");
        }
        Files.delete(target);
        return new IntegrationResult(IntegrationResult.Status.REMOVED, List.of(target.toString()),
                "Removed Sentinel-owned OpenCode integration.");
    }
}
