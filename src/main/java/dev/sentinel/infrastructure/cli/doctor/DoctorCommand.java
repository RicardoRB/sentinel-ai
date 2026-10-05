package dev.sentinel.infrastructure.cli.doctor;

import dev.sentinel.application.doctor.DoctorService;
import dev.sentinel.infrastructure.cli.ExitCodes;
import dev.sentinel.infrastructure.cli.ProjectOptions;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;
import com.google.inject.Inject;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

@Command(name = "doctor", description = "Diagnose Sentinel project and integration prerequisites.", mixinStandardHelpOptions = true)
public final class DoctorCommand implements Callable<Integer> {
    @Spec private CommandSpec spec;
    @Mixin private ProjectOptions options = new ProjectOptions();
    private final DoctorService service;

    @Inject
    public DoctorCommand(DoctorService service) { this.service = service; }

    @Override public Integer call() {
        PrintWriter out = output();
        var findings = service.diagnose(options.directory());
        findings.forEach(f -> out.printf("[%s] %-13s %s%n", f.status(), f.name(), f.message()));
        return findings.stream().anyMatch(f -> f.status() == DoctorService.Status.ERROR)
                ? ExitCodes.ERROR : ExitCodes.OK;
    }

    private PrintWriter output() {
        return spec == null ? new PrintWriter(System.out, true, StandardCharsets.UTF_8) : spec.commandLine().getOut();
    }
}
