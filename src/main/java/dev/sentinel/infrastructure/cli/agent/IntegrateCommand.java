package dev.sentinel.infrastructure.cli.agent;

import dev.sentinel.application.agent.IntegrationService;
import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.infrastructure.cli.ExitCodes;
import dev.sentinel.infrastructure.cli.ProjectOptions;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
    name = "integrate",
    description = "Configure a supported coding-agent integration.",
    mixinStandardHelpOptions = true,
    subcommands = {})
public final class IntegrateCommand implements Callable<Integer> {
  @Spec private CommandSpec spec;

  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @SuppressWarnings("PMD.ImmutableField")
  @Mixin
  private ProjectOptions options = new ProjectOptions();

  @Parameters(
      index = "0",
      arity = "0..1",
      description = "Agent: opencode or claude-code (prompts when omitted)")
  private String agent;

  @Option(names = "--remove", description = "Remove only Sentinel-owned integration content.")
  private boolean remove;

  private final IntegrationService service;
  private final InputStream input;

  @Inject
  public IntegrateCommand(final IntegrationService service) {
    this(service, System.in);
  }

  public IntegrateCommand(final IntegrationService service, final InputStream input) {
    this.service = service;
    this.input = input;
  }

  @Override
  public Integer call() {
    if (agent == null || agent.isBlank()) {
      agent = selectAgent();
    }
    final IntegrationResult result = service.integrate(agent, options.directory(), remove);
    output().printf("%s: %s%n", result.status(), result.message());
    result.changed().forEach(output()::println);
    return result.status() == IntegrationResult.Status.CONFLICT ? ExitCodes.ERROR : ExitCodes.OK;
  }

  private String selectAgent() {
    output().println("Select an agent integration:");
    output().println("1) opencode");
    output().println("2) claude-code");
    output().print("Choose an agent [1-2]: ");
    output().flush();
    try {
      final String selection =
          new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)).readLine();
      if (selection == null) {
        throw new SentinelException(
            "No agent selected. Pass an agent argument or choose one interactively.");
      }
      return switch (selection.trim()) {
        case "1" -> "opencode";
        case "2" -> "claude-code";
        default ->
            throw new SentinelException(
                "Invalid agent selection '"
                    + selection
                    + "'. Choose 1 for opencode or 2 for claude-code.");
      };
    } catch (IOException e) {
      throw new SentinelException("Could not read agent selection: " + e.getMessage(), e);
    }
  }

  private PrintWriter output() {
    return spec == null
        ? new PrintWriter(System.out, true, StandardCharsets.UTF_8)
        : spec.commandLine().getOut();
  }
}
