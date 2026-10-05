package dev.sentinel.application;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.project.Project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Stable choices and project-aware defaults used by the interactive init flow. */
public final class InitSetupCatalog {
    public static final String NO_INTEGRATION = "none";

    public record ArchitectureOption(String id, String label) {
    }

    public record IntegrationOption(String id, String label) {
    }

    public record GateOption(String id, List<String> command, boolean available, String availabilityMessage) {
        public GateOption {
            command = List.copyOf(command);
        }
    }

    public List<IntegrationOption> integrations() {
        return List.of(
                new IntegrationOption(NO_INTEGRATION, "No agent integration"),
                new IntegrationOption("opencode", "OpenCode"),
                new IntegrationOption("claude-code", "Claude Code"));
    }

    public List<ArchitectureOption> architectures() {
        return List.of(
                new ArchitectureOption("layered", "Layered (controller -> service -> repository)"),
                new ArchitectureOption("hexagonal", "Hexagonal (domain isolated from adapters)"),
                new ArchitectureOption("clean", "Clean (domain isolated from outer layers)"));
    }

    public ArchitectureOption architecture(String id) {
        return architectures().stream().filter(option -> option.id().equals(id)).findFirst()
                .orElseThrow(() -> new SentinelException("Unknown architecture '" + id
                        + "'. Supported architectures: layered, hexagonal, clean"));
    }

    public List<GateOption> gates(Project project) {
        String executable = project.mavenWrapperAvailable() ? "./mvnw" : "mvn";
        boolean mavenAvailable = project.mavenWrapperAvailable() || commandAvailable("mvn");
        String availability = mavenAvailable
                ? "Maven command available: " + executable
                : "Maven is unavailable; install Maven or add a Maven Wrapper before running this gate.";
        List<GateOption> options = new ArrayList<>();
        options.add(option("tests", executable, "test", mavenAvailable, availability));
        options.add(option("compile", executable, "compile", mavenAvailable, availability));
        options.add(option("command", executable, "test", mavenAvailable,
                availability + " (customize the command in sentinel.toml after initialization.)"));
        options.add(option("archunit", executable, "-Dtest=ArchitectureTest", "test", mavenAvailable,
                availability + " (runs only ArchitectureTest.)"));
        options.add(option("checkstyle", executable, "checkstyle:check", mavenAvailable, availability));
        options.add(option("spotbugs", executable, "spotbugs:check", mavenAvailable, availability));
        options.add(option("sonar", executable, "sonar", mavenAvailable, availability));
        return List.copyOf(options);
    }

    public IntegrationOption integration(String id) {
        return integrations().stream().filter(option -> option.id().equals(id)).findFirst()
                .orElseThrow(() -> new SentinelException("Unknown integration '" + id
                        + "'. Supported integrations: none, opencode, claude-code"));
    }

    public GateOption gate(Project project, String id) {
        return gates(project).stream().filter(option -> option.id().equals(id)).findFirst()
                .orElseThrow(() -> new SentinelException("Unknown quality gate '" + id
                        + "'. Supported gates: " + QualityGateFactory.SUPPORTED_GATES));
    }

    private static GateOption option(String id, String executable, String goal,
                                     boolean available, String message) {
        return new GateOption(id, List.of(executable, goal), available, message);
    }

    private static GateOption option(String id, String executable, String firstGoal, String secondGoal,
                                     boolean available, String message) {
        return new GateOption(id, List.of(executable, firstGoal, secondGoal), available, message);
    }

    private static boolean commandAvailable(String command) {
        return Files.isExecutable(Path.of("/usr/bin", command))
                || Files.isExecutable(Path.of("/opt/homebrew/bin", command))
                || Files.isExecutable(Path.of("/usr/local/bin", command));
    }
}
