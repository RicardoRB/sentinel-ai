package dev.sentinel.domain.init;

import java.util.List;

/** Validated choices made by an initialization request. */
public record InitSelection(
    List<String> integrations,
    List<String> gates,
    String architecture,
    QualityPreset preset,
    List<String> tools,
    List<String> openSpecValidations,
    List<String> installTools,
    boolean qualityHooks) {
  public InitSelection {
    integrations = List.copyOf(integrations);
    gates = List.copyOf(gates);
    tools = tools == null ? List.of() : List.copyOf(tools);
    openSpecValidations =
        openSpecValidations == null ? List.of() : List.copyOf(openSpecValidations);
    installTools = installTools == null ? List.of() : List.copyOf(installTools);
  }

  public InitSelection(final String integration, final String gate) {
    this(List.of(integration), List.of(gate), null, null, List.of(), List.of(), List.of());
  }

  public InitSelection(final List<String> integrations, final String gate) {
    this(integrations, List.of(gate), null, null, List.of(), List.of(), List.of());
  }

  public InitSelection(final List<String> integrations, final List<String> gates) {
    this(integrations, gates, null, null, List.of(), List.of(), List.of());
  }

  public InitSelection(
      final List<String> integrations, final List<String> gates, final String architecture) {
    this(integrations, gates, architecture, null, List.of(), List.of(), List.of());
  }

  public InitSelection(
      final List<String> integrations,
      final List<String> gates,
      final String architecture,
      final QualityPreset preset) {
    this(integrations, gates, architecture, preset, List.of(), List.of(), List.of(), false);
  }

  public InitSelection(
      final List<String> integrations,
      final List<String> gates,
      final String architecture,
      final QualityPreset preset,
      final List<String> tools,
      final List<String> openSpecValidations,
      final List<String> installTools) {
    this(
        integrations, gates, architecture, preset, tools, openSpecValidations, installTools, false);
  }
}
