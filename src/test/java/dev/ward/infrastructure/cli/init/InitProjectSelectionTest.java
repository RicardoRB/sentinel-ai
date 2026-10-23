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

/** One init run configures the projects named with --project, or the ones it discovers. */
class InitProjectSelectionTest {
  private static final String[] TESTS_ONLY = {"--integration", "none", "--gate", "tests"};
  private static final String POM =
      "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>"
          + "<artifactId>demo</artifactId><version>1.0.0</version></project>";
  private static final String API = "apps/api";
  private static final String BILLING = "services/billing";
  private static final String WARD_TOML = "ward.toml";
  private static final String PROJECT = "--project";

  @TempDir Path root;

  private InitCommandFixture init;

  @BeforeEach
  void monorepo() throws IOException {
    init = InitCommandFixture.repository(root);
    write("package.json", "{}");
    write("pnpm-lock.yaml", "");
    write(API + "/pom.xml", POM);
    write(BILLING + "/pom.xml", POM);
  }

  @Test
  void namedProjectsAreConfiguredAndNothingElse() {
    final int exit = init.run("", with(PROJECT, API, PROJECT, BILLING, "--yes"));

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(root.resolve(API).resolve(WARD_TOML)).exists();
    assertThat(root.resolve(BILLING).resolve(WARD_TOML)).exists();
    assertThat(root.resolve(WARD_TOML)).doesNotExist();
  }

  @Test
  void moduleNamedAsProjectFailsAndNamesTheAggregator() throws IOException {
    write(
        API + "/pom.xml",
        POM.replace("</project>", "<modules><module>core</module></modules></project>"));
    write(API + "/core/pom.xml", POM);
    final Map<String, String> before = ProjectSnapshot.of(root);

    final int exit = init.run("", with(PROJECT, API + "/core", "--yes"));

    assertThat(exit).isEqualTo(ExitCodes.ERROR);
    assertThat(init.output()).contains("use --project " + API);
    assertThat(ProjectSnapshot.of(root)).isEqualTo(before);
  }

  @Test
  void directoryWithoutJavaBuildFails() throws IOException {
    write("apps/web/package.json", "{}");
    final Map<String, String> before = ProjectSnapshot.of(root);

    final int exit = init.run("", with(PROJECT, "apps/web", "--yes"));

    assertThat(exit).isEqualTo(ExitCodes.ERROR);
    assertThat(init.output()).contains("'apps/web' is not inside a Maven or Gradle project");
    assertThat(ProjectSnapshot.of(root)).isEqualTo(before);
  }

  @Test
  void singleDiscoveredProjectIsConfigured() throws IOException {
    Files.delete(root.resolve(BILLING).resolve("pom.xml"));

    final int exit = init.run("", with("--yes"));

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output()).contains("Discovered project " + API + ".");
    assertThat(root.resolve(API).resolve(WARD_TOML)).exists();
  }

  @Test
  void severalCandidatesWithoutTerminalFailListingThem() throws IOException {
    final Map<String, String> before = ProjectSnapshot.of(root);

    final int exit = init.run("", with("--yes"));

    assertThat(exit).isEqualTo(ExitCodes.ERROR);
    assertThat(init.output()).contains(PROJECT + " " + API, PROJECT + " " + BILLING);
    assertThat(ProjectSnapshot.of(root)).isEqualTo(before);
  }

  @Test
  void severalCandidatesOnTerminalAreAskedAllPreselected() {
    final int exit = init.run(true, "2\n\nn\n\n", TESTS_ONLY);

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output())
        .contains("Select the projects to configure", "[x] 1) apps/api (Maven)")
        .contains("[x] 2) services/billing (Maven)");
    assertThat(root.resolve(API).resolve(WARD_TOML)).exists();
    assertThat(root.resolve(BILLING).resolve(WARD_TOML)).doesNotExist();
  }

  @Test
  void endOfInputAtProjectQuestionFails() {
    final int exit = init.run(true, "", TESTS_ONLY);

    assertThat(exit).isEqualTo(ExitCodes.ERROR);
    assertThat(init.output()).contains(InitWizard.PROJECT_INPUT_ENDED);
    assertThat(root.resolve(API).resolve(WARD_TOML)).doesNotExist();
  }

  private static String[] with(final String... extra) {
    final String[] args = new String[TESTS_ONLY.length + extra.length];
    System.arraycopy(TESTS_ONLY, 0, args, 0, TESTS_ONLY.length);
    System.arraycopy(extra, 0, args, TESTS_ONLY.length, extra.length);
    return args;
  }

  private void write(final String relative, final String content) throws IOException {
    final Path file = root.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
