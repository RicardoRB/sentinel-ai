package dev.sentinel.infrastructure.cli.doctor;

import dev.sentinel.application.doctor.DoctorService;
import dev.sentinel.application.doctor.DoctorService.Finding;
import dev.sentinel.infrastructure.cli.ExitCodes;
import dev.sentinel.infrastructure.cli.ProjectOptions;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
    name = "doctor",
    description = "Diagnose Sentinel project and integration prerequisites.",
    mixinStandardHelpOptions = true)
public final class DoctorCommand implements Callable<Integer> {
  @Spec private CommandSpec spec;

  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @Mixin private final ProjectOptions options = new ProjectOptions();

  private final DoctorService service;

  @Inject
  public DoctorCommand(final DoctorService service) {
    this.service = service;
  }

  @Override
  public Integer call() {
    final List<Finding> findings = service.diagnose(options.directory());
    findings.forEach(f -> output().printf("[%s] %-13s %s%n", f.status(), f.name(), f.message()));
    return findings.stream().anyMatch(f -> f.status() == DoctorService.Status.ERROR)
        ? ExitCodes.ERROR
        : ExitCodes.OK;
  }

  private PrintWriter output() {
    return spec == null
        ? new PrintWriter(System.out, true, StandardCharsets.UTF_8)
        : spec.commandLine().getOut();
  }
}
