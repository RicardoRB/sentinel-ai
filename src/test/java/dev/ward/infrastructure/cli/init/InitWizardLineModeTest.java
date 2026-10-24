package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JavaPresetGates;
import dev.ward.application.init.InitProjectFiles;
import dev.ward.application.init.InitService;
import dev.ward.application.init.InitSetupCatalog;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.infrastructure.init.ArchitectureTestGenerator;
import dev.ward.infrastructure.init.FileConfigurationStorage;
import dev.ward.infrastructure.init.PomToolConfigurator;
import dev.ward.infrastructure.init.PresetRuleFileWriter;
import dev.ward.infrastructure.init.QualityHookInstaller;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class InitWizardLineModeTest {
  // format, checkstyle, and pmd each add a [quality-gates.<id>.partial] table.
  private static final int STANDARD_PARTIAL_TABLES = 3;
  @TempDir Path root;

  private final StringWriter output = new StringWriter();

  @BeforeEach
  void createProject() throws IOException {
    Files.writeString(
        root.resolve("pom.xml"),
        "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>"
            + "<artifactId>demo</artifactId><version>1.0.0</version></project>");
    Files.createDirectories(root.resolve(".git/hooks"));
  }

  @Test
  void terminalWizardAsksOptionalQuestionsBeforeWriting() throws IOException {
    final int exit =
        execute(new TerminalLikeInputStream("3\n1\n1\n\ny\n"), "--directory", root.toString());

    assertThat(exit).as(output.toString()).isZero();
    assertThat(output.toString())
        .contains("Select optional tools", "Install Ward pre-commit and pre-push quality hooks");
    assertThat(Files.readString(root.resolve(".git/hooks/pre-commit")))
        .contains(QualityHookInstaller.START_MARKER);
  }

  @Test
  void endOfInputAfterGatesSelectsOptionalDefaults() throws IOException {
    final List<String> events = new ArrayList<>();
    final Logger logger = Logger.getLogger(LinePrompt.class.getName());
    final Handler handler =
        new Handler() {
          @Override
          public void publish(final LogRecord logRecord) {
            events.add(logRecord.getMessage());
          }

          @Override
          public void flush() {
            // Nothing is buffered.
          }

          @Override
          public void close() {
            // Nothing to release.
          }
        };
    logger.addHandler(handler);
    final int exit;
    try {
      exit = execute(new TerminalLikeInputStream("3\n1\n1\n"), "--directory", root.toString());
    } finally {
      logger.removeHandler(handler);
    }

    assertThat(exit).as(output.toString()).isZero();
    final String configuration = Files.readString(root.resolve("ward.toml"));
    assertThat(configuration).contains("[quality-gates.tests]").doesNotContain("openspec");
    assertThat(root.resolve(".git/hooks/pre-commit")).doesNotExist();
    assertThat(root.resolve(".git/hooks/pre-push")).doesNotExist();
    assertThat(events)
        .containsExactly(
            "event=init-optional-default question=tools reason=end-of-input",
            "event=init-optional-default question=quality-hooks reason=end-of-input");
  }

  @Test
  void endOfInputAtInstallationConsentAbortsWithoutWriting() {
    final int exit =
        execute(new TerminalLikeInputStream("3\n1\n1\n1\n"), "--directory", root.toString());

    assertThat(exit).isNotZero();
    assertThat(output.toString()).contains("Install OpenSpec?", "input ended");
    assertThat(root.resolve("ward.toml")).doesNotExist();
    assertThat(root.resolve(".ward")).doesNotExist();
    assertThat(root.resolve(".git/hooks/pre-commit")).doesNotExist();
  }

  @Test
  void numberOfPresetGateRemovesItFromStandard() throws IOException {
    final int exit =
        execute(new TerminalLikeInputStream("1\n1\n6\n"), "--directory", root.toString());

    assertThat(exit).as(output.toString()).isZero();
    assertThat(output.toString()).contains("> [x] 6) pmd", "or press Enter to accept");
    final String configuration = Files.readString(root.resolve("ward.toml"));
    assertThat(configuration)
        .contains("preset = \"standard\"", "[quality-gates.checkstyle]")
        .doesNotContain("[quality-gates.pmd]");
    assertThat(root.resolve("config/pmd-ruleset.xml")).doesNotExist();
  }

  @Test
  void blankGateAnswerAcceptsStandardGates() throws IOException {
    final int exit =
        execute(new TerminalLikeInputStream("1\n1\n\n"), "--directory", root.toString());

    assertThat(exit).as(output.toString()).isZero();
    final String configuration = Files.readString(root.resolve("ward.toml"));
    for (final String gate : JavaPresetGates.STANDARD) {
      assertThat(configuration).contains("[quality-gates." + gate + "]");
    }
    assertThat(configuration.split("\\[quality-gates\\.", -1))
        .hasSize(JavaPresetGates.STANDARD.size() + STANDARD_PARTIAL_TABLES + 1);
  }

  @Test
  void presetOptionKeepsLayeredArchitectureWithoutPrompting() throws IOException {
    final int exit =
        execute(
            new TerminalLikeInputStream(""),
            "--directory",
            root.toString(),
            "--integration",
            "none",
            "--preset",
            "strict");

    assertThat(exit).as(output.toString()).isZero();
    assertThat(output.toString()).doesNotContain("Select an architecture style");
    assertThat(Files.readString(root.resolve("ward.toml")))
        .contains("[quality-gates.archunit]", "[quality-gates.mutation]");
    assertThat(output.toString()).contains("Generated layered ArchUnit test");
  }

  private int execute(final InputStream input, final String... args) {
    final InitService service =
        new InitService(
            new ProjectDetector(new FileSystemProjectInspection()),
            Set.of(),
            new InitSetupCatalog(new FakeEnvironmentInspection(), new QualityHookInstaller()),
            new InitProjectFiles(
                new PomToolConfigurator(),
                new PresetRuleFileWriter(),
                new ArchitectureTestGenerator(),
                new FileConfigurationStorage()));
    final CommandLine command = new CommandLine(new InitCommand(service, input));
    command.setOut(new PrintWriter(output, true));
    command.setErr(new PrintWriter(output, true));
    return command.execute(args);
  }
}
