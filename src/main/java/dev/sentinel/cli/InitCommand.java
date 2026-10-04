package dev.sentinel.cli;

import dev.sentinel.application.InitService;
import com.google.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

@Command(name = "init", description = "Create a default sentinel.toml in the project root.",
        mixinStandardHelpOptions = true, versionProvider = VersionProvider.class)
public class InitCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;
    @Mixin
    private ProjectOptions options = new ProjectOptions();

    private final InitService service;

    @Inject
    public InitCommand(InitService service) {
        this.service = service;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        InitService.InitResult result = service.init(options.directory());
        if (result.created()) {
            out.println("Created " + result.file());
        } else {
            out.println(result.file() + " already exists. Nothing was changed.");
        }
        return ExitCodes.OK;
    }
}
