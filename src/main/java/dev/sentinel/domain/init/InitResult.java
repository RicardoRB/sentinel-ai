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
    List<String> pomWarnings,
    List<Path> ownedSections) {
  /** Copies the lists; missing values mean empty. */
  public InitResult {
    gates = gates == null ? List.of() : List.copyOf(gates);
    integrations = integrations == null ? List.of() : List.copyOf(integrations);
    pomChanges = pomChanges == null ? List.of() : List.copyOf(pomChanges);
    preservedRuleFiles = preservedRuleFiles == null ? List.of() : List.copyOf(preservedRuleFiles);
    pomWarnings = pomWarnings == null ? List.of() : List.copyOf(pomWarnings);
    ownedSections = ownedSections == null ? List.of() : List.copyOf(ownedSections);
  }

  /** Creates a result for a configuration-only initialization. */
  public InitResult(final Path file, final boolean created) {
    this(
        file, created, false, List.of(), List.of(), List.of(), null, List.of(), List.of(),
        List.of());
  }

  /** Creates a result without build-file changes. */
  public InitResult(
      final Path file,
      final boolean created,
      final List<InitGateOption> gates,
      final List<IntegrationResult> integrations) {
    this(
        file,
        created,
        false,
        gates,
        integrations,
        List.of(),
        null,
        List.of(),
        List.of(),
        List.of());
  }

  /** Creates a result that did not overwrite an existing configuration. */
  public InitResult(
      final Path file,
      final boolean created,
      final List<InitGateOption> gates,
      final List<IntegrationResult> integrations,
      final List<String> pomChanges) {
    this(
        file,
        created,
        false,
        gates,
        integrations,
        pomChanges,
        null,
        List.of(),
        List.of(),
        List.of());
  }
}
