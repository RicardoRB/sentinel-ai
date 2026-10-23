package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** In a real Git repository, nested projects install into the hooks Git runs and run scoped. */
@DisabledOnOs(OS.WINDOWS)
class NestedQualityHooksGitTest {
  private static final String POM = "<project><artifactId>api</artifactId></project>";
  private static final String USER_HOOK = "#!/bin/sh\necho user formatter\n";

  @TempDir Path repository;
  @TempDir Path bin;

  private final QualityHookInstaller installer = new QualityHookInstaller();
  private Project api;

  @BeforeEach
  void repositoryWithNestedProject() throws IOException, InterruptedException {
    git("init", "-q");
    final Path root = Files.createDirectories(repository.resolve("apps/api"));
    Files.writeString(root.resolve("pom.xml"), POM);
    api = new FileSystemProjectInspection().detect(root).orElseThrow();
  }

  @Test
  void nestedProjectExtendsTheRepositoryHooksAndCreatesNothingInsideIt() throws IOException {
    final Path preCommit = repository.resolve(".git/hooks/pre-commit");
    Files.writeString(preCommit, USER_HOOK);

    assertThat(installer.plan(api))
        .extracting(PlannedFileChange::file, PlannedFileChange::action)
        .containsExactly(
            tuple(preCommit, PlannedFileChange.Action.MODIFY),
            tuple(repository.resolve(".git/hooks/pre-push"), PlannedFileChange.Action.CREATE));
    installer.apply(api);

    assertThat(Files.readString(preCommit))
        .contains("echo user formatter", "# ward-owned: quality-hooks v1 begin apps/api");
    assertThat(repository.resolve("apps/api/.git")).doesNotExist();
  }

  @Test
  void configuredHooksPathReceivesTheSections() throws IOException, InterruptedException {
    git("config", "core.hooksPath", ".githooks");

    installer.apply(api);

    assertThat(repository.resolve(".githooks/pre-commit")).exists();
    assertThat(repository.resolve(".githooks/pre-push")).exists();
    assertThat(repository.resolve(".git/hooks/pre-commit")).doesNotExist();
  }

  @Test
  void preCommitSkipsCommitsThatDoNotTouchTheProject() throws IOException, InterruptedException {
    final Path calls = stubWard();
    installer.apply(api);
    Files.createDirectories(repository.resolve("apps/web"));
    Files.writeString(repository.resolve("apps/web/page.tsx"), "export {}\n");
    git("add", "apps/web/page.tsx");

    assertThat(runHook("pre-commit")).isZero();
    assertThat(calls).doesNotExist();
  }

  @Test
  void preCommitRunsFromTheProjectWhenItIsStaged() throws IOException, InterruptedException {
    final Path calls = stubWard();
    installer.apply(api);
    git("add", "apps/api/pom.xml");

    assertThat(runHook("pre-commit")).isZero();
    assertThat(Files.readString(calls).strip())
        .isEqualTo(
            real(repository.resolve("apps/api")) + " check --profile pre-commit --format json");
  }

  /** A stub {@code ward} on the PATH that records its working directory and arguments. */
  private Path stubWard() throws IOException {
    final Path calls = bin.resolve("calls");
    final Path ward = bin.resolve("ward");
    Files.writeString(ward, "#!/bin/sh\necho \"$(pwd -P) $*\" >> '" + calls + "'\n");
    Files.setPosixFilePermissions(ward, PosixFilePermissions.fromString("rwxr-xr-x"));
    return calls;
  }

  private int runHook(final String name) throws IOException, InterruptedException {
    final ProcessBuilder builder =
        new ProcessBuilder(real(repository.resolve(".git/hooks").resolve(name)).toString())
            .directory(repository.toFile())
            .redirectErrorStream(true);
    final Map<String, String> environment = builder.environment();
    environment.put("PATH", bin + ":" + environment.getOrDefault("PATH", "/usr/bin:/bin"));
    final Process process = builder.start();
    process.getInputStream().readAllBytes();
    return process.waitFor();
  }

  private void git(final String... args) throws IOException, InterruptedException {
    final List<String> command = new ArrayList<>(List.of("git"));
    command.addAll(List.of(args));
    final Process process =
        new ProcessBuilder(command)
            .directory(repository.toFile())
            .redirectErrorStream(true)
            .start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.waitFor()).as(output).isZero();
  }

  private static Path real(final Path path) throws IOException {
    final Path parent = path.getParent().toRealPath();
    return parent.resolve(path.getFileName());
  }
}
