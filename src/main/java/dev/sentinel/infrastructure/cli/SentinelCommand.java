package dev.sentinel.infrastructure.cli;

import dev.sentinel.infrastructure.cli.agent.IntegrateCommand;
import dev.sentinel.infrastructure.cli.doctor.DoctorCommand;
import dev.sentinel.infrastructure.cli.gate.CheckCommand;
import dev.sentinel.infrastructure.cli.init.InitCommand;
import dev.sentinel.infrastructure.cli.loop.LoopCommand;
import dev.sentinel.infrastructure.cli.project.DetectCommand;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
    name = "sentinel",
    description = "Sentinel is a quality gate orchestrator for AI coding agents.",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    subcommands = {
      DetectCommand.class,
      InitCommand.class,
      CheckCommand.class,
      IntegrateCommand.class,
      DoctorCommand.class,
      LoopCommand.class
    },
    footer = {"", "Exit codes: 0 = ok, 1 = failed, 2 = error"})
public class SentinelCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  @Override
  public Integer call() {
    if (spec == null) {
      PrintWriter err = new PrintWriter(System.err, true, StandardCharsets.UTF_8);
      err.println("Usage: sentinel [COMMAND]");
      err.println("Use 'sentinel --help' for available commands.");
    } else {
      spec.commandLine().usage(spec.commandLine().getErr());
    }
    return ExitCodes.ERROR;
  }
}
