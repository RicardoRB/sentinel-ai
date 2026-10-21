package dev.sentinel.infrastructure.cli.doctor;

import dev.sentinel.application.doctor.DoctorService;
import dev.sentinel.application.doctor.DoctorService.Finding;
import dev.sentinel.application.update.UpdateNoticeService;
import dev.sentinel.application.update.UpdateStatus;
import dev.sentinel.infrastructure.cli.ExitCodes;
import dev.sentinel.infrastructure.cli.ProjectOptions;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** The {@code sentinel doctor} command: prints the project's diagnostic findings. */
@Command(
    name = "doctor",
    description = "Diagnose Sentinel project and integration prerequisites.",
    mixinStandardHelpOptions = true)
public final class DoctorCommand implements Callable<Integer> {
  @Spec private CommandSpec spec;

  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @Mixin private final ProjectOptions options = new ProjectOptions();

  private final Supplier<DoctorService> service;
  private final UpdateNoticeService updates;

  /**
   * Creates the command; {@code service} runs the diagnostics and {@code updates} reports whether a
   * newer Sentinel release exists.
   */
  @Inject
  public DoctorCommand(final DoctorService service, final UpdateNoticeService updates) {
    this.service = () -> service;
    this.updates = updates;
  }

  @Override
  public Integer call() {
    final List<Finding> findings = new ArrayList<>(service.get().diagnose(options.directory()));
    findings.add(versionFinding(updates.freshStatus()));
    findings.forEach(f -> output().printf("[%s] %-13s %s%n", f.status(), f.name(), f.message()));
    return findings.stream().anyMatch(f -> f.status() == DoctorService.Status.ERROR)
        ? ExitCodes.ERROR
        : ExitCodes.OK;
  }

  // An outdated or unknown version is advice, never a reason for doctor to fail.
  private static Finding versionFinding(final UpdateStatus status) {
    final DoctorService.Status severity =
        switch (status.state()) {
          case OUTDATED, UNKNOWN -> DoctorService.Status.WARNING;
          default -> DoctorService.Status.OK;
        };
    return new Finding("sentinel", severity, status.message());
  }

  private PrintWriter output() {
    return spec == null
        ? new PrintWriter(System.out, true, StandardCharsets.UTF_8)
        : spec.commandLine().getOut();
  }
}
