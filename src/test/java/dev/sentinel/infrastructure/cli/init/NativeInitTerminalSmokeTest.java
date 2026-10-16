package dev.sentinel.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.infrastructure.cli.ExitCodes;
import java.io.File;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
 * ./mvnw -Dtest=NativeInitTerminalSmokeTest -Dsentinel.native.binary=target/sentinel test} after
 * {@code native:compile}.
 */
@EnabledOnOs({OS.LINUX, OS.MAC})
@EnabledIfSystemProperty(named = "sentinel.native.binary", matches = ".+")
class NativeInitTerminalSmokeTest {
  private static final Duration PROMPT_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration KEY_TIMEOUT = Duration.ofSeconds(10);
  private static final byte[] ARROW_DOWN = {0x1b, '[', 'B'};

  @TempDir Path workspace;

  @Test
  void rawKeyIntegrationMenuTogglesAndCancelsWithoutDiagnostics() throws Exception {
    final Path binary = Path.of(System.getProperty("sentinel.native.binary")).toAbsolutePath();
    assertThat(binary).as("sentinel.native.binary; run native:compile first").isExecutable();
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
        assertThat(project.resolve("sentinel.toml")).doesNotExist();
      } finally {
        process.destroyForcibly();
      }
    }
  }

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
