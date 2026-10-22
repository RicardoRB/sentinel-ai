package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.PomChange;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Gradle accepts the generated build script, and the plugins use what init generates. */
class GradleQualityConfigurationTest {
  private static final List<String> STRICT_GATES =
      List.of(
          "format",
          "checkstyle",
          "pmd",
          "spotbugs",
          "coverage",
          "archunit",
          "enforcer",
          "mutation",
          "dependency-check");

  @TempDir Path root;

  @Test
  void addsPluginsInsideTheExistingPluginsBlock() throws IOException {
    final String script =
        configure("build.gradle", "plugins {\n    id 'java'\n}\n\nrepositories {}\n", null);

    assertThat(script)
        .startsWith(
            "plugins {\n    "
                + GradlePluginsBlock.MARKER
                + "\n    id 'com.diffplug.spotless' version '8.10.4'\n")
        .contains("    id 'info.solidsoft.pitest' version '1.19.0'\n    id 'java'\n}\n");
    assertThat(script.indexOf("plugins {")).isEqualTo(script.lastIndexOf("plugins {"));
  }

  @Test
  void createsAPluginsBlockAfterBuildscriptWhenThereIsNone() throws IOException {
    final String script =
        configure(
            "build.gradle",
            "buildscript {\n    repositories { mavenCentral() }\n}\napply plugin: 'java'\n",
            null);

    assertThat(script)
        .startsWith("buildscript {\n    repositories { mavenCentral() }\n}\n\nplugins {\n")
        .contains("}\napply plugin: 'java'\n");
  }

  @Test
  void prependsAPluginsBlockToAScriptWithoutOne() throws IOException {
    assertThat(configure("build.gradle", "apply plugin: 'java'\n", null))
        .startsWith("plugins {\n    " + GradlePluginsBlock.MARKER);
  }

  @Test
  void wiresPresetRuleFilesThresholdsAndMainSourcesForGroovy() throws IOException {
    final String script =
        configure("build.gradle", "plugins {\n    id 'java'\n}\n", QualityPreset.STRICT);

    assertThat(script)
        .contains(
            "checkstyle {\n    configFile = file('config/checkstyle.xml')\n"
                + "    sourceSets = [project.sourceSets.main]\n}",
            "pmd {\n    ruleSetFiles = files('config/pmd-ruleset.xml')\n    ruleSets = []\n",
            "spotless {\n    java {\n        googleJavaFormat()\n",
            "\njacocoTestCoverageVerification {\n",
            "counter = 'BRANCH'\n                minimum = 0.75",
            "counter = 'LINE'\n                minimum = 0.85",
            "pitest {\n    pitestVersion = '1.30.1'\n    junit5PluginVersion = '1.2.3'\n"
                + "    mutationThreshold = 60\n}")
        .doesNotContain("historyInputFile", "historyOutputFile");
  }

  @Test
  void usesKotlinSyntaxAndTaskAccessorsForKotlinScripts() throws IOException {
    final String script =
        configure("build.gradle.kts", "plugins {\n    java\n}\n", QualityPreset.STRICT);

    assertThat(script)
        .contains(
            "    id(\"com.diffplug.spotless\") version \"8.10.4\"\n",
            "configFile = file(\"config/checkstyle.xml\")",
            "sourceSets = listOf(project.sourceSets.main.get())",
            "ruleSets = listOf()",
            "\ntasks.jacocoTestCoverageVerification {\n",
            "minimum = \"0.85\".toBigDecimal()",
            "pitestVersion = \"1.30.1\"",
            "junit5PluginVersion = \"1.2.3\"")
        .doesNotContain("historyInputFile", "historyOutputFile");
  }

  @Test
  void withoutAPresetKeepsGradleRuleDefaultsAndEnforcesEightyPercentLines() throws IOException {
    final String script = configure("build.gradle", "plugins {\n    id 'java'\n}\n", null);

    assertThat(script)
        .doesNotContain("config/checkstyle.xml", "config/pmd-ruleset.xml", "mutationThreshold")
        .contains("counter = 'LINE'\n                minimum = 0.8\n");
  }

  @Test
  void runningAgainChangesNothing() throws IOException {
    final String first =
        configure("build.gradle", "plugins {\n    id 'java'\n}\n", QualityPreset.STRICT);

    final PomChange second =
        new GradleToolConfigurator()
            .apply(project(), gates(), QualityPreset.STRICT, finding -> false);

    assertThat(second.changed()).isFalse();
    assertThat(Files.readString(root.resolve("build.gradle"))).isEqualTo(first);
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
    return STRICT_GATES.stream()
        .map(id -> new InitGateOption(id, id, List.of("./gradlew", id), true, "available"))
        .toList();
  }
}
