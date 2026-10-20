package dev.sentinel.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Runs the generated Gradle {@code pitest} task on Java 25 bytecode. Needs network access and a
 * Gradle executable: {@code ./mvnw -Dtest=GradleMutationJava25SmokeTest
 * -Dsentinel.gradle.binary=/path/to/gradle test}.
 */
@EnabledIfSystemProperty(named = "sentinel.gradle.binary", matches = ".+")
class GradleMutationJava25SmokeTest {
  private static final long TIMEOUT_MINUTES = 10;
  private static final String SOURCE =
      """
      package com.acme;

      /** Totals. */
      public final class Price {
        private Price() {}

        /** Returns the total. */
        public static int total(final int cents, final int quantity) {
          return quantity <= 0 ? 0 : cents * quantity;
        }
      }
      """;
  private static final String TEST =
      """
      package com.acme;

      import static org.junit.jupiter.api.Assertions.assertEquals;

      import org.junit.jupiter.api.Test;

      class PriceTest {
        @Test
        void multiplies() {
          assertEquals(6, Price.total(2, 3));
          assertEquals(0, Price.total(2, 0));
        }
      }
      """;

  @TempDir Path root;

  @ParameterizedTest
  @ValueSource(strings = {"build.gradle", "build.gradle.kts"})
  void generatedPitestRunsOnJava25(final String buildFile)
      throws IOException, InterruptedException {
    final boolean kotlin = buildFile.endsWith(".kts");
    writeProject(buildFile, kotlin);
    new GradleToolConfigurator()
        .apply(
            new Project(root, Language.JAVA, BuildTool.GRADLE, Framework.NONE),
            List.of(
                new InitGateOption(
                    "mutation", "mutation", List.of("gradle", "pitest"), true, "available")),
            QualityPreset.STRICT,
            finding -> false);

    final Path log = root.resolve("pitest.log");
    final Process process =
        new ProcessBuilder(System.getProperty("sentinel.gradle.binary"), "-q", "pitest")
            .directory(root.toFile())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start();
    assertThat(process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)).as("pitest finished").isTrue();

    final String output = Files.readString(log, StandardCharsets.UTF_8);
    assertThat(output).as(output).doesNotContain("Unsupported class file major version");
    assertThat(process.exitValue()).as(output).isZero();
  }

  private void writeProject(final String buildFile, final boolean kotlin) throws IOException {
    final Path main = Files.createDirectories(root.resolve("src/main/java/com/acme"));
    final Path test = Files.createDirectories(root.resolve("src/test/java/com/acme"));
    Files.writeString(main.resolve("Price.java"), SOURCE);
    Files.writeString(test.resolve("PriceTest.java"), TEST);
    Files.writeString(
        root.resolve(kotlin ? "settings.gradle.kts" : "settings.gradle"),
        kotlin ? "rootProject.name = \"shop\"\n" : "rootProject.name = 'shop'\n");
    Files.writeString(root.resolve(buildFile), kotlin ? kotlinBuild() : groovyBuild());
  }

  private static String groovyBuild() {
    return """
        plugins {
            id 'java'
        }

        group = 'com.acme'
        repositories { mavenCentral() }
        java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }
        dependencies {
            testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
            testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
        }
        test { useJUnitPlatform() }
        """;
  }

  private static String kotlinBuild() {
    return """
        plugins {
            java
        }

        group = "com.acme"
        repositories { mavenCentral() }
        java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }
        dependencies {
            testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
            testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        }
        tasks.test { useJUnitPlatform() }
        """;
  }
}
