package dev.sentinel.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.JsonTree;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** {@code sentinel check --path}: the partial check an agent edit runs. */
@DisabledOnOs(OS.WINDOWS)
class SentinelCliPartialCheckTest extends SentinelCliIntegrationSupport {
  private static final String PATH_OPTION = "--path";
  private static final String README = "README.md";
  private static final String SCOPE = "scope";
  private static final String ORDER = "src/main/java/com/acme/Order.java";

  @BeforeEach
  void configure() throws IOException {
    Files.writeString(
        project.resolve(SENTINEL_TOML),
        """
        version = 1
        [quality-gates.format]
        command = "./mvnw spotless:check"
        [quality-gates.checkstyle]
        command = "./mvnw checkstyle:check"
        [quality-gates.checkstyle.partial]
        inputs = ["src/**/*.java"]
        args = ["-Dcheckstyle.includes={paths}"]
        item = "**/{name}"
        separator = ","
        """);
    Files.createDirectories(project.resolve(ORDER).getParent());
    Files.writeString(project.resolve(ORDER), "class Order {}\n");
  }

  @Test
  void jsonReportListsThePathsAndEachGateScope() {
    assertThat(run(CHECK, "-C", project.toString(), FORMAT_OPTION, JSON, PATH_OPTION, README))
        .isEqualTo(ExitCodes.OK);

    final JsonTree report = JsonTree.parse(out.toString());
    assertThat(report.get("partial").get("paths").get(0).asString()).isEqualTo(README);
    assertThat(report.get(CHECKS).get(0).get(SCOPE).asString()).isEqualTo("full");
    assertThat(report.get(CHECKS).get(1).get(SCOPE).asString()).isEqualTo("skipped");
    assertThat(report.get(CHECKS).get(1).get(STATUS).asString()).isEqualTo("SKIPPED");
    assertThat(report.get(STATUS).asString()).isEqualTo("PASSED");
  }

  @Test
  void sourcePathNarrowsTheGateCommand() {
    assertThat(
            run(
                CHECK,
                "-C",
                project.toString(),
                FORMAT_OPTION,
                JSON,
                PATH_OPTION,
                ORDER,
                PATH_OPTION,
                README))
        .isEqualTo(ExitCodes.OK);

    final JsonTree checkstyle = JsonTree.parse(out.toString()).get(CHECKS).get(1);
    assertThat(checkstyle.get(SCOPE).asString()).isEqualTo("narrowed");
    assertThat(checkstyle.get("command").asString())
        .isEqualTo("./mvnw checkstyle:check -Dcheckstyle.includes=**/Order.java");
    assertThat(JsonTree.parse(out.toString()).get("partial").get("paths").size()).isEqualTo(2);
  }

  @Test
  void reportWithoutPathsHasNoPartialFields() {
    assertThat(run(CHECK, "-C", project.toString(), FORMAT_OPTION, JSON)).isEqualTo(ExitCodes.OK);

    final JsonTree report = JsonTree.parse(out.toString());
    assertThat(report.has("partial")).isFalse();
    assertThat(report.get(CHECKS).get(1).has(SCOPE)).isFalse();
    assertThat(report.get(CHECKS).get(1).get("command").asString())
        .isEqualTo("./mvnw checkstyle:check");
  }

  @Test
  void textReportShowsEveryGateAndSaysWhyOneWasSkipped() {
    assertThat(run(CHECK, "-C", project.toString(), PATH_OPTION, README)).isEqualTo(ExitCodes.OK);

    assertThat(out.toString()).contains("– Checkstyle — Not affected by the edited files");
  }

  @Test
  void failingNarrowedGateFailsTheCheck() throws IOException {
    fail();

    assertThat(run(CHECK, "-C", project.toString(), FORMAT_OPTION, JSON, PATH_OPTION, ORDER))
        .isEqualTo(ExitCodes.FAILED);
    assertThat(err.toString()).doesNotContain("{");
  }
}
