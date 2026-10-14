package dev.sentinel.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.application.init.InitService;
import dev.sentinel.application.init.InitSetupCatalog;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.FakeEnvironmentInspection;
import dev.sentinel.domain.init.CompatibilityProjectSnapshot;
import dev.sentinel.domain.init.ToolchainSnapshot;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.infrastructure.init.ArchitectureTestGenerator;
import dev.sentinel.infrastructure.init.FileConfigurationStorage;
import dev.sentinel.infrastructure.init.PomToolConfigurator;
import dev.sentinel.infrastructure.init.PresetRuleFileWriter;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class InitCompatibilityCliTest {
  @Test
  void refusalLeavesExistingPluginUnchanged(@TempDir final Path root) throws Exception {
    writePom(root);
    final StringWriter output = new StringWriter();
    final CommandLine command = command("n\n", output);

    final int exit =
        command.execute(
            "--directory", root.toString(), "--integration", "none", "--gate", "format");

    assertThat(exit).isZero();
    assertThat(Files.readString(root.resolve("pom.xml"))).contains("<version>1.0.0</version>");
    assertThat(output.toString()).contains("Compatibility update proposed");
  }

  @Test
  void yesAppliesValidatedCompatibilityUpdateWithoutPrompt(@TempDir final Path root)
      throws Exception {
    writePom(root);
    final StringWriter output = new StringWriter();
    final CommandLine command = command("", output);

    final int exit =
        command.execute(
            "--directory", root.toString(), "--integration", "none", "--gate", "format", "--yes");

    assertThat(exit).isZero();
    assertThat(Files.readString(root.resolve("pom.xml"))).contains("<version>3.10.3</version>");
    assertThat(output.toString()).doesNotContain("Compatibility update proposed");
  }

  private static CommandLine command(final String input, final StringWriter output) {
    final ProjectDetector detector = new ProjectDetector(new FileSystemProjectInspection());
    final PomToolConfigurator configurator =
        new PomToolConfigurator(
            project ->
                new CompatibilityProjectSnapshot(
                    project,
                    new ToolchainSnapshot(
                        17, Optional.of("3.9.0"), Optional.empty(), Framework.NONE),
                    Map.of("spotless-maven-plugin", "1.0.0")));
    final InitService service =
        new InitService(
            detector,
            Set.of(),
            new InitSetupCatalog(new FakeEnvironmentInspection()),
            configurator,
            new ArchitectureTestGenerator(),
            new FileConfigurationStorage(),
            new PresetRuleFileWriter());
    final InitCommand init =
        new InitCommand(service, new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    final CommandLine command = new CommandLine(init);
    command.setOut(new PrintWriter(output, true));
    command.setErr(new PrintWriter(output, true));
    return command;
  }

  private static void writePom(final Path root) throws IOException {
    Files.writeString(
        root.resolve("pom.xml"),
        "<project><build><plugins><plugin><artifactId>spotless-maven-plugin</artifactId>"
            + "<version>1.0.0</version></plugin></plugins></build></project>");
  }
}
