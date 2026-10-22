package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.cli.ExitCodes;
import java.io.File;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives a built executable's {@code init} wizard through a pseudo-terminal.
 *
 * <p>Regular tests cannot reach the raw-key path because their stdin is not a TTY, and native-only
 * failures (for example, classes defined at run time) never show up on the JVM. Run it with {@code
 * ./mvnw -Dtest=NativeInitTerminalSmokeTest -Dward.native.binary=target/ward test} after {@code
 * native:compile}.
 */
@EnabledOnOs({OS.LINUX, OS.MAC})
@EnabledIfSystemProperty(named = "ward.native.binary", matches = ".+")
class NativeInitTerminalSmokeTest {
  private static final Duration PROMPT_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration KEY_TIMEOUT = Duration.ofSeconds(10);
  private static final byte[] ARROW_DOWN = {0x1b, '[', 'B'};
  private static final byte[] ENTER = {'\r'};
  private static final int PMD_ROW = 6;
  private static final long POLL_MILLIS = 50;
  private static final String COMPATIBILITY_PROMPT = "Apply this Maven plugin update\\?";

  @TempDir Path workspace;

  @Test
  void rawKeyIntegrationMenuTogglesAndCancelsWithoutDiagnostics() throws Exception {
    final Path binary = Path.of(System.getProperty("ward.native.binary")).toAbsolutePath();
    assertThat(binary).as("ward.native.binary; run native:compile first").isExecutable();
    final Path project = copyFixture(workspace.resolve("project"));

    try (Arena arena = Arena.ofConfined();
        PseudoTerminal terminal = PseudoTerminal.open(arena)) {
      final File device = new File(terminal.devicePath());
      final Process process =
          new ProcessBuilder(binary.toString(), "init")
              .directory(project.toFile())
              .redirectInput(ProcessBuilder.Redirect.from(device))
              .redirectOutput(ProcessBuilder.Redirect.appendTo(device))
              .redirectError(ProcessBuilder.Redirect.appendTo(device))
              .start();
      try {
        terminal.awaitOutput(process, "Choose a preset", PROMPT_TIMEOUT);
        terminal.write("3\n".getBytes(StandardCharsets.US_ASCII));
        terminal.awaitOutput(process, "Select agent integrations", PROMPT_TIMEOUT);
        terminal.write(ARROW_DOWN);
        terminal.write(" ".getBytes(StandardCharsets.US_ASCII));
        terminal.awaitOutput(process, "[x]", KEY_TIMEOUT);
        terminal.write("q".getBytes(StandardCharsets.US_ASCII));
        terminal.awaitOutput(process, "Initialization cancelled by the user.", KEY_TIMEOUT);

        assertThat(process.waitFor(KEY_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(terminal.output()).isEqualTo(ExitCodes.ERROR);
        assertThat(terminal.output())
            .doesNotContain("WARNING:")
            .doesNotContain("unexpected error")
            .doesNotContain("Exception");
        assertThat(project.resolve("ward.toml")).doesNotExist();
      } finally {
        process.destroyForcibly();
      }
    }
  }

  @Test
  void rawKeyWizardCompletesEveryScreenAndHonorsUntickedPresetGate() throws Exception {
    final Path binary = Path.of(System.getProperty("ward.native.binary")).toAbsolutePath();
    final Path project = copyFixture(workspace.resolve("complete"));
    Files.createDirectories(project.resolve(".git/hooks"));

    try (Arena arena = Arena.ofConfined();
        PseudoTerminal terminal = PseudoTerminal.open(arena)) {
      final File device = new File(terminal.devicePath());
      final Process process =
          new ProcessBuilder(binary.toString(), "init")
              .directory(project.toFile())
              .redirectInput(ProcessBuilder.Redirect.from(device))
              .redirectOutput(ProcessBuilder.Redirect.appendTo(device))
              .redirectError(ProcessBuilder.Redirect.appendTo(device))
              .start();
      try {
        answerEveryScreenUntickingPmd(terminal, process);
        assertThat(terminal.output())
            .contains(
                "create     ward.toml",
                "create     .git/hooks/pre-commit",
                "Step 1 of 5",
                "Step 5 of 5",
                "Enter=1]")
            .doesNotContain("\033[2J");
        terminal.write("\n".getBytes(StandardCharsets.US_ASCII));
        declineCompatibilityUpdatesUntilExit(terminal, process);

        assertThat(process.exitValue()).as(terminal.output()).isZero();
        assertThat(terminal.output())
            .doesNotContain("WARNING:")
            .doesNotContain("unexpected error")
            .doesNotContain("Exception");
        assertThat(Files.readString(project.resolve("ward.toml")))
            .contains("preset = \"standard\"", "[quality-gates.checkstyle]")
            .doesNotContain("[quality-gates.pmd]");
        assertThat(project.resolve("config/pmd-ruleset.xml")).doesNotExist();
        assertThat(Files.readString(project.resolve(".git/hooks/pre-commit")))
            .contains("ward-owned: quality-hooks");
      } finally {
        process.destroyForcibly();
      }
    }
  }

  private static void answerEveryScreenUntickingPmd(
      final PseudoTerminal terminal, final Process process) throws InterruptedException {
    terminal.awaitOutput(process, "Choose a preset", PROMPT_TIMEOUT);
    terminal.write("1\n".getBytes(StandardCharsets.US_ASCII));
    terminal.awaitOutput(process, "Select agent integrations", PROMPT_TIMEOUT);
    terminal.write(" ".getBytes(StandardCharsets.US_ASCII));
    terminal.awaitOutput(process, "> [x] 1) No agent integration", KEY_TIMEOUT);
    terminal.write(ENTER);
    terminal.awaitOutput(process, "Select quality gates", PROMPT_TIMEOUT);
    for (int row = 1; row < PMD_ROW; row++) {
      terminal.write(ARROW_DOWN);
    }
    terminal.awaitOutput(process, "> [x] 6) pmd", KEY_TIMEOUT);
    terminal.write(" ".getBytes(StandardCharsets.US_ASCII));
    terminal.awaitOutput(process, "> [ ] 6) pmd", KEY_TIMEOUT);
    terminal.write(ENTER);
    terminal.awaitOutput(process, "Select optional tools", PROMPT_TIMEOUT);
    terminal.write(ENTER);
    terminal.awaitOutput(process, "quality hooks? [y/N]", PROMPT_TIMEOUT);
    terminal.write("y\n".getBytes(StandardCharsets.US_ASCII));
    terminal.awaitOutput(process, "Proceed? [Y/n]", PROMPT_TIMEOUT);
  }

  @Test
  void reviewListsThePlanAndDecliningChangesNothing() throws Exception {
    final Path binary = Path.of(System.getProperty("ward.native.binary")).toAbsolutePath();
    final Path project = copyFixture(workspace.resolve("declined"));
    final String pom = Files.readString(project.resolve("pom.xml"));

    try (Arena arena = Arena.ofConfined();
        PseudoTerminal terminal = PseudoTerminal.open(arena)) {
      final File device = new File(terminal.devicePath());
      final Process process =
          new ProcessBuilder(binary.toString(), "init", "--integration", "none", "--gate", "tests")
              .directory(project.toFile())
              .redirectInput(ProcessBuilder.Redirect.from(device))
              .redirectOutput(ProcessBuilder.Redirect.appendTo(device))
              .redirectError(ProcessBuilder.Redirect.appendTo(device))
              .start();
      try {
        terminal.awaitOutput(process, "Select optional tools", PROMPT_TIMEOUT);
        terminal.write(ENTER);
        terminal.awaitOutput(process, "quality hooks? [y/N]", PROMPT_TIMEOUT);
        terminal.write("n\n".getBytes(StandardCharsets.US_ASCII));
        terminal.awaitOutput(process, "Proceed? [Y/n]", PROMPT_TIMEOUT);
        assertThat(terminal.output()).contains("create     ward.toml");
        terminal.write("n\n".getBytes(StandardCharsets.US_ASCII));
        terminal.awaitOutput(process, "No changes made.", KEY_TIMEOUT);

        assertThat(process.waitFor(KEY_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(terminal.output()).isZero();
        assertThat(project.resolve("ward.toml")).doesNotExist();
        assertThat(Files.readString(project.resolve("pom.xml"))).isEqualTo(pom);
      } finally {
        process.destroyForcibly();
      }
    }
  }

  @Test
  void dryRunPrintsThePlanAndLeavesTheProjectUnchanged() throws Exception {
    final Path project = copyFixture(workspace.resolve("dry-run"));
    final String pom = Files.readString(project.resolve("pom.xml"));

    final Result result =
        run(
            project,
            "init",
            "--dry-run",
            "--preset",
            "strict",
            "--integration",
            "none",
            "--architecture",
            "layered");

    assertThat(result.exitCode()).as(result.output()).isZero();
    assertThat(result.output())
        .contains("Ward will make these changes:", "modify     pom.xml")
        .doesNotContain("Exception");
    assertThat(project.resolve("ward.toml")).doesNotExist();
    assertThat(project.resolve("config")).doesNotExist();
    assertThat(Files.readString(project.resolve("pom.xml"))).isEqualTo(pom);
  }

  /** Answers "n" to every Maven compatibility prompt until the executable exits. */
  private static void declineCompatibilityUpdatesUntilExit(
      final PseudoTerminal terminal, final Process process) throws InterruptedException {
    final long deadline = System.nanoTime() + PROMPT_TIMEOUT.toNanos();
    int answered = 0;
    while (process.isAlive()) {
      assertThat(System.nanoTime())
          .as("waiting for init to finish; terminal output so far:%n%s", terminal.output())
          .isLessThan(deadline);
      final int prompts = terminal.output().split(COMPATIBILITY_PROMPT, -1).length - 1;
      for (; answered < prompts; answered++) {
        terminal.write("n\n".getBytes(StandardCharsets.US_ASCII));
      }
      Thread.sleep(POLL_MILLIS);
    }
  }

  @Test
  void strictPresetInitWritesMavenToolsThatPassPomValidation() throws Exception {
    final Path project = copyFixture(workspace.resolve("strict"));

    final Result result = run(project, "init", "--integration", "none", "--preset", "strict");

    assertThat(result.exitCode()).as(result.output()).isZero();
    assertThat(result.output()).doesNotContain("Exception").doesNotContain("unexpected error");
    assertThat(Files.readString(project.resolve("pom.xml")))
        .contains("<artifactId>pitest-maven</artifactId>")
        .containsPattern("<configuration>\\s*<mutationThreshold>");
  }

  @Test
  void initReportsMalformedPomWithoutWritingOrCrashing() throws Exception {
    final Path project = copyFixture(workspace.resolve("invalid"));
    final Path pom = project.resolve("pom.xml");
    Files.writeString(
        pom, Files.readString(pom).replace("<version>1.0.0</version>", "<version>&bad;</version>"));
    final String original = Files.readString(pom);

    final Result result = run(project, "init", "--integration", "none", "--gate", "pmd");

    assertThat(result.exitCode()).as(result.output()).isEqualTo(ExitCodes.ERROR);
    // The XML parser's error messages are a resource bundle that must be in the native image.
    assertThat(result.output())
        .contains("The entity \"bad\" was referenced, but not declared.")
        .doesNotContain("unexpected error")
        .doesNotContain("MissingResourceException");
    assertThat(Files.readString(pom)).isEqualTo(original);
    assertThat(project.resolve("ward.toml")).doesNotExist();
  }

  private static Result run(final Path project, final String... arguments)
      throws IOException, InterruptedException {
    final Path binary = Path.of(System.getProperty("ward.native.binary")).toAbsolutePath();
    final List<String> command = new ArrayList<>();
    command.add(binary.toString());
    command.addAll(List.of(arguments));
    final Process process =
        new ProcessBuilder(command)
            .directory(project.toFile())
            .redirectErrorStream(true)
            .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
            .start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.waitFor(PROMPT_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
    return new Result(process.exitValue(), output);
  }

  private record Result(int exitCode, String output) {}

  private static Path copyFixture(final Path target) throws IOException {
    final Path fixture = Path.of("src/test/resources/fixtures/maven-project");
    try (Stream<Path> files = Files.walk(fixture)) {
      for (final Path source : files.toList()) {
        final Path destination = target.resolve(fixture.relativize(source).toString());
        if (Files.isDirectory(source)) {
          Files.createDirectories(destination);
        } else {
          Files.copy(source, destination);
          destination.toFile().setExecutable(source.toFile().canExecute());
        }
      }
    }
    return target;
  }
}
