package dev.sentinel.application;

import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelConfigurationReader;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.QualityGate;
import dev.sentinel.domain.project.Project;
import com.google.inject.Inject;

import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;

public class CheckService {

    private final ProjectDetector detector;
    private final SentinelConfigurationReader configurationReader;
    private final QualityGateFactory gateFactory;
    private final QualityGateRunner runner;

    @Inject
    public CheckService(ProjectDetector detector, SentinelConfigurationReader configurationReader,
                        QualityGateFactory gateFactory, QualityGateRunner runner) {
        this.detector = detector;
        this.configurationReader = configurationReader;
        this.gateFactory = gateFactory;
        this.runner = runner;
    }

    public CheckReport check(Path start) {
        return check(start, null);
    }

    public CheckReport check(Path start, String profileName) {
        Project project = detector.detect(start).orElseThrow(ProjectNotFoundException::new);
        SentinelConfiguration configuration =
                configurationReader.read(project.root().resolve(SentinelConfiguration.FILE_NAME));
        SentinelConfiguration selected = selectProfile(configuration, profileName);
        List<QualityGate> gates = gateFactory.create(selected, project);
        if (selected.enabledGates().isEmpty()) {
            throw new SentinelException("No quality gates are enabled in " + SentinelConfiguration.FILE_NAME
                    + "; refusing to report a pass without checking anything.");
        }
        return runner.run(project, gates);
    }

    private static SentinelConfiguration selectProfile(SentinelConfiguration configuration, String name) {
        if (name == null || name.isBlank()) return configuration;
        var profile = configuration.profiles().get(name);
        if (profile == null) throw new SentinelException("Unknown profile '" + name + "'. Available profiles: " + configuration.profiles().keySet());
        var selected = new LinkedHashMap<String, dev.sentinel.domain.config.GateConfiguration>();
        for (String gate : profile.gates()) {
            var config = configuration.gates().get(gate);
            if (config == null) throw new SentinelException("Profile '" + name + "' references unknown gate '" + gate + "'.");
            selected.put(gate, config);
        }
        return new SentinelConfiguration(configuration.version(), selected, configuration.profiles());
    }
}
