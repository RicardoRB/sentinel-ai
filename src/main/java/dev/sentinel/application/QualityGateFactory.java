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
        List<QualityGate> gates = new ArrayList<>();
        for (Map.Entry<String, GateConfiguration> entry : configuration.enabledGates().entrySet()) {
            gates.add(switch (entry.getKey()) {
                case MavenTestGate.NAME -> new MavenTestGate(executor, entry.getValue().command());
                default -> throw new SentinelException("Quality gate '" + entry.getKey()
                        + "' is enabled but not supported yet. Supported gates: " + MavenTestGate.NAME);
            });
        }
        return gates;
    }
}
