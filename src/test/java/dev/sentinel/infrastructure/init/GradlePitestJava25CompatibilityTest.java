package dev.sentinel.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the generated Gradle PIT configuration against Java 25 when Gradle is available. */
class GradlePitestJava25CompatibilityTest {
  private static final int JAVA_25 = 25;

  @Test
  void runsMutationTestingWithJava25(@TempDir final Path root) throws Exception {
    assumeTrue(
        Runtime.version().feature() >= JAVA_25,
        "Java 25 is unavailable; skipping Gradle PIT smoke test");
    assumeTrue(commandAvailable(), "Gradle is not installed; skipping Gradle PIT smoke test");
    writeFixture(root);

    new GradleToolConfigurator()
        .apply(
            new Project(root, Language.JAVA, BuildTool.GRADLE, Framework.NONE),
            List.of(gate()),
            null,
            finding -> false);

    final Process process =
        new ProcessBuilder("gradle", "--no-daemon", "pitest")
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    final int exitCode = process.waitFor();

    assertThat(exitCode).isZero();
    assertThat(output).doesNotContain("Unsupported class file major version 69");
  }

  private static boolean commandAvailable() throws InterruptedException {
    try {
      final Process process =
          new ProcessBuilder("gradle", "--version").redirectErrorStream(true).start();
      process.getInputStream().readAllBytes();
      return process.waitFor() == 0;
    } catch (IOException e) {
      return false;
    }
  }

  private static void writeFixture(final Path root) throws IOException {
    Files.writeString(
        root.resolve("build.gradle"),
        "plugins { id 'java' }\n\nrepositories { mavenCentral() }\n"
            + "dependencies { testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2' }\n"
            + "test { useJUnitPlatform() }\n");
    Files.createDirectories(root.resolve("src/main/java/example"));
    Files.createDirectories(root.resolve("src/test/java/example"));
    Files.writeString(
        root.resolve("src/main/java/example/Calculator.java"),
        "package example;\npublic final class Calculator {\n"
            + "  public int add(int left, int right) { return left + right; }\n}\n");
    Files.writeString(
        root.resolve("src/test/java/example/CalculatorTest.java"),
        "package example;\nimport static org.junit.jupiter.api.Assertions.assertEquals;\n"
            + "import org.junit.jupiter.api.Test;\n"
            + "class CalculatorTest { @Test void adds() { assertEquals(3, new Calculator().add(1, 2)); } }\n");
  }

  private static InitGateOption gate() {
    return new InitGateOption(
        "mutation", "mutation", List.of("gradle", "pitest"), true, "available");
  }
}
