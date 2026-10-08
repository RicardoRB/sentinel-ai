package dev.sentinel.infrastructure.cli.project;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.project.Project;
import dev.sentinel.infrastructure.cli.ExitCodes;
import dev.sentinel.infrastructure.cli.ProjectOptions;
import dev.sentinel.infrastructure.cli.VersionProvider;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
    name = "detect",
    description = "Detect the supported project in the current directory.",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class)
public class DetectCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;
  @Mixin private ProjectOptions options = new ProjectOptions();

  private final ProjectDetector detector;

  @Inject
  public DetectCommand(ProjectDetector detector) {
    this.detector = detector;
  }

  @Override
  public Integer call() {
    final Optional<Project> project = detector.detect(options.directory());
    if (project.isEmpty()) {
      output().println("No supported project detected.");
      return ExitCodes.FAILED;
    }
    final Project p = project.get();
    output().println("Project detected");
    output().println();
    output().printf("Language:     %s%n", p.language().displayName());
    output().printf("Build tool:   %s%n", p.buildTool().displayName());
    output().printf("Framework:    %s%n", p.framework().displayName());
    output().printf("Project root: %s%n", p.root());
    return ExitCodes.OK;
  }

  private PrintWriter output() {
    return spec == null
        ? new PrintWriter(System.out, true, StandardCharsets.UTF_8)
        : spec.commandLine().getOut();
  }
}
