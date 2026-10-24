package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.FakeOpenSpecEnvironment;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** OpenSpec gates with a configured root run there, and never search further parents. */
class OpenSpecRootGateTest {
  private static final List<String> COMMAND = List.of("openspec", "validate", "--all");
  private static final String OPENSPEC = "openspec";
  private static final String UP = "../..";

  @TempDir Path repository;

  private Project api;
  private final FakeCommandExecutor executor = new FakeCommandExecutor(0, "ok", "");

  @BeforeEach
  void nestedProject() throws IOException {
    final Path root = Files.createDirectories(repository.resolve("apps/api"));
    api =
        new Project(
            root,
            Language.JAVA,
            BuildTool.MAVEN,
            Framework.NONE,
            false,
            false,
            repository,
            Set.of());
  }

  @Test
  void configuredRootRunsFromTheRepositoryRoot() throws IOException {
    Files.createDirectories(repository.resolve(OPENSPEC));

    final GateResult result = gate(UP).execute(api);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(executor.workingDirectories)
        .containsExactly(repository.toAbsolutePath().normalize());
  }

  @Test
  void missingDirectoryAtTheConfiguredRootNamesItAndIgnoresFurtherParents() throws IOException {
    Files.createDirectories(repository.resolve(OPENSPEC));

    final GateResult result = gate("..").execute(api);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).contains("root '..'");
    assertThat(executor.commands).isEmpty();
  }

  @Test
  void rootOutsideTheRepositoryIsRejectedWithoutRunning() {
    final GateResult result = gate("../../..").execute(api);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).contains("outside the repository");
    assertThat(executor.commands).isEmpty();
  }

  @Test
  void withoutRootTheProjectRootIsStillRequired() throws IOException {
    Files.createDirectories(repository.resolve(OPENSPEC));

    final GateResult result = gate(null).execute(api);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).contains("Project-root openspec/");
  }

  private OpenSpecQualityGate gate(final String root) {
    return new OpenSpecQualityGate(
        OPENSPEC, executor, new FakeOpenSpecEnvironment(true), COMMAND, root);
  }
}
