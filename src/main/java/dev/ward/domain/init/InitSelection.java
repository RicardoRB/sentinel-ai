package dev.ward.domain.init;

import java.util.List;

/**
 * Validated choices made by an initialization request. {@code ci} names the CI provider whose
 * workflow init writes, or is {@code null} for none; {@code agentRules} writes the gate rules into
 * {@code AGENTS.md}.
 */
public record InitSelection(
    List<String> integrations,
    List<String> gates,
    String architecture,
    QualityPreset preset,
    List<String> tools,
    List<String> openSpecValidations,
    List<String> installTools,
    boolean qualityHooks,
    String ci,
    boolean agentRules) {
  /** Copies the lists; missing optional lists mean empty. */
  public InitSelection {
    integrations = List.copyOf(integrations);
    gates = List.copyOf(gates);
    tools = tools == null ? List.of() : List.copyOf(tools);
    openSpecValidations =
        openSpecValidations == null ? List.of() : List.copyOf(openSpecValidations);
    installTools = installTools == null ? List.of() : List.copyOf(installTools);
  }

  /** Selects one integration and one gate. */
  public InitSelection(final String integration, final String gate) {
    this(
        List.of(integration),
        List.of(gate),
        null,
        null,
        List.of(),
        List.of(),
        List.of(),
        false,
        null,
        false);
  }

  /** Selects {@code integrations} and one gate. */
  public InitSelection(final List<String> integrations, final String gate) {
    this(
        integrations,
        List.of(gate),
        null,
        null,
        List.of(),
        List.of(),
        List.of(),
        false,
        null,
        false);
  }

  /** Selects {@code integrations} and {@code gates}, without preset, tools, or hooks. */
  public InitSelection(final List<String> integrations, final List<String> gates) {
    this(integrations, gates, null, null, List.of(), List.of(), List.of(), false, null, false);
  }

  /** Selects {@code integrations} and {@code gates} with an architecture style. */
  public InitSelection(
      final List<String> integrations, final List<String> gates, final String architecture) {
    this(
        integrations,
        gates,
        architecture,
        null,
        List.of(),
        List.of(),
        List.of(),
        false,
        null,
        false);
  }

  /** Selects {@code integrations} and {@code gates} with an architecture style and preset. */
  public InitSelection(
      final List<String> integrations,
      final List<String> gates,
      final String architecture,
      final QualityPreset preset) {
    this(
        integrations,
        gates,
        architecture,
        preset,
        List.of(),
        List.of(),
        List.of(),
        false,
        null,
        false);
  }
}
