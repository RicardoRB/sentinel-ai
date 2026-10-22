package dev.ward.infrastructure.cli.spec;

import dev.ward.infrastructure.cli.ExitCodes;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** The {@code ward spec} command group; it runs only through a subcommand. */
@Command(
    name = "spec",
    description = "Work with the project's specification artifacts.",
    subcommands = {SpecValidateCommand.class, SpecGuardCommand.class, SpecEnforceCommand.class})
public final class SpecCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  /** Creates the command group. */
  @Inject
  public SpecCommand() {
    // Container command; Dagger constructs it through this constructor.
  }

  @Override
  public Integer call() {
    if (spec != null) {
      spec.commandLine().usage(spec.commandLine().getErr());
    }
    return ExitCodes.ERROR;
  }
}
