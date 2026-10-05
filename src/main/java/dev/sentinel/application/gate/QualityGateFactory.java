package dev.sentinel.application.gate;

import dev.sentinel.domain.config.GateConfiguration;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.application.gate.MavenTestGate;
import dev.sentinel.application.gate.CommandQualityGate;
import dev.sentinel.domain.gate.SkippedQualityGate;
import dev.sentinel.domain.gate.QualityGate;
import dev.sentinel.domain.gate.SupportedQualityGates;
import dev.sentinel.domain.process.CommandExecutor;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Resolves the typed built-in gate registry in stable configuration order. */
public class QualityGateFactory {

    public static final List<String> SUPPORTED_GATES = SupportedQualityGates.IDS;

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
        configuration.gates().keySet().forEach(id -> {
            if (!SUPPORTED_GATES.contains(id) && !SupportedQualityGates.LEGACY_IDS.contains(id)) {
                throw new SentinelException("Quality gate '" + id + "' is unknown. Supported gates: " + SUPPORTED_GATES);
            }
        });
        for (Map.Entry<String, GateConfiguration> entry : configuration.gates().entrySet()) {
            String id = entry.getKey();
            if (!entry.getValue().enabled()) {
                gates.add(new SkippedQualityGate(id));
                continue;
            }
            gates.add(switch (id) {
                case MavenTestGate.NAME -> new MavenTestGate(executor,
                        testsCommand(entry.getValue().command(), project));
                default -> new CommandQualityGate(id, executor, entry.getValue().command());
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
