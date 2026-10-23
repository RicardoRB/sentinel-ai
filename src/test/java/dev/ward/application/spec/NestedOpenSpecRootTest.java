package dev.ward.application.spec;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.FakeOpenSpecEnvironment;
import dev.ward.domain.spec.GuardDecision;
import dev.ward.domain.spec.GuardRequest;
import dev.ward.domain.spec.SpecValidationReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A nested project validates and protects the repository-level openspec/ it uses. */
class NestedOpenSpecRootTest {
  private static final String DESIGN = "openspec/changes/foo/design.md";
  private static final String SPEC = "openspec/specs/x/spec.md";
  private static final String CONFIGURATION =
      "version = 1\n[quality-gates.openspec]\ncommand = [\"openspec\", \"validate\", \"--all\"]\n";

  @TempDir Path repository;

  private final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");
  private Path api;

  @BeforeEach
  void nestedProjectWithRepositoryOpenSpec() throws IOException {
    Files.createDirectories(repository.resolve(".git"));
    Files.createDirectories(repository.resolve("openspec/changes/foo"));
    Files.createDirectories(repository.resolve("openspec/specs/x"));
    Files.writeString(repository.resolve(SPEC), "# x");
    api = Files.createDirectories(repository.resolve("apps/api"));
    withPom(api, PLAIN_POM);
  }

  @Test
  void editedChangeIsValidatedFromTheConfiguredRoot() throws IOException {
    configure("root = \"../..\"\n");

    final SpecValidationReport report =
        validation().validatePaths(api, List.of(), List.of(repository.resolve(DESIGN).toString()));

    assertThat(report.items()).hasSize(1);
    assertThat(executor.commands)
        .containsExactly(
            List.of(
                "openspec", "validate", "foo", "--type", "change", "--strict", "--no-interactive"));
    assertThat(executor.workingDirectories)
        .containsExactly(repository.toAbsolutePath().normalize());
  }

  @Test
  void withoutRootRepositoryPathsStayOutsideTheProject() throws IOException {
    configure("");

    final SpecValidationReport report =
        validation().validatePaths(api, List.of(), List.of(repository.resolve(DESIGN).toString()));

    assertThat(report.items()).isEmpty();
    assertThat(executor.commands).isEmpty();
  }

  @Test
  void rootOutsideTheRepositoryValidatesNothing() throws IOException {
    configure("root = \"../../..\"\n");

    assertThat(
            validation()
                .validatePaths(api, List.of(), List.of(repository.resolve(DESIGN).toString()))
                .items())
        .isEmpty();
    assertThat(executor.commands).isEmpty();
  }

  @Test
  void guardProtectsTheRepositoryLevelSpecs() {
    final GuardDecision decision =
        ApprovedArtifactGuardServiceTest.service()
            .evaluate(GuardRequest.paths(api, "", List.of(repository.resolve(SPEC).toString())));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).contains("Active changes: foo");
  }

  @Test
  void guardResolvesRelativePathsFromTheProject() {
    assertThat(
            ApprovedArtifactGuardServiceTest.service()
                .evaluate(GuardRequest.paths(api, "", List.of("../../" + SPEC)))
                .allowed())
        .isFalse();
    assertThat(
            ApprovedArtifactGuardServiceTest.service()
                .evaluate(GuardRequest.paths(api, "", List.of(SPEC)))
                .allowed())
        .isTrue();
  }

  private SpecValidationService validation() {
    final FakeOpenSpecEnvironment environment = new FakeOpenSpecEnvironment(true);
    return SpecValidationFixtures.service(
        executor, environment, SpecValidationFixtures.checks(executor, environment));
  }

  private void configure(final String root) throws IOException {
    Files.writeString(api.resolve("ward.toml"), CONFIGURATION + root);
  }
}
