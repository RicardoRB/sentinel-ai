package dev.sentinel.infrastructure.init;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.BuildToolConfiguration;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.PomChange;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;

/** Adds conservative, reversible Gradle plugin declarations for selected init gates. */
public final class GradleToolConfigurator implements BuildToolConfiguration {
  private static final String MARKER = "// Sentinel-managed quality gate configuration";

  private static final List<Plugin> PLUGINS =
      List.of(
          new Plugin("format", "com.diffplug.spotless", "6.25.0"),
          new Plugin("checkstyle", "checkstyle", ""),
          new Plugin("pmd", "pmd", ""),
          new Plugin("spotbugs", "com.github.spotbugs", "6.0.26"),
          new Plugin("coverage", "jacoco", ""),
          new Plugin("dependency-check", "org.owasp.dependencycheck", "8.4.0"),
          new Plugin("mutation", "info.solidsoft.pitest", "1.15.0"),
          new Plugin("sonar", "org.sonarqube", "5.1.0.4882"),
          new Plugin("license", "com.github.hierynomus.license", "0.16.1"),
          new Plugin("api-compat", "me.champeau.japicmp", "0.4.3"));

  @Inject
  public GradleToolConfigurator() {}

  @Override
  public PomChange apply(final Project project, final List<InitGateOption> gates) {
    return apply(project, gates, null, new Object());
  }

  @Override
  public PomChange apply(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Object approval) {
    final Path buildFile = buildFile(project.root());
    final Set<String> selected = gates.stream().map(InitGateOption::id).collect(Collectors.toSet());
    try {
      final String original = Files.readString(buildFile);
      final StringBuilder updated = new StringBuilder(original);
      final List<String> tools = new ArrayList<>();
      appendPlugins(project, selected, updated, tools);
      appendSpecialConfigurations(project, selected, updated, tools);
      if (!tools.isEmpty()) {
        Files.writeString(
            buildFile,
            updated.toString(),
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE);
      }
      return new PomChange(buildFile, original, tools);
    } catch (IOException e) {
      throw new SentinelException(
          "Could not configure Gradle tools in " + buildFile + ": " + e.getMessage(), e);
    }
  }

  private static void appendPlugins(
      final Project project,
      final Set<String> selected,
      final StringBuilder updated,
      final List<String> tools) {
    for (final Plugin plugin : PLUGINS) {
      if (selected.contains(plugin.gate()) && updated.indexOf(plugin.id()) < 0) {
        updated.append(plugin.declaration(project)).append('\n');
        tools.add(plugin.id());
      }
    }
  }

  private static void appendSpecialConfigurations(
      final Project project,
      final Set<String> selected,
      final StringBuilder updated,
      final List<String> tools) {
    if (selected.contains("enforcer") && updated.indexOf("forbiddenApis") < 0) {
      updated.append(enforcementDeclaration(project)).append('\n');
      tools.add("gradle-enforcer");
    }
    if (selected.contains("archunit") && updated.indexOf("archunit-junit5") < 0) {
      updated.append(archunitDeclaration(project)).append('\n');
      tools.add("archunit-junit5");
    }
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
      throw new SentinelException("Could not restore " + change.file() + ": " + e.getMessage(), e);
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
    throw new SentinelException(
        "Could not configure Gradle tools: no build.gradle or build.gradle.kts found.");
  }

  private static String enforcementDeclaration(final Project project) {
    return dslDeclaration(
        project,
        "\n" + MARKER + "\ntasks.named('check') { dependsOn 'checkstyleMain' }",
        "\n" + MARKER + "\ntasks.named(\"check\") { dependsOn(\"checkstyleMain\") }");
  }

  private static String archunitDeclaration(final Project project) {
    return dslDeclaration(
        project,
        "\n"
            + MARKER
            + "\ndependencies { testImplementation 'com.tngtech.archunit:archunit-junit5:1.3.0' }",
        "\n"
            + MARKER
            + "\ndependencies { testImplementation(\"com.tngtech.archunit:archunit-junit5:1.3.0\") }");
  }

  private record Plugin(String gate, String id, String version) {
    String declaration(final Project project) {
      return dslDeclaration(
          project,
          "\n" + MARKER + "\nplugins { id '" + id + "'" + versionClause() + " }",
          "\n" + MARKER + "\nplugins { id(\"" + id + "\")" + kotlinVersionClause() + " }");
    }

    private String versionClause() {
      return version.isBlank() ? "" : " version '" + version + "'";
    }

    private String kotlinVersionClause() {
      return version.isBlank() ? "" : " version \"" + version + "\"";
    }
  }

  private static String dslDeclaration(
      final Project project, final String groovy, final String kotlin) {
    return Files.isRegularFile(project.root().resolve("build.gradle.kts")) ? kotlin : groovy;
  }
}
