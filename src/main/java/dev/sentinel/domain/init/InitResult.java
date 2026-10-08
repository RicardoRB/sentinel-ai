package dev.sentinel.domain.init;

import dev.sentinel.domain.agent.IntegrationResult;
import java.nio.file.Path;
import java.util.List;

/** Framework-neutral result of an initialization attempt. */
public record InitResult(
    Path file,
    boolean created,
    boolean overwritten,
    List<InitGateOption> gates,
    List<IntegrationResult> integrations,
    List<String> pomChanges,
    ArchitectureTestChange architectureTest,
    List<Path> preservedRuleFiles,
    List<String> pomWarnings) {
  public InitResult {
    gates = gates == null ? List.of() : List.copyOf(gates);
    integrations = integrations == null ? List.of() : List.copyOf(integrations);
    pomChanges = pomChanges == null ? List.of() : List.copyOf(pomChanges);
    preservedRuleFiles = preservedRuleFiles == null ? List.of() : List.copyOf(preservedRuleFiles);
    pomWarnings = pomWarnings == null ? List.of() : List.copyOf(pomWarnings);
  }

  public InitResult(final Path file, final boolean created) {
    this(file, created, false, List.of(), List.of(), List.of(), null, List.of(), List.of());
  }

  public InitResult(
      final Path file,
      final boolean created,
      final List<InitGateOption> gates,
      final List<IntegrationResult> integrations) {
    this(file, created, false, gates, integrations, List.of(), null, List.of(), List.of());
  }

  public InitResult(
      final Path file,
      final boolean created,
      final List<InitGateOption> gates,
      final List<IntegrationResult> integrations,
      final List<String> pomChanges) {
    this(file, created, false, gates, integrations, pomChanges, null, List.of(), List.of());
  }

  public InitResult(
      final Path file,
      final boolean created,
      final boolean overwritten,
      final List<InitGateOption> gates,
      final List<IntegrationResult> integrations,
      final List<String> pomChanges,
      final ArchitectureTestChange architectureTest) {
    this(
        file,
        created,
        overwritten,
        gates,
        integrations,
        pomChanges,
        architectureTest,
        List.of(),
        List.of());
  }
}
