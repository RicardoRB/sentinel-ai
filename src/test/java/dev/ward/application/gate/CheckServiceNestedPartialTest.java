package dev.ward.application.gate;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A nested project ignores edits that land elsewhere in the repository it shares. */
class CheckServiceNestedPartialTest {
  private static final String CONFIGURATION =
      "version = 1\n[quality-gates.format]\ncommand = \"mvn spotless:check\"\n";
  private static final String WEB_PAGE = "apps/web/page.tsx";

  @TempDir Path repository;

  private final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");
  private Path api;

  @BeforeEach
  void nestedProject() throws IOException {
    Files.createDirectories(repository.resolve(".git"));
    api = Files.createDirectories(repository.resolve("apps/api"));
    withPom(api, PLAIN_POM);
    Files.writeString(api.resolve("ward.toml"), CONFIGURATION);
  }

  @Test
  void editsOutsideTheProjectRunNoGate() {
    final CheckReport report = partial(api, repository.resolve(WEB_PAGE));

    assertThat(executor.commands).isEmpty();
    assertThat(report.results()).extracting(GateResult::status).containsOnly(GateStatus.SKIPPED);
    assertThat(report.passed()).isTrue();
  }

  @Test
  void editsInsideTheProjectStillRunTheGates() {
    partial(api, repository.resolve(WEB_PAGE), api.resolve("pom.xml"));

    assertThat(executor.commands).containsExactly(List.of("mvn", "spotless:check"));
  }

  @Test
  void rootProjectsKeepRunningForOutsideEdits() throws IOException {
    withPom(repository, PLAIN_POM);
    Files.writeString(repository.resolve("ward.toml"), CONFIGURATION);

    partial(repository, Path.of("/elsewhere/page.tsx"));

    assertThat(executor.commands).containsExactly(List.of("mvn", "spotless:check"));
  }

  private CheckReport partial(final Path start, final Path... paths) {
    return CheckServiceFixtures.checks(executor).checkPartial(start, List.of(paths), Set.of());
  }
}
