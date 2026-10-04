package dev.sentinel.application;

import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.gate.QualityGate;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QualityGateRunnerTest {

    private final Project project = new Project(Path.of("/p"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);
    private final QualityGateRunner runner = new QualityGateRunner();

    private static QualityGate gate(String name, GateStatus status) {
        return new QualityGate() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public GateResult execute(Project project) {
                return new GateResult(name, status, List.of("x"), status == GateStatus.PASSED ? 0 : 1,
                        Duration.ofMillis(10), "", "");
            }
        };
    }

    @Test
    void allPassedAggregatesToPassed() {
        CheckReport report = runner.run(project, List.of(gate("a", GateStatus.PASSED), gate("b", GateStatus.PASSED)));

        assertThat(report.status()).isEqualTo(GateStatus.PASSED);
        assertThat(report.passed()).isTrue();
    }

    @Test
    void oneFailureAggregatesToFailedAndOtherGatesStillRun() {
        CheckReport report = runner.run(project, List.of(
                gate("a", GateStatus.FAILED), gate("b", GateStatus.PASSED)));

        assertThat(report.status()).isEqualTo(GateStatus.FAILED);
        assertThat(report.results()).extracting(GateResult::name).containsExactly("a", "b");
    }
}
