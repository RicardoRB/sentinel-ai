package dev.sentinel.infrastructure.cli.loop;

import dev.sentinel.application.loop.QualityLoopService;
import dev.sentinel.domain.config.CommandLineTokenizer;
import dev.sentinel.domain.loop.LoopConfiguration;
import dev.sentinel.domain.loop.LoopRequest;
import dev.sentinel.domain.loop.LoopTerminalState;
import dev.sentinel.infrastructure.cli.ExitCodes;
import dev.sentinel.infrastructure.cli.ProjectOptions;
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
    name = "loop",
    description = "Run bounded agent attempts followed by quality checks.",
    mixinStandardHelpOptions = true)
public final class LoopCommand implements Callable<Integer> {
  private final QualityLoopService service;

  @Inject
  public LoopCommand(QualityLoopService service) {
    this.service = service;
  }

  @Spec private CommandSpec spec;
  @Mixin private ProjectOptions options = new ProjectOptions();

  @Parameters(index = "0", description = "Task passed to the external agent.")
  private String task;

  @Option(
      names = "--agent-command",
      required = true,
      description = "Agent executable and arguments; never run through a shell.")
  private String agentCommand;

  @Option(names = "--max-iterations", defaultValue = "3")
  private int maxIterations;

  @Option(names = "--timeout-seconds", defaultValue = "300")
  private int timeoutSeconds;

  @Option(names = "--allow-dirty")
  private boolean allowDirty;

  @Override
  public Integer call() {
    final var root = options.directory().toAbsolutePath().normalize();
    final var request =
        new LoopRequest(
            root,
            CommandLineTokenizer.tokenize(agentCommand),
            task,
            new LoopConfiguration(maxIterations, timeoutSeconds, allowDirty));
    final var result = service.run(request);
    output()
        .printf(
            "%s after %d iteration(s): %s%n",
            result.state(), result.iterations(), result.message());
    return result.state() == LoopTerminalState.PASSED ? ExitCodes.OK : ExitCodes.FAILED;
  }

  private PrintWriter output() {
    return spec == null
        ? new PrintWriter(System.out, true, StandardCharsets.UTF_8)
        : spec.commandLine().getOut();
  }
}
