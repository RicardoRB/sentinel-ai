package dev.sentinel.application.init;

import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.config.GateConfiguration;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.ArchitectureTestChange;
import dev.sentinel.domain.init.ArchitectureTestGeneration;
import dev.sentinel.domain.init.BuildToolConfiguration;
import dev.sentinel.domain.init.ConfigurationStorage;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.InitResult;
import dev.sentinel.domain.init.InitSelection;
import dev.sentinel.domain.init.PomChange;
import dev.sentinel.domain.project.Project;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.application.project.ProjectNotFoundException;
import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class InitService {

    static final String DEFAULT_CONFIGURATION = """
            version = 1

            [quality-gates.tests]
            enabled = true
            command = "./mvnw test"
            """;

    private final ProjectDetector detector;
    private final Set<AgentIntegration> integrations;
    private final InitSetupCatalog catalog;
    private final BuildToolConfiguration pomTools;
    private final ArchitectureTestGeneration architectureTests;
    private final ConfigurationStorage configurationStorage;

    @Inject
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
            justification = "The constructor-injected storage port is retained for use-case orchestration.")
    public InitService(ProjectDetector detector, Set<AgentIntegration> integrations,
                       InitSetupCatalog catalog, BuildToolConfiguration pomTools,
                       ArchitectureTestGeneration architectureTests, ConfigurationStorage configurationStorage) {
        this.detector = detector;
        this.integrations = Set.copyOf(integrations);
        this.catalog = catalog;
        this.pomTools = pomTools;
        this.architectureTests = architectureTests;
        this.configurationStorage = configurationStorage;
    }

    public InitResult init(Path start) {
        Project project = project(start);
        Path file = project.root().resolve(SentinelConfiguration.FILE_NAME);
        return new InitResult(file, configurationStorage.create(file, DEFAULT_CONFIGURATION));
    }

    public Project project(Path start) {
        return detector.detect(start).orElseThrow(ProjectNotFoundException::new);
    }

    public boolean configurationExists(Project project) {
        return configurationStorage.exists(project.root().resolve(SentinelConfiguration.FILE_NAME));
    }

    public InitSetupCatalog catalog() {
        return catalog;
    }

    public InitResult initialize(Path start, InitSelection selection) {
        return initialize(start, selection, false);
    }

    public InitResult initialize(Path start, InitSelection selection, boolean overwrite) {
        Project project = project(start);
        validateIntegrations(selection.integrations());
        List<InitGateOption> gates = selection.gates().stream()
                .map(id -> catalog.gate(project, id)).toList();
        if (gates.isEmpty()) throw new SentinelException("Select at least one quality gate.");
        if (selection.architecture() != null) catalog.architecture(selection.architecture());
        Path configurationFile = project.root().resolve(SentinelConfiguration.FILE_NAME);
        String originalConfiguration = configurationStorage.read(configurationFile).orElse(null);
        boolean hadConfiguration = originalConfiguration != null;
        if (hadConfiguration && !overwrite) {
            return new InitResult(project.root().resolve(SentinelConfiguration.FILE_NAME), false, gates, List.of());
        }

        List<IntegrationResult> installed = new ArrayList<>();
        PomChange pomChange = null;
        ArchitectureTestChange architectureTest = null;
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
            pomChange = pomTools.apply(project, gates);
            if (requiresArchitectureTest(gates)) {
                architectureTest = architectureTests.apply(project,
                        selection.architecture() == null ? "layered" : selection.architecture());
            }
            InitResult result = writeConfiguration(project, gates, installed, pomChange.tools(), architectureTest, overwrite);
            if (!result.created()) {
                architectureTests.rollback(architectureTest);
                pomTools.rollback(pomChange);
                rollbackNewIntegrations(project.root(), selection.integrations(), installed);
            }
            return result;
        } catch (RuntimeException e) {
            architectureTests.rollback(architectureTest);
            pomTools.rollback(pomChange);
            if (originalConfiguration != null) {
                try {
                    configurationStorage.restore(configurationFile, originalConfiguration);
                } catch (RuntimeException ignored) {
                    // Preserve the original setup failure; restoration can be retried manually.
                }
            }
            rollbackNewIntegrations(project.root(), selection.integrations(), installed);
            throw e;
        }
    }

    private InitResult writeConfiguration(Project project, List<InitGateOption> gates,
                                          List<IntegrationResult> integrations, List<String> pomChanges,
                                          ArchitectureTestChange architectureTest, boolean overwrite) {
        Path file = project.root().resolve(SentinelConfiguration.FILE_NAME);
        String content = configuration(gates);
        if (overwrite) {
            configurationStorage.replace(file, content);
            return new InitResult(file, true, true, gates, integrations, pomChanges, architectureTest);
        }
        boolean created = configurationStorage.create(file, content);
        return new InitResult(file, created, false, gates, integrations, pomChanges, architectureTest);
    }

    private IntegrationResult installIntegration(Path root, String id) {
        if (integrations.isEmpty()) {
            throw new SentinelException("Agent integrations are unavailable in this runtime.");
        }
        return integration(id).integrate(root, false);
    }

    private void rollbackNewIntegrations(Path root, List<String> ids, List<IntegrationResult> results) {
        for (int i = 0; i < Math.min(ids.size(), results.size()); i++) {
            if (results.get(i).status() != IntegrationResult.Status.CHANGED) continue;
            try {
                integration(ids.get(i)).integrate(root, true);
            } catch (RuntimeException ignored) {
                // Preserve the original initialization error; ownership-safe removal remains available.
            }
        }
    }

    private AgentIntegration integration(String id) {
        return integrations.stream().filter(candidate -> candidate.id().equals(id))
                .findFirst().orElseThrow(() -> new SentinelException(
                        "Unknown agent '" + id + "'. Supported agents: opencode, claude-code"));
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

    private static boolean requiresArchitectureTest(List<InitGateOption> gates) {
        return gates.stream().anyMatch(gate -> gate.id().equals("archunit"));
    }

    private static String configuration(List<InitGateOption> gates) {
        StringBuilder content = new StringBuilder("version = 1\n");
        for (InitGateOption gate : gates) {
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
