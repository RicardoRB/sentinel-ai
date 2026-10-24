package dev.ward.application.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.OwnedSection;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.QualityPreset.PresetRules;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Renders the {@code AGENTS.md} section that tells coding agents the limits the selected gates
 * enforce, so they meet them before the first check instead of after it fails.
 */
final class AgentRulesTemplate {
  static final String BEGIN = "<!-- ward-owned: agent-rules v1 begin -->";
  static final String END = "<!-- ward-owned: agent-rules v1 end -->";
  static final String CHECKSTYLE_FILE = "config/checkstyle.xml";
  static final String PMD_FILE = "config/pmd-ruleset.xml";
  private static final int PERCENT = 100;
  private static final String PROPERTY_MAX = ".max";

  /** Checkstyle modules agents most often trip over, with the rule each one enforces. */
  private static final Map<String, String> CHECKSTYLE_RULES =
      Map.ofEntries(
          Map.entry("MissingJavadocType", "Javadoc on public types and methods"),
          Map.entry("MagicNumber", "no magic numbers (use named constants)"),
          Map.entry("AvoidStarImport", "no star or unused imports"),
          Map.entry("IllegalCatch", "no catch of overly broad exception types"),
          Map.entry("EmptyCatchBlock", "no empty catch blocks"),
          Map.entry("MissingSwitchDefault", "every `switch` has a `default`"),
          Map.entry("OneStatementPerLine", "one declaration or statement per line"),
          Map.entry("HiddenField", "no locals or parameters that hide fields"));

  private AgentRulesTemplate() {}

  /**
   * Returns the section for {@code gates}. Limits come from {@code preset}; a rule file in {@code
   * userOwned} is the user's, so the section points to it instead of quoting preset values.
   */
  static OwnedSection render(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final List<Path> userOwned) {
    final Set<String> ids = gates.stream().map(InitGateOption::id).collect(Collectors.toSet());
    final Set<String> owned =
        userOwned.stream()
            .map(file -> project.root().relativize(file.toAbsolutePath()).toString())
            .collect(Collectors.toSet());
    final List<String> lines = new ArrayList<>();
    lines.add("## Ward quality gates");
    lines.add("");
    lines.add(
        "`ward.toml` defines the gates: "
            + gates.stream().map(gate -> "`" + gate.id() + "`").collect(Collectors.joining(", "))
            + ".");
    lines.add("");
    lines.add(
        "- Fix the cause of every finding. Do not add suppressions or exclusions, disable"
            + " rules, or lower thresholds to make a gate pass.");
    lines.add(
        "- Run `ward check` after changes and `ward check --profile ci` before" + " finishing.");
    if (preset != null) {
      lines.addAll(limits(ids, preset.rules(), owned));
    }
    return new OwnedSection(
        project.root().resolve("AGENTS.md"), BEGIN, END, String.join("\n", lines) + "\n", false);
  }

  /** Returns the gates that can get a limit line, for the gate contract test. */
  static Set<String> limitGates() {
    return limitLines(QualityPreset.STRICT.rules(), Set.of()).keySet();
  }

  /** Returns one line per selected gate that has a limit worth stating, in gate order. */
  private static List<String> limits(
      final Set<String> ids, final PresetRules rules, final Set<String> owned) {
    return limitLines(rules, owned).entrySet().stream()
        .filter(entry -> ids.contains(entry.getKey()))
        .map(Map.Entry::getValue)
        .toList();
  }

  /** Returns the limit line of each gate that has one, keyed by gate ID, in gate order. */
  private static Map<String, String> limitLines(final PresetRules rules, final Set<String> owned) {
    final Map<String, String> lines = new LinkedHashMap<>();
    lines.put(
        "checkstyle",
        owned.contains(CHECKSTYLE_FILE)
            ? "- Checkstyle: follow the rules in `" + CHECKSTYLE_FILE + "`."
            : "- Checkstyle (`" + CHECKSTYLE_FILE + "`): " + checkstyle(rules) + ".");
    lines.put(
        "pmd",
        owned.contains(PMD_FILE)
            ? "- PMD: follow the rules in `" + PMD_FILE + "`."
            : "- PMD (`" + PMD_FILE + "`): " + pmd(rules) + ".");
    lines.put(
        "spotbugs",
        "- SpotBugs: effort "
            + rules.spotbugsEffort()
            + ", threshold "
            + rules.spotbugsThreshold()
            + ". Copy mutable collections in and out of records and constructors.");
    lines.put(
        "coverage",
        "- Coverage (JaCoCo): " + coverage(rules) + ". New code needs tests that assert behavior.");
    if (rules.mutationThreshold() != null) {
      lines.put(
          "mutation",
          "- Mutation (PIT): at least " + rules.mutationThreshold() + "% of mutants killed.");
    }
    lines.put(
        "archunit", "- ArchUnit: keep the architecture tests passing; do not add `@ArchIgnore`.");
    lines.put("enforcer", "- Enforcer: " + String.join(", ", rules.enforcerRules()) + ".");
    return lines;
  }

  private static String checkstyle(final PresetRules rules) {
    final Map<String, String> properties = rules.checkstyleProperties();
    final List<String> parts = new ArrayList<>();
    limit(parts, properties, "FileLength", "files of at most %s lines");
    limit(parts, properties, "MethodLength", "methods of at most %s lines");
    limit(parts, properties, "ParameterNumber", "at most %s parameters");
    limit(parts, properties, "CyclomaticComplexity", "cyclomatic complexity of at most %s");
    rules.checkstyleModules().stream()
        .map(CHECKSTYLE_RULES::get)
        .filter(Objects::nonNull)
        .forEach(parts::add);
    return String.join("; ", parts);
  }

  private static void limit(
      final List<String> parts,
      final Map<String, String> properties,
      final String module,
      final String format) {
    final String max = properties.get(module + PROPERTY_MAX);
    if (max != null) {
      parts.add(String.format(format, max));
    }
  }

  private static String pmd(final PresetRules rules) {
    final List<String> parts = new ArrayList<>();
    for (final String rule : rules.pmdRules()) {
      final String name = rule.substring(rule.lastIndexOf('/') + 1).replace(".xml", "");
      final String part =
          switch (name) {
            case "MethodArgumentCouldBeFinal" -> "final parameters";
            case "LocalVariableCouldBeFinal" -> "final local variables";
            case "UseExplicitTypes" -> "no `var`";
            default -> name;
          };
      parts.add(part);
    }
    return String.join("; ", parts);
  }

  private static String coverage(final PresetRules rules) {
    return new TreeMap<>(rules.jacocoMinimums())
        .entrySet().stream()
            .map(
                entry ->
                    entry.getKey().toLowerCase(Locale.ROOT)
                        + " at least "
                        + Math.round(entry.getValue() * PERCENT)
                        + "%")
            .collect(Collectors.joining(", "));
  }
}
