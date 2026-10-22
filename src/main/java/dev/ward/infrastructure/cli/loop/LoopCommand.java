package dev.ward.infrastructure.cli.loop;

import dev.ward.application.loop.QualityLoopService;
import dev.ward.domain.config.CommandLineTokenizer;
import dev.ward.domain.loop.LoopConfiguration;
import dev.ward.domain.loop.LoopRequest;
import dev.ward.domain.loop.LoopResult;
import dev.ward.domain.loop.LoopTerminalState;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.cli.ProjectOptions;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** The {@code ward loop} command: runs agent attempts until the quality gates pass. */
@Command(
    name = "loop",
    description = "Run bounded agent attempts followed by quality checks.",
    mixinStandardHelpOptions = true)
public final class LoopCommand implements Callable<Integer> {
  private final QualityLoopService service;

  @Spec private CommandSpec spec;

  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @Mixin private final ProjectOptions options = new ProjectOptions();

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

  /** Creates the command; {@code service} runs the loop. */
  @Inject
  public LoopCommand(final QualityLoopService service) {
    this.service = service;
  }

  @Override
  public Integer call() {
    final Path root = options.directory().toAbsolutePath().normalize();
    final LoopRequest request =
        new LoopRequest(
            root,
            CommandLineTokenizer.tokenize(agentCommand),
            task,
            new LoopConfiguration(maxIterations, timeoutSeconds, allowDirty));
    final LoopResult result = service.run(request);
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
