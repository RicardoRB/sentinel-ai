package dev.sentinel.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.PomChange;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The opt-in {@code sentinelPaths} filter init writes after the Gradle Checkstyle and PMD blocks.
 * The rendered text was run against Gradle 9.7.1 in both DSLs, with and without the property.
 */
class GradlePathFilterBlockTest {
  // The standard preset filters the checkstyleMain and pmdMain tasks.
  private static final int FILTERED_TASKS = 2;
  private static final String GROOVY = "build.gradle";
  private static final String KOTLIN = "build.gradle.kts";
  private static final String GROOVY_PLUGINS = "plugins {\n    id 'java'\n}\n";
  private static final String KOTLIN_PLUGINS = "plugins {\n    java\n}\n";

  @TempDir Path root;

  @Test
  void groovyCheckstyleAndPmdReadTheOptInSentinelPathsFilter() throws IOException {
    final String script = configure(GROOVY, GROOVY_PLUGINS, QualityPreset.STANDARD);

    for (final String task : List.of("checkstyleMain', Checkstyle", "pmdMain', Pmd")) {
      assertThat(script)
          .contains(
              "tasks.named('"
                  + task
                  + ") {\n"
                  + "    def sentinelPaths = providers.gradleProperty('sentinelPaths')\n"
                  + "    if (sentinelPaths.isPresent()) {\n"
                  + "        def paths = sentinelPaths.get().split(',') as List\n"
                  + "        def root = layout.projectDirectory.asFile.toPath()\n"
                  + "        setSource(source.filter { paths.contains(root.relativize(it.toPath())"
                  + ".toString().replace('\\\\', '/')) })\n"
                  + "    }\n"
                  + "}");
    }
  }

  @Test
  void kotlinCheckstyleAndPmdUseTypedTasksForTheFilter() throws IOException {
    final String script = configure(KOTLIN, KOTLIN_PLUGINS, QualityPreset.STANDARD);

    for (final String task : List.of("Checkstyle>(\"checkstyleMain", "Pmd>(\"pmdMain")) {
      assertThat(script)
          .contains(
              "tasks.named<"
                  + task
                  + "\") {\n"
                  + "    val sentinelPaths = providers.gradleProperty(\"sentinelPaths\")\n"
                  + "    if (sentinelPaths.isPresent) {\n"
                  + "        val paths = sentinelPaths.get().split(\",\")\n"
                  + "        val root = layout.projectDirectory.asFile\n"
                  + "        setSource(source.filter {"
                  + " paths.contains(it.relativeTo(root).invariantSeparatorsPath) })\n"
                  + "    }\n"
                  + "}");
    }
  }

  @Test
  void withoutAPresetNoPathFilterIsWritten() throws IOException {
    assertThat(configure(GROOVY, GROOVY_PLUGINS, null)).doesNotContain("sentinelPaths");
  }

  @Test
  void runningAgainOnAKotlinScriptChangesNothing() throws IOException {
    final String first = configure(KOTLIN, KOTLIN_PLUGINS, QualityPreset.STANDARD);

    final PomChange second =
        new GradleToolConfigurator()
            .apply(project(), gates(), QualityPreset.STANDARD, finding -> false);

    assertThat(second.changed()).isFalse();
    assertThat(Files.readString(root.resolve(KOTLIN))).isEqualTo(first);
    assertThat(first.split("val sentinelPaths =", -1)).hasSize(FILTERED_TASKS + 1);
  }

  private String configure(final String file, final String script, final QualityPreset preset)
      throws IOException {
    Files.writeString(root.resolve(file), script);
    new GradleToolConfigurator().apply(project(), gates(), preset, finding -> false);
    return Files.readString(root.resolve(file));
  }

  private Project project() {
    return new Project(root, Language.JAVA, BuildTool.GRADLE, Framework.NONE);
  }

  private static List<InitGateOption> gates() {
    return List.of("checkstyle", "pmd").stream()
        .map(id -> new InitGateOption(id, id, List.of("./gradlew", id), true, "available"))
        .toList();
  }
}
