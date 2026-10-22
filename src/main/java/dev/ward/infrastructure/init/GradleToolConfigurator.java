package dev.ward.infrastructure.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.PomChange;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * Adds conservative, reversible Gradle configuration for selected init gates: plugin declarations
 * inside the {@code plugins {}} block, and blocks that wire the plugins to the generated rule files
 * and thresholds.
 */
public final class GradleToolConfigurator implements BuildToolConfiguration {
  private static final List<Plugin> PLUGINS =
      List.of(
          new Plugin("format", "com.diffplug.spotless", "8.10.4"),
          new Plugin("checkstyle", "checkstyle", ""),
          new Plugin("pmd", "pmd", ""),
          new Plugin("spotbugs", "com.github.spotbugs", "6.0.26"),
          new Plugin("coverage", "jacoco", ""),
          new Plugin("dependency-check", "org.owasp.dependencycheck", "12.1.0"),
          new Plugin("mutation", "info.solidsoft.pitest", "1.19.0"),
          new Plugin("sonar", "org.sonarqube", "5.1.0.4882"),
          new Plugin("license", "com.github.hierynomus.license", "0.16.1"),
          new Plugin("api-compat", "me.champeau.gradle.japicmp", "0.4.6"));

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public GradleToolConfigurator() {}

  @Override
  public PomChange apply(final Project project, final List<InitGateOption> gates) {
    return apply(project, gates, null, finding -> false);
  }

  @Override
  public PomChange apply(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Predicate<? super CompatibilityFinding> approval) {
    final GradleBuildFileEdit edit = compute(project, gates, preset);
    if (!edit.tools().isEmpty()) {
      try {
        Files.writeString(
            edit.buildFile(),
            edit.content(),
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE);
      } catch (IOException e) {
        throw new WardException(
            "Could not configure Gradle tools in " + edit.buildFile() + ": " + e.getMessage(), e);
      }
    }
    return new PomChange(edit.buildFile(), edit.original(), edit.tools());
  }

  @Override
  public PlannedFileChange plan(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean approved) {
    final GradleBuildFileEdit edit = compute(project, gates, preset);
    return new PlannedFileChange(
        edit.buildFile(),
        edit.details().isEmpty()
            ? PlannedFileChange.Action.UNCHANGED
            : PlannedFileChange.Action.MODIFY,
        edit.details());
  }

  /** Builds the edited build file in memory; shared by {@link #apply} and {@link #plan}. */
  private static GradleBuildFileEdit compute(
      final Project project, final List<InitGateOption> gates, final QualityPreset preset) {
    final Path buildFile = buildFile(project.root());
    final Set<String> selected = gates.stream().map(InitGateOption::id).collect(Collectors.toSet());
    final GradleBuildFileEdit edit;
    try {
      edit = new GradleBuildFileEdit(buildFile, Files.readString(buildFile));
    } catch (IOException e) {
      throw new WardException(
          "Could not configure Gradle tools in " + buildFile + ": " + e.getMessage(), e);
    }
    final boolean kotlin = Files.isRegularFile(project.root().resolve("build.gradle.kts"));
    final List<String> declarations = new ArrayList<>();
    for (final Plugin plugin : PLUGINS) {
      if (selected.contains(plugin.gate()) && !edit.contains(plugin.id())) {
        declarations.add(plugin.declaration(kotlin));
        edit.record(plugin.id(), plugin.version());
      }
    }
    edit.addPlugins(declarations);
    GradleBuildBlocks.append(edit, selected, preset, kotlin);
    return edit;
  }

  @Override
  public void rollback(final PomChange change) {
    if (change == null || !change.changed()) {
      return;
    }
    try {
      Files.writeString(
          change.file(),
          change.originalContent(),
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
    } catch (IOException e) {
      throw new WardException("Could not restore " + change.file() + ": " + e.getMessage(), e);
    }
  }

  private static Path buildFile(final Path root) {
    final Path kotlin = root.resolve("build.gradle.kts");
    final Path groovy = root.resolve("build.gradle");
    if (Files.isRegularFile(kotlin)) {
      return kotlin;
    }
    if (Files.isRegularFile(groovy)) {
      return groovy;
    }
    throw new WardException(
        "Could not configure Gradle tools: no build.gradle or build.gradle.kts found.");
  }

  private record Plugin(String gate, String id, String version) {
    String declaration(final boolean kotlin) {
      return kotlin
          ? "id(\"" + id + "\")" + kotlinVersionClause()
          : "id '" + id + "'" + versionClause();
    }

    private String versionClause() {
      return version.isBlank() ? "" : " version '" + version + "'";
    }

    private String kotlinVersionClause() {
      return version.isBlank() ? "" : " version \"" + version + "\"";
    }
  }
}
