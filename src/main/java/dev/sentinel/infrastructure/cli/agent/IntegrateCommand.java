package dev.sentinel.infrastructure.cli.agent;


import java.io.InputStream;
import dev.sentinel.application.agent.IntegrationService;
import dev.sentinel.infrastructure.cli.ExitCodes;
import dev.sentinel.infrastructure.cli.ProjectOptions;
import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.config.SentinelException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import com.google.inject.Inject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

@Command(name = "integrate", description = "Configure a supported coding-agent integration.",
        mixinStandardHelpOptions = true, subcommands = {})
public final class IntegrateCommand implements Callable<Integer> {
    @Spec private CommandSpec spec;
    @Mixin private ProjectOptions options = new ProjectOptions();
    @Parameters(index = "0", arity = "0..1", description = "Agent: opencode or claude-code (prompts when omitted)") private String agent;
    @Option(names = "--remove", description = "Remove only Sentinel-owned integration content.") private boolean remove;
    private final IntegrationService service;
    private final InputStream input;

    @Inject
    public IntegrateCommand(IntegrationService service) {
        this(service, System.in);
    }

    public IntegrateCommand(IntegrationService service, InputStream input) {
        this.service = service;
        this.input = input;
    }

    @Override public Integer call() {
        try {
            if (agent == null || agent.isBlank()) agent = selectAgent();
            IntegrationResult result = service.integrate(agent, options.directory(), remove);
            PrintWriter out = output();
            out.printf("%s: %s%n", result.status(), result.message());
            result.changed().forEach(path -> out.println(path));
            return result.status() == IntegrationResult.Status.CONFLICT ? ExitCodes.ERROR : ExitCodes.OK;
        } catch (SentinelException e) {
            throw e;
        }
    }

    private String selectAgent() {
        PrintWriter out = output();
        out.println("Select an agent integration:");
        out.println("1) opencode");
        out.println("2) claude-code");
        out.print("Choose an agent [1-2]: ");
        out.flush();
        try {
            String selection = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .readLine();
            if (selection == null) {
                throw new SentinelException("No agent selected. Pass an agent argument or choose one interactively.");
            }
            return switch (selection.trim()) {
                case "1" -> "opencode";
                case "2" -> "claude-code";
                default -> throw new SentinelException("Invalid agent selection '" + selection
                        + "'. Choose 1 for opencode or 2 for claude-code.");
            };
        } catch (IOException e) {
            throw new SentinelException("Could not read agent selection: " + e.getMessage());
        }
    }

    private PrintWriter output() {
        return spec == null ? new PrintWriter(System.out, true, StandardCharsets.UTF_8) : spec.commandLine().getOut();
    }
}
