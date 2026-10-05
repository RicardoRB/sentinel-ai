package dev.sentinel.domain.init;

import dev.sentinel.domain.agent.IntegrationResult;

import java.nio.file.Path;
import java.util.List;

/** Framework-neutral result of an initialization attempt. */
public record InitResult(Path file, boolean created, boolean overwritten,
                         List<InitGateOption> gates, List<IntegrationResult> integrations,
                         List<String> pomChanges, ArchitectureTestChange architectureTest) {
    public InitResult {
        gates = gates == null ? List.of() : List.copyOf(gates);
        integrations = integrations == null ? List.of() : List.copyOf(integrations);
        pomChanges = pomChanges == null ? List.of() : List.copyOf(pomChanges);
    }

    public InitResult(Path file, boolean created) {
        this(file, created, false, List.of(), List.of(), List.of(), null);
    }

    public InitResult(Path file, boolean created, List<InitGateOption> gates,
                      List<IntegrationResult> integrations) {
        this(file, created, false, gates, integrations, List.of(), null);
    }

    public InitResult(Path file, boolean created, List<InitGateOption> gates,
                      List<IntegrationResult> integrations, List<String> pomChanges) {
        this(file, created, false, gates, integrations, pomChanges, null);
    }
}
