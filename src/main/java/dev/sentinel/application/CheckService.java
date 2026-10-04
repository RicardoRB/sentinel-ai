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
        Project project = detector.detect(start).orElseThrow(ProjectNotFoundException::new);
        SentinelConfiguration configuration =
                configurationReader.read(project.root().resolve(SentinelConfiguration.FILE_NAME));
        List<QualityGate> gates = gateFactory.create(configuration, project);
        if (configuration.enabledGates().isEmpty()) {
            throw new SentinelException("No quality gates are enabled in " + SentinelConfiguration.FILE_NAME
                    + "; refusing to report a pass without checking anything.");
        }
        return runner.run(project, gates);
    }
}
