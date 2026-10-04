package dev.sentinel.application;

import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import com.google.inject.Inject;

public final class DoctorService {
    public enum Status { OK, WARNING, ERROR }
    public record Finding(String name, Status status, String message) {}

    private final ProjectDetector detector;
    @Inject
    public DoctorService(ProjectDetector detector) { this.detector = detector; }

    public List<Finding> diagnose(Path start) {
        List<Finding> findings = new ArrayList<>();
        var project = detector.detect(start);
        if (project.isEmpty()) {
            findings.add(new Finding("project", Status.ERROR, "No supported project detected."));
            return findings;
        }
        Path root = project.get().root();
        findings.add(new Finding("project", Status.OK, root.toString()));
        findings.add(new Finding("maven", Files.isExecutable(root.resolve("mvnw")) || commandAvailable("mvn")
                ? Status.OK : Status.ERROR, project.get().mavenWrapperAvailable() ? "Maven Wrapper available." : "System Maven is required."));
        Path config = root.resolve(SentinelConfiguration.FILE_NAME);
        try {
            if (!Files.isRegularFile(config)) throw new SentinelException("Run 'sentinel init'.");
            findings.add(new Finding("configuration", Status.OK, "Configuration is present."));
        } catch (SentinelException e) {
            findings.add(new Finding("configuration", Status.ERROR, e.getMessage()));
        }
        findings.add(new Finding("integration", Files.isRegularFile(root.resolve(".opencode/commands/sentinel-check.md"))
                ? Status.OK : Status.WARNING, "OpenCode integration " + (Files.isRegularFile(root.resolve(".opencode/commands/sentinel-check.md")) ? "present." : "not configured.")));
        findings.add(new Finding("native", Files.isExecutable(Path.of("target/sentinel")) ? Status.OK : Status.WARNING,
                Files.isExecutable(Path.of("target/sentinel")) ? "Native executable present." : "Build with the native profile."));
        return findings;
    }

    private static boolean commandAvailable(String command) {
        return Files.isExecutable(Path.of("/usr/bin", command)) || Files.isExecutable(Path.of("/opt/homebrew/bin", command));
    }
}
