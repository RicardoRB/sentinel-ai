package dev.sentinel.cli;

import dev.sentinel.application.CheckService;
import dev.sentinel.application.GitStateInspector;
import dev.sentinel.application.QualityLoopService;
import dev.sentinel.domain.config.CommandLineTokenizer;
import dev.sentinel.domain.loop.LoopConfiguration;
import dev.sentinel.infrastructure.ProcessAgentRunner;
import dev.sentinel.infrastructure.ProcessCommandExecutor;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

@Command(name = "loop", description = "Run bounded agent attempts followed by quality checks.", mixinStandardHelpOptions = true)
public final class LoopCommand implements Callable<Integer> {
    @Spec private CommandSpec spec;
    @Mixin private ProjectOptions options = new ProjectOptions();
    @Parameters(index = "0", description = "Task passed to the external agent.") private String task;
    @Option(names = "--agent-command", required = true, description = "Agent executable and arguments; never run through a shell.") private String agentCommand;
    @Option(names = "--max-iterations", defaultValue = "3") private int maxIterations;
    @Option(names = "--timeout-seconds", defaultValue = "300") private int timeoutSeconds;
    @Option(names = "--allow-dirty") private boolean allowDirty;

    @Override public Integer call() {
        var root = options.directory().toAbsolutePath().normalize();
        var runner = new ProcessAgentRunner(new ProcessCommandExecutor(), root, CommandLineTokenizer.tokenize(agentCommand));
        var service = new CheckService(new dev.sentinel.application.ProjectDetector(), new dev.sentinel.infrastructure.TomlConfigurationReader(),
                new dev.sentinel.application.QualityGateFactory(new ProcessCommandExecutor()), new dev.sentinel.application.QualityGateRunner());
        var result = new QualityLoopService(service, runner, new GitStateInspector(new ProcessCommandExecutor()))
                .run(root, task, new LoopConfiguration(maxIterations, timeoutSeconds, allowDirty));
        PrintWriter out = output();
        out.printf("%s after %d iteration(s): %s%n", result.state(), result.iterations(), result.message());
        return result.state() == dev.sentinel.domain.loop.LoopTerminalState.PASSED ? ExitCodes.OK : ExitCodes.FAILED;
    }

    private PrintWriter output() {
        return spec == null ? new PrintWriter(System.out, true) : spec.commandLine().getOut();
    }
}
