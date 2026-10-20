package dev.sentinel.infrastructure.init;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.infrastructure.init.PomElements.Element;
import java.util.List;
import java.util.Optional;

/**
 * Inserts declarations into the project-level elements of a pom.xml. Only the inserted text is new;
 * the rest of the user's file, including its formatting, is preserved byte for byte.
 */
final class PomXml {
  private static final String DEFAULT_UNIT = "  ";
  private static final String PLUGINS = "plugins";
  private static final String BUILD = "build";
  private static final String PLUGIN_MANAGEMENT = "pluginManagement";

  private PomXml() {}

  /** Adds a plugin to {@code project/build/plugins}, creating missing parents. */
  static String addPlugin(final String pom, final String plugin) {
    final Element project = PomElements.project(pom);
    final Optional<Element> build = PomElements.child(pom, project, BUILD);
    if (build.isEmpty()) {
      return insertChild(pom, project, "project", wrap(BUILD, wrap(PLUGINS, plugin)));
    }
    final Optional<Element> plugins = PomElements.child(pom, build.get(), PLUGINS);
    if (plugins.isEmpty()) {
      return insertChild(pom, build.get(), BUILD, wrap(PLUGINS, plugin));
    }
    return insertChild(pom, plugins.get(), PLUGINS, plugin);
  }

  /** Adds a plugin to {@code project/build/pluginManagement/plugins}, creating missing parents. */
  static String addManagedPlugin(final String pom, final String plugin) {
    final Element project = PomElements.project(pom);
    final Optional<Element> build = PomElements.child(pom, project, BUILD);
    if (build.isEmpty()) {
      return insertChild(
          pom, project, "project", wrap(BUILD, wrap(PLUGIN_MANAGEMENT, wrap(PLUGINS, plugin))));
    }
    final Optional<Element> management = PomElements.child(pom, build.get(), PLUGIN_MANAGEMENT);
    if (management.isEmpty()) {
      return insertChild(pom, build.get(), BUILD, wrap(PLUGIN_MANAGEMENT, wrap(PLUGINS, plugin)));
    }
    final Optional<Element> plugins = PomElements.child(pom, management.get(), PLUGINS);
    if (plugins.isEmpty()) {
      return insertChild(pom, management.get(), PLUGIN_MANAGEMENT, wrap(PLUGINS, plugin));
    }
    return insertChild(pom, plugins.get(), PLUGINS, plugin);
  }

  /** Adds a dependency to {@code project/dependencies}, creating the element when missing. */
  static String addDependency(final String pom, final String dependency) {
    final Element project = PomElements.project(pom);
    final Optional<Element> dependencies = PomElements.child(pom, project, "dependencies");
    if (dependencies.isEmpty()) {
      return insertChild(pom, project, "project", wrap("dependencies", dependency));
    }
    return insertChild(pom, dependencies.get(), "dependencies", dependency);
  }

  /**
   * Replaces the version of {@code artifact}'s project-level plugin declaration. Returns empty when
   * the plugin is not declared in {@code project/build/plugins}.
   *
   * @throws SentinelException when the declaration's version is not exactly {@code current}
   */
  static Optional<String> replacePluginVersion(
      final String pom, final String artifact, final String current, final String selected) {
    final Optional<Element> plugin =
        PomElements.child(pom, PomElements.project(pom), BUILD)
            .flatMap(build -> PomElements.child(pom, build, PLUGINS))
            .flatMap(plugins -> PomElements.declaration(pom, plugins, artifact));
    if (plugin.isEmpty()) {
      return Optional.empty();
    }
    final Element version =
        PomElements.child(pom, plugin.get(), "version")
            .filter(element -> PomElements.text(pom, element).equals(current))
            .orElseThrow(
                () ->
                    new SentinelException(
                        "Could not safely update managed Maven plugin " + artifact + "."));
    return Optional.of(
        pom.substring(0, version.openEnd()) + selected + pom.substring(version.closeStart()));
  }

  private static String insertChild(
      final String xml, final Element parent, final String name, final String block) {
    final String parentIndent = lineIndent(xml, parent.openStart());
    final String childIndent = parentIndent + indentUnit(xml);
    final String content = indent(block, childIndent);
    if (parent.selfClosing()) {
      final String openTag =
          xml.substring(parent.openStart(), parent.openEnd() - 2).stripTrailing();
      return xml.substring(0, parent.openStart())
          + openTag
          + ">\n"
          + content
          + parentIndent
          + "</"
          + name
          + ">"
          + xml.substring(parent.openEnd());
    }
    final int lineStart = xml.lastIndexOf('\n', parent.closeStart() - 1) + 1;
    if (xml.substring(lineStart, parent.closeStart()).isBlank()) {
      return xml.substring(0, lineStart) + content + xml.substring(lineStart);
    }
    return xml.substring(0, parent.closeStart())
        + "\n"
        + content
        + parentIndent
        + xml.substring(parent.closeStart());
  }

  /** Leading whitespace of the line at {@code position}, or none when text precedes it. */
  private static String lineIndent(final String xml, final int position) {
    final int lineStart = xml.lastIndexOf('\n', position - 1) + 1;
    final String prefix = xml.substring(lineStart, position);
    return prefix.isBlank() ? prefix : "";
  }

  /** Indentation of the first indented element line, which is one nesting level in the pom. */
  private static String indentUnit(final String xml) {
    return xml.lines()
        .map(line -> line.substring(0, line.length() - line.stripLeading().length()))
        .filter(prefix -> !prefix.isEmpty() && xml.contains("\n" + prefix + "<"))
        .findFirst()
        .orElse(DEFAULT_UNIT);
  }

  private static String wrap(final String name, final String block) {
    return "<" + name + ">\n" + indent(block, DEFAULT_UNIT) + "</" + name + ">\n";
  }

  /** Re-indents a snippet so its least-indented lines start at {@code indent}. */
  private static String indent(final String block, final String indent) {
    final List<String> lines = block.lines().filter(line -> !line.isBlank()).toList();
    final int common =
        lines.stream()
            .mapToInt(line -> line.length() - line.stripLeading().length())
            .min()
            .orElse(0);
    final StringBuilder result = new StringBuilder();
    for (final String line : lines) {
      result.append(indent).append(line.substring(common).stripTrailing()).append('\n');
    }
    return result.toString();
  }
}
