package dev.sentinel.application.gate;

import static dev.sentinel.ProjectFixtures.PLAIN_POM;
import static dev.sentinel.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.FakeCommandExecutor;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.SupportedQualityGates;
import dev.sentinel.infrastructure.config.TomlConfigurationReader;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Gate subsets and exclusions used by spec validation and agent hooks. */
class CheckServiceGateSelectionTest {
  private static final String OPENSPEC = "openspec";
  private static final String ARCHIVED = "openspec-archived";
  private static final List<String> ACTIVE_COMMAND = List.of(OPENSPEC, "validate", "--all");
  private static final List<String> ARCHIVED_COMMAND = List.of(OPENSPEC, "validate", "--archived");
  private static final List<String> TESTS_COMMAND = List.of("mvn", "test");

  @TempDir Path dir;

  private final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

  @Test
  void gateSubsetRunsOnlyListedGatesInConfigurationOrder() throws IOException {
    configure(
        """
        version = 1
        [quality-gates.tests]
        command = "mvn test"
        [quality-gates.openspec]
        command = "openspec validate --all"
        [quality-gates.openspec-archived]
        command = "openspec validate --archived"
        """);

    final CheckReport report = service().check(dir, List.of(), Set.of(ARCHIVED, OPENSPEC));

    assertThat(report.results()).extracting("name").containsExactly(OPENSPEC, ARCHIVED);
    assertThat(executor.commands).containsExactly(ACTIVE_COMMAND, ARCHIVED_COMMAND);
  }

  @Test
  void gateSubsetWithNothingEnabledIsAnEmptyPassingReport() throws IOException {
    configure(
        """
        version = 1
        [quality-gates.tests]
        command = "mvn test"
        [quality-gates.openspec]
        enabled = false
        command = "openspec validate --all"
        """);

    final CheckReport report = service().check(dir, List.of(), Set.of(OPENSPEC));

    assertThat(report.results()).isEmpty();
    assertThat(report.passed()).isTrue();
    assertThat(executor.commands).isEmpty();
  }

  @Test
  void gateSubsetHonorsProfileSelection() throws IOException {
    configure(
        """
        version = 1
        [quality-gates.openspec]
        command = "openspec validate --all"
        [quality-gates.openspec-archived]
        command = "openspec validate --archived"
        profiles = ["ci"]
        """);

    service().check(dir, List.of("ci"), Set.of(OPENSPEC, ARCHIVED));

    assertThat(executor.commands).containsExactly(ARCHIVED_COMMAND);
    assertThatThrownBy(() -> service().check(dir, List.of("nope"), Set.of(OPENSPEC)))
        .isInstanceOf(SentinelException.class)
        .hasMessageContaining("Unknown profiles");
  }

  @Test
  void excludedSpecGatesDoNotRun() throws IOException {
    configure(
        """
        version = 1
        [quality-gates.tests]
        command = "mvn test"
        [quality-gates.openspec]
        command = "openspec validate --all"
        """);

    final CheckReport report =
        service()
            .check(
                dir,
                List.of(),
                CheckProgressListener.NO_OP,
                false,
                SupportedQualityGates.specIds());

    assertThat(report.results()).extracting("name").containsExactly("tests");
    assertThat(executor.commands).containsExactly(TESTS_COMMAND);
  }

  @Test
  void excludingEveryEnabledGateIsAnEmptyPass() throws IOException {
    configure("version = 1\n[quality-gates.openspec]\ncommand = \"openspec validate --all\"\n");

    final CheckReport report =
        service()
            .check(
                dir,
                List.of(),
                CheckProgressListener.NO_OP,
                false,
                SupportedQualityGates.specIds());

    assertThat(report.results()).isEmpty();
    assertThat(report.passed()).isTrue();
    assertThat(executor.commands).isEmpty();
  }

  @Test
  void excludingNothingKeepsTheNoEnabledGatesError() throws IOException {
    configure("version = 1\n[quality-gates.openspec]\nenabled = false\ncommand = \"x\"\n");

    assertThatThrownBy(
            () ->
                service()
                    .check(
                        dir,
                        List.of(),
                        CheckProgressListener.NO_OP,
                        false,
                        SupportedQualityGates.specIds()))
        .isInstanceOf(SentinelException.class)
        .hasMessageContaining("No quality gates are enabled");
  }

  private void configure(final String toml) throws IOException {
    withPom(dir, PLAIN_POM);
    Files.writeString(dir.resolve("sentinel.toml"), toml);
  }

  private CheckService service() {
    return new CheckService(
        new ProjectDetector(new FileSystemProjectInspection()),
        new TomlConfigurationReader(),
        new QualityGateFactory(executor),
        new QualityGateRunner());
  }
}
