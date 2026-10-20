package dev.sentinel.infrastructure.cli.agent;

import dev.sentinel.application.agent.IntegrationService;
import dev.sentinel.domain.agent.GuardMode;
import dev.sentinel.domain.agent.IntegrationRequest;
import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.agent.SupportedAgents;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.infrastructure.cli.ExitCodes;
import dev.sentinel.infrastructure.cli.ProjectOptions;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** The {@code sentinel integrate} command: installs or removes an agent integration. */
@Command(
    name = "integrate",
    description = "Configure a supported coding-agent integration.",
    mixinStandardHelpOptions = true,
    subcommands = {})
public final class IntegrateCommand implements Callable<Integer> {
  @Spec private CommandSpec spec;

  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @Mixin private final ProjectOptions options = new ProjectOptions();

  @Parameters(
      index = "0",
      arity = "0..1",
      description = "Supported agent ID (prompts when omitted)")
  private String agent;

  @Option(names = "--remove", description = "Remove only Sentinel-owned integration content.")
  private boolean remove;

  @Option(
      names = "--guard-approved-specs",
      negatable = true,
      description = {
        "Install the opt-in guard that stops agents from modifying approved OpenSpec specs and",
        "archived changes; --no-guard-approved-specs removes it. Omit to keep the current state."
      })
  private Boolean guard;

  private final IntegrationService service;
  private final InputStream input;

  /** Creates the command reading confirmations from standard input. */
  @Inject
  public IntegrateCommand(final IntegrationService service) {
    this(service, System.in);
  }

  /** Creates the command reading confirmations from {@code input}. */
  public IntegrateCommand(final IntegrationService service, final InputStream input) {
    this.service = service;
    this.input = input;
  }

  @Override
  public Integer call() {
    if (remove && guard != null) {
      error().println("--remove cannot be combined with --[no-]guard-approved-specs.");
      return ExitCodes.ERROR;
    }
    if (agent == null || agent.isBlank()) {
      agent = selectAgent();
    }
    final IntegrationResult result =
        service.integrate(
            agent, options.directory(), new IntegrationRequest(remove, GuardMode.of(guard)));
    output().printf("%s: %s%n", result.status(), result.message());
    if (!result.capabilities().isEmpty()) {
      output()
          .printf(
              "Capabilities: %s%n",
              result.capabilities().stream().map(Enum::name).sorted().toList());
    }
    result.changed().forEach(output()::println);
    return result.status() == IntegrationResult.Status.CONFLICT ? ExitCodes.ERROR : ExitCodes.OK;
  }

  private String selectAgent() {
    output().println("Select an agent integration:");
    final List<SupportedAgents.Definition> agents = SupportedAgents.definitions();
    for (int index = 0; index < agents.size(); index++) {
      output().printf("%d) %s%n", index + 1, agents.get(index).id());
    }
    output().printf("Choose an agent [1-%d]: ", agents.size());
    output().flush();
    try {
      final String selection =
          new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)).readLine();
      if (selection == null) {
        throw new SentinelException(
            "No agent selected. Pass an agent argument or choose one interactively.");
      }
      final int selected = parseSelection(selection, agents.size());
      if (selected < 1 || selected > agents.size()) {
        throw invalidSelection(selection, agents.size());
      }
      return agents.get(selected - 1).id();
    } catch (IOException e) {
      throw new SentinelException("Could not read agent selection: " + e.getMessage(), e);
    }
  }

  private static int parseSelection(final String selection, final int size) {
    try {
      return Integer.parseInt(selection.trim());
    } catch (NumberFormatException exception) {
      throw invalidSelection(selection, size, exception);
    }
  }

  private static SentinelException invalidSelection(final String selection, final int size) {
    return invalidSelection(selection, size, null);
  }

  private static SentinelException invalidSelection(
      final String selection, final int size, final Throwable cause) {
    return new SentinelException(
        "Invalid agent selection '" + selection + "'. Choose a number from 1 to " + size + ".",
        cause);
  }

  private PrintWriter error() {
    return spec == null
        ? new PrintWriter(System.err, true, StandardCharsets.UTF_8)
        : spec.commandLine().getErr();
  }

  private PrintWriter output() {
    return spec == null
        ? new PrintWriter(System.out, true, StandardCharsets.UTF_8)
        : spec.commandLine().getOut();
  }
}
