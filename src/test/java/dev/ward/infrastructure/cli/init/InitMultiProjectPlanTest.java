package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.ProjectSnapshot;
import dev.ward.infrastructure.cli.ExitCodes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** One run plans several projects as one grouped plan and applies what it showed. */
class InitMultiProjectPlanTest {
  private static final String POM =
      "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>"
          + "<artifactId>demo</artifactId><version>1.0.0</version></project>";
  private static final String API = "apps/api";
  private static final String BILLING = "services/billing";
  private static final String PRE_COMMIT = ".git/hooks/pre-commit";
  private static final String[] BOTH = {
    "--project",
    API,
    "--project",
    BILLING,
    "--integration",
    "none",
    "--gate",
    "tests",
    "--quality-hooks",
    "--ci",
    "github",
    "--yes"
  };

  @TempDir Path root;

  private InitCommandFixture init;

  @BeforeEach
  void monorepo() throws IOException {
    init = InitCommandFixture.repository(root);
    write(API + "/pom.xml", POM);
    write(BILLING + "/pom.xml", POM);
  }

  @Test
  void dryRunGroupsFilesByProjectAndListsSharedFilesOnce() throws IOException {
    final Map<String, String> before = ProjectSnapshot.of(root);

    final int exit = init.run("", with("--dry-run"));

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output())
        .contains("Project apps/api:", "Project services/billing:", "Repository root:")
        .contains("create     .github/workflows/ward-apps-api.yml")
        .contains("create     .github/workflows/ward-services-billing.yml");
    assertThat(init.output().split("create     " + PRE_COMMIT + "\n", -1)).hasSize(2);
    assertThat(ProjectSnapshot.of(root)).isEqualTo(before);
  }

  @Test
  void applyingWritesWhatThePlanShowed() throws IOException {
    init.run("", with("--dry-run"));

    assertThat(init.run("", BOTH)).as(init.output()).isEqualTo(ExitCodes.OK);

    assertThat(Files.readString(root.resolve(PRE_COMMIT)))
        .contains(
            "# ward-owned: quality-hooks v1 begin apps/api",
            "# ward-owned: quality-hooks v1 begin services/billing");
    assertThat(root.resolve(".github/workflows/ward-apps-api.yml")).exists();
    assertThat(root.resolve(".github/workflows/ward-services-billing.yml")).exists();
    assertThat(root.resolve(API).resolve("ward.toml")).exists();
    assertThat(root.resolve(BILLING).resolve("ward.toml")).exists();
  }

  @Test
  void singleNestedProjectPlanShowsWhereRepositoryFilesLand() {
    final int exit =
        init.run(
            "",
            "--project",
            API,
            "--integration",
            "none",
            "--gate",
            "tests",
            "--quality-hooks",
            "--ci",
            "github",
            "--yes",
            "--dry-run");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output())
        .contains(
            "create     ../../" + PRE_COMMIT,
            "create     ../../.github/workflows/ward-apps-api.yml")
        .contains("create     ward.toml");
  }

  @Test
  void failureNamesTheProjectsAlreadyConfigured() throws IOException {
    write(BILLING + "/config", "a file where the rule directory belongs");

    final int exit =
        init.run(
            "",
            "--project",
            API,
            "--project",
            BILLING,
            "--integration",
            "none",
            "--preset",
            "standard",
            "--architecture",
            "layered",
            "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.ERROR);
    assertThat(init.output())
        .contains("Could not initialize services/billing", "Already configured: apps/api");
    assertThat(root.resolve(API).resolve("ward.toml")).exists();
    assertThat(root.resolve(BILLING).resolve("ward.toml")).doesNotExist();
  }

  private static String[] with(final String extra) {
    final String[] args = new String[BOTH.length + 1];
    System.arraycopy(BOTH, 0, args, 0, BOTH.length);
    args[BOTH.length] = extra;
    return args;
  }

  private void write(final String relative, final String content) throws IOException {
    final Path file = root.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
