package dev.sentinel.cli;

import dev.sentinel.application.ProjectDetector;
import dev.sentinel.domain.project.Project;
import com.google.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.Callable;

@Command(name = "detect", description = "Detect the supported project in the current directory.",
        mixinStandardHelpOptions = true, versionProvider = VersionProvider.class)
public class DetectCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;
    @Mixin
    private ProjectOptions options = new ProjectOptions();

    private final ProjectDetector detector;

    @Inject
    public DetectCommand(ProjectDetector detector) {
        this.detector = detector;
    }

    @Override
    public Integer call() {
        PrintWriter out = output();
        Optional<Project> project = detector.detect(options.directory());
        if (project.isEmpty()) {
            out.println("No supported project detected.");
            return ExitCodes.FAILED;
        }
        Project p = project.get();
        out.println("Project detected");
        out.println();
        out.printf("Language:     %s%n", p.language().displayName());
        out.printf("Build tool:   %s%n", p.buildTool().displayName());
        out.printf("Framework:    %s%n", p.framework().displayName());
        out.printf("Project root: %s%n", p.root());
        return ExitCodes.OK;
    }

    private PrintWriter output() {
        return spec == null ? new PrintWriter(System.out, true, StandardCharsets.UTF_8) : spec.commandLine().getOut();
    }
}
