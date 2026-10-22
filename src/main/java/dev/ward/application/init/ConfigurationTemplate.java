package dev.ward.application.init;

import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityGatePhasePolicy;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import java.util.List;

/** Renders the {@code ward.toml} written by {@code ward init}. */
final class ConfigurationTemplate {
  private static final int SECTION_CAPACITY = 96;

  private ConfigurationTemplate() {}

  static String render(
      final List<InitGateOption> gates, final QualityPreset preset, final Project project) {
    return render(gates, preset, project, null);
  }

  /**
   * Renders the configuration; {@code openSpecRoot}, when not {@code null}, is written as the
   * project-relative directory the OpenSpec gates run from.
   */
  static String render(
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Project project,
      final String openSpecRoot) {
    final StringBuilder content = new StringBuilder(SECTION_CAPACITY * (gates.size() + 1));
    content.append("version = 1\n");
    if (preset != null) {
      content.append("preset = \"").append(preset.id()).append("\"\n");
    }
    for (final InitGateOption gate : gates) {
      final List<String> command = gate.command();
      final String goal =
          command.subList(1, command.size()).stream()
              .map(ConfigurationTemplate::escape)
              .reduce((left, right) -> left + " " + right)
              .orElse("");
      content
          .append("\n[quality-gates.")
          .append(gate.id())
          .append("]\nenabled = true\nprofiles = [")
          .append(
              String.join(
                  ", ",
                  QualityGatePhasePolicy.profilesFor(gate.id()).stream()
                      .map(profile -> "\"" + profile + "\"")
                      .toList()))
          .append("]\n");
      if (!SupportedQualityGates.TRACEABILITY.equals(gate.id())) {
        content
            .append("command = \"")
            .append(escape(command.getFirst()))
            .append(command.size() > 1 ? " " : "")
            .append(goal)
            .append("\"\n");
      }
      if (openSpecRoot != null && isOpenSpec(gate.id())) {
        content.append("root = \"").append(escape(openSpecRoot)).append("\"\n");
      }
      partial(content, gate.id(), project, preset);
    }
    return content.toString();
  }

  private static boolean isOpenSpec(final String id) {
    return "openspec".equals(id) || "openspec-archived".equals(id);
  }

  private static void partial(
      final StringBuilder content,
      final String id,
      final Project project,
      final QualityPreset preset) {
    PartialCheckDefaults.forGate(id, project, preset)
        .ifPresent(
            table -> {
              content.append("\n[quality-gates.").append(id).append(".partial]\n");
              array(content, "inputs", table.inputs());
              array(content, "config", table.config());
              if (!table.args().isEmpty()) {
                array(content, "args", table.args());
              }
              string(content, "item", table.item());
              string(content, "separator", table.separator());
            });
  }

  private static void array(
      final StringBuilder content, final String key, final List<String> values) {
    content
        .append(key)
        .append(" = [")
        .append(String.join(", ", values.stream().map(ConfigurationTemplate::quoted).toList()))
        .append("]\n");
  }

  private static void string(final StringBuilder content, final String key, final String value) {
    if (value != null) {
      content.append(key).append(" = ").append(quoted(value)).append('\n');
    }
  }

  private static String quoted(final String value) {
    return "\"" + escape(value) + "\"";
  }

  private static String escape(final String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
