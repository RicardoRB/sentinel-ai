package dev.sentinel.application.init;

import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.QualityPreset;
import java.util.List;

/** Renders the {@code sentinel.toml} written by {@code sentinel init}. */
final class ConfigurationTemplate {
  private static final int SECTION_CAPACITY = 96;

  private ConfigurationTemplate() {}

  static String render(final List<InitGateOption> gates, final QualityPreset preset) {
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
          .append("]\nenabled = true\ncommand = \"")
          .append(escape(command.getFirst()))
          .append(' ')
          .append(goal)
          .append("\"\n");
    }
    return content.toString();
  }

  private static String escape(final String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
