package dev.sentinel.infrastructure.cli;

import dev.sentinel.application.agent.IntegrationService;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.agent.OpenSpecSetupInspection;
import dev.sentinel.infrastructure.agent.ClaudeCodeIntegration;
import dev.sentinel.infrastructure.agent.OpenCodeIntegration;
import dev.sentinel.infrastructure.cli.agent.IntegrateCommand;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.List;
import picocli.CommandLine;

/** Builds an {@code integrate} command line over the real adapters for CLI integration tests. */
final class IntegrateCommandFixture {
  private IntegrateCommandFixture() {}

  static CommandLine commandLine(
      final InputStream input,
      final OpenSpecSetupInspection openSpec,
      final PrintWriter out,
      final PrintWriter err,
      final CommandLine.IExecutionStrategy strategy) {
    final IntegrateCommand integrate =
        new IntegrateCommand(
            new IntegrationService(
                new ProjectDetector(new FileSystemProjectInspection()),
                List.of(new OpenCodeIntegration(), new ClaudeCodeIntegration()),
                openSpec),
            input);
    final CommandLine cli = new CommandLine(integrate);
    cli.setOut(out);
    cli.setErr(err);
    cli.setExecutionExceptionHandler(CommandLineRunnerImpl::handle);
    cli.setExecutionStrategy(strategy);
    return cli;
  }
}
