package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.ProjectSnapshot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** The full CLI initializes two projects of a real monorepo from its root. */
@DisabledOnOs(OS.WINDOWS)
class WardCliMonorepoInitTest extends WardCliIntegrationSupport {
  private static final String API = "apps/api";
  private static final String BILLING = "services/billing";
  private static final String USER_HOOK = "#!/bin/sh\npnpm format\n";
  private static final String USER_SETTINGS =
      "{\"hooks\": {\"PostToolUse\": [{\"matcher\": \"Edit\", \"hooks\":"
          + " [{\"type\": \"command\", \"command\": \"./scripts/verify-edit.sh\"}]}]}}\n";

  @TempDir Path repository;

  @BeforeEach
  void monorepo() throws IOException, InterruptedException {
    git("init", "-q");
    write("package.json", "{\"name\": \"monorepo\"}");
    write("pnpm-lock.yaml", "");
    write("apps/web/package.json", "{}");
    write("node_modules/left-pad/package.json", "{}");
    write("node_modules/left-pad/pom.xml", "<project/>");
    write(".git/hooks/pre-commit", USER_HOOK);
    write(".claude/settings.json", USER_SETTINGS);
    Files.createDirectories(repository.resolve("openspec"));
    final Path api = repository.resolve(API);
    copyFixture(api);
    final Path pom = api.resolve("pom.xml");
    Files.writeString(
        pom,
        Files.readString(pom)
            .replace(
                "</project>",
                "    <modules><module>core</module><module>web</module></modules>\n</project>"));
    write(API + "/core/pom.xml", "<project><artifactId>core</artifactId></project>");
    write(API + "/web/pom.xml", "<project><artifactId>web</artifactId></project>");
    write(BILLING + "/settings.gradle.kts", "include(\":domain\")\n");
    write(BILLING + "/build.gradle.kts", "plugins { java }\n");
    write(BILLING + "/domain/build.gradle.kts", "plugins { java }\n");
  }

  @Test
  void dryRunThenInitWriteRepositoryFilesAtTheRootAndKeepUserContent() throws IOException {
    final Map<String, String> before = ProjectSnapshot.of(repository);

    assertThat(run(arguments(true))).as(out + "\n" + err).isEqualTo(ExitCodes.OK);
    assertThat(out.toString())
        .contains("Project apps/api:", "Project services/billing:", "Repository root:")
        .contains("modify     .git/hooks/pre-commit", "modify     .claude/settings.json");
    assertThat(ProjectSnapshot.of(repository)).isEqualTo(before);

    assertThat(run(arguments(false))).as(out + "\n" + err).isEqualTo(ExitCodes.OK);

    assertThat(read(".git/hooks/pre-commit"))
        .startsWith("#!/bin/sh\n")
        .contains(
            "pnpm format",
            "# ward-owned: quality-hooks v1 begin apps/api",
            "# ward-owned: quality-hooks v1 begin services/billing");
    assertThat(read(".claude/settings.json"))
        .contains(
            "./scripts/verify-edit.sh",
            "ward-edit-write-apps-api",
            "ward-edit-write-services-billing");
    assertThat(repository.resolve(".github/workflows/ward-apps-api.yml")).exists();
    assertThat(repository.resolve(".github/workflows/ward-services-billing.yml")).exists();
    assertThat(repository.resolve(".opencode/plugins/ward-edit-write-apps-api.js")).exists();
    assertThat(read(API + "/ward.toml")).contains("root = \"../..\"");
    assertThat(repository.resolve(API + "/core/ward.toml")).doesNotExist();
    assertThat(repository.resolve(BILLING + "/domain/ward.toml")).doesNotExist();
    for (final String inside : List.of(API, BILLING)) {
      assertThat(repository.resolve(inside).resolve(".git")).doesNotExist();
      assertThat(repository.resolve(inside).resolve(".github")).doesNotExist();
      assertThat(repository.resolve(inside).resolve(".claude")).doesNotExist();
    }
  }

  private String[] arguments(final boolean dryRun) {
    final List<String> args =
        new ArrayList<>(
            List.of(
                INIT,
                "-C",
                repository.toString(),
                "--project",
                API,
                "--project",
                BILLING,
                GATE_OPTION,
                TESTS,
                INTEGRATION_OPTION,
                "claude-code,opencode",
                "--tool",
                "openspec",
                "--openspec-validation",
                "openspec",
                "--quality-hooks",
                "--ci",
                "github",
                "--yes"));
    if (dryRun) {
      args.add("--dry-run");
    }
    out.getBuffer().setLength(0);
    err.getBuffer().setLength(0);
    return args.toArray(String[]::new);
  }

  private void copyFixture(final Path target) throws IOException {
    for (final String file : List.of("pom.xml", "mvnw")) {
      Files.createDirectories(target);
      Files.copy(project.resolve(file), target.resolve(file));
    }
  }

  private String read(final String relative) throws IOException {
    return Files.readString(repository.resolve(relative));
  }

  private void write(final String relative, final String content) throws IOException {
    final Path file = repository.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
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
}
