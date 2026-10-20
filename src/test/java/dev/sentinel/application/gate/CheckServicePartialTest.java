package dev.sentinel.application.gate;

import static dev.sentinel.ProjectFixtures.PLAIN_POM;
import static dev.sentinel.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.sentinel.domain.FakeCommandExecutor;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateScope;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.gate.SupportedQualityGates;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Partial checks: which gates run after an edit, with which argument vector. */
class CheckServicePartialTest {
  private static final String CONFIGURATION =
      """
      version = 1
      [quality-gates.format]
      command = "mvn spotless:check"
      [quality-gates.checkstyle]
      command = "mvn checkstyle:check"
      [quality-gates.checkstyle.partial]
      inputs = ["src/**/*.java"]
      config = ["config/checkstyle.xml", "pom.xml"]
      args = ["-Dcheckstyle.includes={paths}"]
      item = "**/{name}"
      separator = ","
      [quality-gates.pmd]
      command = "mvn pmd:check"
      [quality-gates.pmd.partial]
      inputs = ["src/**/*.java"]
      config = ["pom.xml"]
      [quality-gates.tests]
      command = "mvn test"
      profiles = ["pre-push"]
      [quality-gates.tests.partial]
      inputs = ["src/**/*.java"]
      [quality-gates.openspec]
      command = "openspec validate --all"
      """;
  private static final List<String> FORMAT = List.of("mvn", "spotless:check");
  private static final List<String> CHECKSTYLE = List.of("mvn", "checkstyle:check");
  private static final List<String> PMD = List.of("mvn", "pmd:check");
  private static final String ORDER = "src/main/java/com/acme/Order.java";

  @TempDir Path dir;

  private FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

  @BeforeEach
  void project() throws IOException {
    withPom(dir, PLAIN_POM);
    Files.writeString(dir.resolve("sentinel.toml"), CONFIGURATION);
    Files.createDirectories(dir.resolve(ORDER).getParent());
    Files.writeString(dir.resolve(ORDER), "class Order {}\n");
  }

  @Test
  void irrelevantEditSkipsPartialGatesWithoutSpawningThem() {
    final CheckReport report = partial("README.md");

    assertThat(executor.commands).containsExactly(FORMAT);
    assertThat(report.results())
        .extracting(GateResult::name, GateResult::status, GateResult::scope)
        .containsExactly(
            tuple("format", GateStatus.PASSED, GateScope.FULL),
            tuple("checkstyle", GateStatus.SKIPPED, GateScope.SKIPPED),
            tuple("pmd", GateStatus.SKIPPED, GateScope.SKIPPED));
    assertThat(report.results().get(1).summary()).isEqualTo(PartialGates.NOT_AFFECTED);
    assertThat(report.partialPaths()).containsExactly("README.md");
  }

  @Test
  void sourceEditNarrowsGatesThatDeclareArgumentsAndRunsTheRestInFull() {
    final CheckReport report = partial(ORDER);

    assertThat(executor.commands)
        .containsExactly(
            FORMAT, List.of("mvn", "checkstyle:check", "-Dcheckstyle.includes=**/Order.java"), PMD);
    assertThat(report.results())
        .extracting(GateResult::scope)
        .containsExactly(GateScope.FULL, GateScope.NARROWED, GateScope.FULL);
  }

  @Test
  void configurationEditRunsTheGateWithItsUnchangedCommand() {
    partial("pom.xml");

    assertThat(executor.commands).containsExactly(FORMAT, CHECKSTYLE, PMD);
  }

  @Test
  void deletedSourceRunsTheGateInFull() {
    partial("src/main/java/com/acme/Deleted.java");

    assertThat(executor.commands).containsExactly(FORMAT, CHECKSTYLE, PMD);
  }

  @Test
  void skippedGatesDoNotDecideTheVerdict() {
    executor = new FakeCommandExecutor(1, "", "");

    final CheckReport failing = partial("README.md");

    assertThat(failing.passed()).isFalse();
    executor = new FakeCommandExecutor(0, "", "");
    assertThat(partial("README.md").passed()).isTrue();
  }

  @Test
  void namedProfileSelectsItsOwnGates() {
    final CheckReport report =
        CheckServiceFixtures.checks(executor)
            .checkPartial(
                dir,
                List.of(dir.resolve(ORDER)),
                new CheckRunOptions(
                    List.of("pre-push"), CheckProgressListener.NO_OP, false, Set.of()));

    assertThat(report.results()).extracting(GateResult::name).containsExactly("tests");
    assertThat(report.results().getFirst().scope()).isEqualTo(GateScope.FULL);
  }

  @Test
  void progressAndFailFastApplyToPartialChecks() {
    executor = new FakeCommandExecutor(1, "", "");
    final List<String> finished = new ArrayList<>();

    final CheckReport report =
        CheckServiceFixtures.checks(executor)
            .checkPartial(
                dir,
                List.of(dir.resolve(ORDER)),
                new CheckRunOptions(
                    List.of(),
                    new CheckProgressListener() {
                      @Override
                      public void gateFinished(final GateResult result) {
                        finished.add(result.name());
                      }
                    },
                    true,
                    SupportedQualityGates.specIds()));

    assertThat(executor.commands).containsExactly(FORMAT);
    assertThat(finished).containsExactly("format", "checkstyle", "pmd");
    assertThat(report.results().get(1).status()).isEqualTo(GateStatus.SKIPPED);
  }

  @Test
  void excludingEveryRemainingGateReturnsAnEmptyReport() throws IOException {
    Files.writeString(
        dir.resolve("sentinel.toml"),
        """
        version = 1
        [quality-gates.openspec]
        command = "openspec validate --all"
        """);

    final CheckReport report =
        CheckServiceFixtures.checks(executor)
            .checkPartial(dir, List.of(dir.resolve(ORDER)), SupportedQualityGates.specIds());

    assertThat(report.results()).isEmpty();
    assertThat(executor.commands).isEmpty();
  }

  private CheckReport partial(final String path) {
    return CheckServiceFixtures.checks(executor)
        .checkPartial(dir, List.of(dir.resolve(path)), SupportedQualityGates.specIds());
  }
}
