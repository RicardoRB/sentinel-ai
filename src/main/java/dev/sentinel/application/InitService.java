package dev.sentinel.application;

import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.config.GateConfiguration;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.project.Project;
import com.google.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class InitService {

    static final String DEFAULT_CONFIGURATION = """
            version = 1

            [quality-gates.tests]
            enabled = true
            command = "./mvnw test"
            """;

    public record InitResult(Path file, boolean created, List<InitSetupCatalog.GateOption> gates,
                             List<IntegrationResult> integrations, List<String> pomChanges,
                             ArchitectureTestGenerator.TestChange architectureTest) {
        public InitResult(Path file, boolean created) {
            this(file, created, List.of(), List.of(), List.of(), null);
        }

        public InitResult(Path file, boolean created, List<InitSetupCatalog.GateOption> gates,
                          List<IntegrationResult> integrations) {
            this(file, created, gates, integrations, List.of(), null);
        }

        public InitResult(Path file, boolean created, List<InitSetupCatalog.GateOption> gates,
                          List<IntegrationResult> integrations, List<String> pomChanges) {
            this(file, created, gates, integrations, pomChanges, null);
        }

        public InitResult {
            integrations = integrations == null ? List.of() : List.copyOf(integrations);
            pomChanges = pomChanges == null ? List.of() : List.copyOf(pomChanges);
        }
    }

    private final ProjectDetector detector;
    private final IntegrationService integrations;
    private final InitSetupCatalog catalog;
    private final PomToolConfigurator pomTools;
    private final ArchitectureTestGenerator architectureTests;

    /** Backward-compatible service constructor for embedded callers and legacy tests. */
    public InitService(ProjectDetector detector) {
        this(detector, null, new InitSetupCatalog(), new PomToolConfigurator(), new ArchitectureTestGenerator());
    }

    public InitService(ProjectDetector detector, IntegrationService integrations,
                       InitSetupCatalog catalog) {
        this(detector, integrations, catalog, new PomToolConfigurator(), new ArchitectureTestGenerator());
    }

    @Inject
    public InitService(ProjectDetector detector, IntegrationService integrations,
                       InitSetupCatalog catalog, PomToolConfigurator pomTools,
                       ArchitectureTestGenerator architectureTests) {
        this.detector = detector;
        this.integrations = integrations;
        this.catalog = catalog;
        this.pomTools = pomTools;
        this.architectureTests = architectureTests;
    }

    public InitResult init(Path start) {
        Project project = project(start);
        Path file = project.root().resolve(SentinelConfiguration.FILE_NAME);
        try {
            Files.writeString(file, DEFAULT_CONFIGURATION, StandardOpenOption.CREATE_NEW);
            return new InitResult(file, true);
        } catch (java.nio.file.FileAlreadyExistsException e) {
            return new InitResult(file, false);
        } catch (IOException e) {
            throw new SentinelException("Could not write " + file + ": " + e.getMessage(), e);
        }
    }

    public Project project(Path start) {
        return detector.detect(start).orElseThrow(ProjectNotFoundException::new);
    }

    public InitSetupCatalog catalog() {
        return catalog;
    }

    public InitResult initialize(Path start, InitSelection selection) {
        Project project = project(start);
        validateIntegrations(selection.integrations());
        List<InitSetupCatalog.GateOption> gates = selection.gates().stream()
                .map(id -> catalog.gate(project, id)).toList();
        if (gates.isEmpty()) throw new SentinelException("Select at least one quality gate.");
        if (selection.architecture() != null) catalog.architecture(selection.architecture());
        if (Files.exists(project.root().resolve(SentinelConfiguration.FILE_NAME))) {
            return new InitResult(project.root().resolve(SentinelConfiguration.FILE_NAME), false, gates, List.of());
        }

        List<IntegrationResult> installed = new ArrayList<>();
        PomToolConfigurator.PomChange pomChange = null;
        ArchitectureTestGenerator.TestChange architectureTest = null;
        try {
            for (String integration : selection.integrations()) {
                if (InitSetupCatalog.NO_INTEGRATION.equals(integration)) continue;
                IntegrationResult result = installIntegration(project.root(), integration);
                installed.add(result);
                if (result.status() == IntegrationResult.Status.CONFLICT) {
                    throw new SentinelException("Cannot initialize " + project.root()
                            + ": selected integration conflicts with user-owned content.");
                }
            }
            pomChange = pomTools.configure(project.root(), gates);
            if (requiresArchitectureTest(gates)) {
                architectureTest = architectureTests.generate(project.root(),
                        selection.architecture() == null ? "layered" : selection.architecture());
            }
            InitResult result = writeConfiguration(project, gates, installed, pomChange.tools(), architectureTest);
            if (!result.created()) {
                architectureTests.rollback(architectureTest);
                pomTools.rollback(pomChange);
                rollbackNewIntegrations(project.root(), selection.integrations(), installed);
            }
            return result;
        } catch (RuntimeException e) {
            architectureTests.rollback(architectureTest);
            pomTools.rollback(pomChange);
            rollbackNewIntegrations(project.root(), selection.integrations(), installed);
            throw e;
        }
    }

    private InitResult writeConfiguration(Project project, List<InitSetupCatalog.GateOption> gates,
                                          List<IntegrationResult> integrations, List<String> pomChanges,
                                          ArchitectureTestGenerator.TestChange architectureTest) {
        Path file = project.root().resolve(SentinelConfiguration.FILE_NAME);
        try {
            String content = configuration(gates);
            Files.writeString(file, content, StandardOpenOption.CREATE_NEW);
            return new InitResult(file, true, gates, integrations, pomChanges, architectureTest);
        } catch (java.nio.file.FileAlreadyExistsException e) {
            return new InitResult(file, false, gates, integrations, pomChanges, architectureTest);
        } catch (IOException e) {
            throw new SentinelException("Could not write " + file + ": " + e.getMessage(), e);
        }
    }

    private IntegrationResult installIntegration(Path root, String id) {
        if (integrations == null) {
            throw new SentinelException("Agent integrations are unavailable in this runtime.");
        }
        return integrations.integrate(id, root, false);
    }

    private void rollbackNewIntegrations(Path root, List<String> ids, List<IntegrationResult> results) {
        if (integrations == null) return;
        for (int i = 0; i < Math.min(ids.size(), results.size()); i++) {
            if (results.get(i).status() != IntegrationResult.Status.CHANGED) continue;
            try {
                integrations.integrate(ids.get(i), root, true);
            } catch (RuntimeException ignored) {
                // Preserve the original initialization error; ownership-safe removal remains available.
            }
        }
    }

    private void validateIntegrations(List<String> ids) {
        if (ids.isEmpty()) {
            throw new SentinelException("Select at least one integration, or choose none.");
        }
        boolean noIntegration = ids.contains(InitSetupCatalog.NO_INTEGRATION);
        if (noIntegration && ids.size() > 1) {
            throw new SentinelException("The no-integration choice cannot be combined with another integration.");
        }
        ids.forEach(catalog::integration);
    }

    private static boolean requiresArchitectureTest(List<InitSetupCatalog.GateOption> gates) {
        return gates.stream().anyMatch(gate -> gate.id().equals("architecture") || gate.id().equals("archunit"));
    }

    private static String configuration(List<InitSetupCatalog.GateOption> gates) {
        StringBuilder content = new StringBuilder("version = 1\n");
        for (InitSetupCatalog.GateOption gate : gates) {
            String executable = gate.command().getFirst().replace("\\", "\\\\").replace("\"", "\\\"");
            String goal = gate.command().size() > 1
                    ? gate.command().subList(1, gate.command().size()).stream()
                    .map(value -> value.replace("\\", "\\\\").replace("\"", "\\\""))
                    .reduce((left, right) -> left + " " + right).orElse("") : "";
            content.append("\n[quality-gates.").append(gate.id())
                    .append("]\nenabled = true\ncommand = \"")
                    .append(executable).append(" ").append(goal).append("\"\n");
        }
        return content.toString();
    }
}
