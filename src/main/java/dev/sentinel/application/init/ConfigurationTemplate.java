package dev.sentinel.application.init;

import dev.sentinel.domain.gate.SupportedQualityGates;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.QualityGatePhasePolicy;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.project.Project;
import java.util.List;

/** Renders the {@code sentinel.toml} written by {@code sentinel init}. */
final class ConfigurationTemplate {
  private static final int SECTION_CAPACITY = 96;

  private ConfigurationTemplate() {}

  static String render(
      final List<InitGateOption> gates, final QualityPreset preset, final Project project) {
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
      partial(content, gate.id(), project, preset);
    }
    return content.toString();
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
