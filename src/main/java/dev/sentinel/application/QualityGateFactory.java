package dev.sentinel.application;

import dev.sentinel.domain.config.GateConfiguration;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.gate.MavenTestGate;
import dev.sentinel.domain.gate.QualityGate;
import dev.sentinel.domain.process.CommandExecutor;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds the gates for every enabled entry of the configuration. Only {@code tests} exists for now. */
public class QualityGateFactory {

    private final CommandExecutor executor;

    @Inject
    public QualityGateFactory(CommandExecutor executor) {
        this.executor = executor;
    }

    public List<QualityGate> create(SentinelConfiguration configuration) {
        return create(configuration, null);
    }

    public List<QualityGate> create(SentinelConfiguration configuration, dev.sentinel.domain.project.Project project) {
        List<QualityGate> gates = new ArrayList<>();
        for (Map.Entry<String, GateConfiguration> entry : configuration.enabledGates().entrySet()) {
            gates.add(switch (entry.getKey()) {
                case MavenTestGate.NAME -> new MavenTestGate(executor,
                        testsCommand(entry.getValue().command(), project));
                default -> throw new SentinelException("Quality gate '" + entry.getKey()
                        + "' is enabled but not supported yet. Supported gates: " + MavenTestGate.NAME);
            });
        }
        return gates;
    }

    private static List<String> testsCommand(List<String> configured,
                                              dev.sentinel.domain.project.Project project) {
        if (project != null && !project.mavenWrapperAvailable() && configured.size() >= 1
                && (configured.getFirst().equals("./mvnw") || configured.getFirst().equals("mvnw"))) {
            List<String> fallback = new ArrayList<>(configured);
            fallback.set(0, "mvn");
            return fallback;
        }
        return configured;
    }
}
