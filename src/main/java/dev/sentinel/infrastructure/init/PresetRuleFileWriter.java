package dev.sentinel.infrastructure.init;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.init.RuleFileChange;
import dev.sentinel.domain.init.RuleFileGeneration;
import dev.sentinel.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

/** Writes ownership-marked Maven rule files for a quality preset. */
public final class PresetRuleFileWriter implements RuleFileGeneration {
  private static final String MARKER = "<!-- managed-by: sentinel preset=";

  @Inject
  public PresetRuleFileWriter() {}

  @Override
  public RuleFileChange apply(
      final Project project, final QualityPreset preset, final List<InitGateOption> gates) {
    if (preset == null) {
      return new RuleFileChange(project.root().resolve("config"), false, Map.of(), List.of());
    }
    final Path config = project.root().resolve("config");
    final boolean createdDirectory = !Files.exists(config);
    final Map<Path, String> originals = new LinkedHashMap<>();
    final List<Path> preserved = new ArrayList<>();
    try {
      Files.createDirectories(config);
      final List<String> ids = gates.stream().map(InitGateOption::id).toList();
      if (ids.contains("pmd")) {
        write(config.resolve("pmd-ruleset.xml"), pmd(preset), preset, originals, preserved);
      }
      if (ids.contains("checkstyle")) {
        write(config.resolve("checkstyle.xml"), checkstyle(preset), preset, originals, preserved);
      }
      if (ids.contains("spotbugs") && preset == QualityPreset.STANDARD) {
        write(
            config.resolve("spotbugs-exclude.xml"), spotbugs(preset), preset, originals, preserved);
      }
      if (ids.contains("enforcer")) {
        write(config.resolve("enforcer-rules.xml"), enforcer(preset), preset, originals, preserved);
      }
      return new RuleFileChange(config, createdDirectory, originals, preserved);
    } catch (IOException e) {
      throw new SentinelException("Could not write preset rule files: " + e.getMessage(), e);
    }
  }

  private static void write(
      final Path file,
      final String body,
      final QualityPreset preset,
      final Map<Path, String> originals,
      final List<Path> preserved)
      throws IOException {
    final String existing = Files.exists(file) ? Files.readString(file) : null;
    if (existing != null && !existing.startsWith(MARKER)) {
      preserved.add(file);
      return;
    }
    originals.put(file, existing);
    Files.writeString(file, MARKER + preset.id() + " -->\n" + body);
  }

  @Override
  public void rollback(final RuleFileChange change) {
    if (change == null) {
      return;
    }
    try {
      for (final Map.Entry<Path, String> entry : change.originals().entrySet()) {
        if (entry.getValue() == null) {
          Files.deleteIfExists(entry.getKey());
        } else {
          Files.writeString(entry.getKey(), entry.getValue());
        }
      }
      if (change.createdDirectory() && Files.isDirectory(change.configDirectory())) {
        Files.deleteIfExists(change.configDirectory());
      }
    } catch (IOException e) {
      throw new SentinelException("Could not roll back preset rule files: " + e.getMessage(), e);
    }
  }

  private static String pmd(final QualityPreset preset) {
    return "<ruleset name=\"Sentinel "
        + preset.id()
        + " PMD\" xmlns=\"http://pmd.sourceforge.net/ruleset/2.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
        + preset.rules().pmdRules().stream()
            .map(rule -> "  <rule ref=\"" + rule + "\"/>\n")
            .reduce("", String::concat)
        + "</ruleset>\n";
  }

  private static String checkstyle(final QualityPreset preset) {
    return "<module name=\"Checker\">\n  <module name=\"TreeWalker\">\n"
        + preset.rules().checkstyleModules().stream()
            .map(rule -> "    <module name=\"" + rule + "\"/>\n")
            .reduce("", String::concat)
        + "  </module>\n</module>\n";
  }

  private static String spotbugs(final QualityPreset preset) {
    return "<FindBugsFilter>\n"
        + preset.rules().spotbugsExcludes().stream()
            .map(bug -> "  <Match><Bug pattern=\"" + bug + "\"/></Match>\n")
            .reduce("", String::concat)
        + "</FindBugsFilter>\n";
  }

  private static String enforcer(final QualityPreset preset) {
    return "<rules>\n"
        + preset.rules().enforcerRules().stream()
            .map(
                rule ->
                    "requireMavenVersion".equals(rule)
                        ? "  <requireMavenVersion><version>[3.9,)</version></requireMavenVersion>\n"
                        : "  <" + rule + "/>\n")
            .reduce("", String::concat)
        + "</rules>\n";
  }
}
